package com.eastwood.tools.plugins.micromodule

import org.gradle.api.GradleException
import org.gradle.api.GradleScriptException
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element
import org.w3c.dom.NodeList

class MicroModuleCodeCheck {

    Project project
    String projectPath
    File buildDir
    DefaultMicroModuleExtension microModuleExtension

    MicroManifest microManifest
    ResourceMerged resourceMerged

    Map<String, List<String>> microModuleReferenceMap

    String errorMessage = ""
    String lineSeparator = System.getProperty("line.separator")

    public MicroModuleCodeCheck(Project project) {
        this.project = project
        projectPath = project.projectDir.absolutePath
        buildDir = new File(project.projectDir, "build")

        microModuleExtension = project.extensions.getByName("microModule")

        initMicroModuleReference()

        microManifest = getMicroManifest()
    }

    @TaskAction
    void checkResources(String mergeTaskName) {
        println ":${project.name}:codeCheckResources"
        resourceMerged = new ResourceMerged()
        resourceMerged.load(project.projectDir, mergeTaskName)
        if (!resourceMerged.resourcesMergerFile.exists()) {
            println "[micro-module-code-check] - resourcesMergerFile is not exists!"
            return
        }

        NodeList resourcesNodeList = resourceMerged.getResourcesNodeList()
        List<File> modifiedResourcesList = getModifiedResourcesList(resourcesNodeList)
        if (modifiedResourcesList.size() == 0) {
            return
        }
        handleModifiedResources(modifiedResourcesList)
        if (errorMessage != "") {
            throw new GradleScriptException(errorMessage, null)
        }
        String packageName = getMainManifest().getPackageName()
        microManifest.packageName = packageName
        saveMicroManifest()
    }

    List<File> getModifiedResourcesList(NodeList resourcesNodeList) {
        Map<String, ResourceFile> lastModifiedResourcesMap = microManifest.getResourcesMap()
        List<File> modifiedResourcesList = new ArrayList<>()
        if (resourcesNodeList == null || resourcesNodeList.length == 0) return modifiedResourcesList

        for (int i = 0; i < resourcesNodeList.getLength(); i++) {
            Element resourcesElement = (Element) resourcesNodeList.item(i)
            NodeList fileNodeList = resourcesElement.getElementsByTagName("file")
            for (int j = 0; j < fileNodeList.getLength(); j++) {
                Element fileElement = (Element) fileNodeList.item(j)
                String filePath = fileElement.getAttribute("path")
                if (filePath != null && filePath.endsWith(".xml")) {
                    File file = project.file(filePath)
                    ResourceFile resourceFile = lastModifiedResourcesMap.get(filePath)
                    def currentModified = file.lastModified()
                    if (resourceFile == null || resourceFile.lastModified.longValue() < currentModified) {
                        modifiedResourcesList.add(file)

                        if (resourceFile == null) {
                            resourceFile = new ResourceFile()
                            resourceFile.name = file.name
                            resourceFile.path = filePath
                            resourceFile.microModuleName = getMicroModuleName(filePath)
                            lastModifiedResourcesMap.put(filePath, resourceFile)
                        }
                        resourceFile.lastModified = currentModified
                    }
                }
            }
        }
        return modifiedResourcesList
    }

    void handleModifiedResources(List<File> modifiedResourcesList) {
        Map<String, String> resourcesMap = resourceMerged.getResourcesMap()
        def resourcesPattern = /@(dimen|drawable|color|string|style|id|mipmap|layout)\/[A-Za-z0-9_]+/
        modifiedResourcesList.each {
            String text = it.text
            def matcher = (text =~ resourcesPattern)

            String moduleName = getMicroModuleName(it.absolutePath)

            while (matcher.find()) {
                def find = matcher.group()
                def name = find.substring(find.indexOf("/") + 1)
                def from = resourcesMap.get(name)

                if (from != null && moduleName != from && !isReference(moduleName, from)) {
                    def message = it.absolutePath + ':' + (text.substring(0, text.indexOf(find)).count(lineSeparator) + 1)
                    message += lineSeparator
                    message += "- can't use [" + find + "] which from microModule '${from}'."
                    message += lineSeparator
                    errorMessage += message
                }
            }
        }
    }

    @TaskAction
    void checkClasses(String productFlavorBuildType, mergeTaskName) {
        println ":${project.name}:codeCheckClasses"
        File classesMergerFile = new File(buildDir, "intermediates/classes/${productFlavorBuildType}/" + microManifest.packageName.replace(".", "/"))
        if (!classesMergerFile.exists()) {
            println "[micro-module-code-check] - classesMergerFile is not exists!"
            return
        }

        List<File> modifiedClassesList = getModifiedClassesList()
        if (modifiedClassesList.size() == 0) {
            return
        }


        if (resourceMerged == null) {
            resourceMerged = new ResourceMerged()
            resourceMerged.load(project.projectDir, mergeTaskName)
            if (!resourceMerged.resourcesMergerFile.exists()) {
                println "[micro-module-code-check] - " + mergeTaskName + ' is not exists!'
                return
            }
        }
        handleModifiedClasses(modifiedClassesList)
        if (errorMessage != "") {
            throw new GradleScriptException(errorMessage, null)
        }
        saveMicroManifest()
    }

    List<File> getModifiedClassesList() {
        Map<String, ResourceFile> lastModifiedClassesMap = microManifest.getClassesMap()
        List<File> modifiedClassesList = new ArrayList<>()
        File javaDir = new File(microModuleExtension.mainMicroModule.microModuleDir, "/src/main/java")
        getModifiedJavaFile(javaDir, modifiedClassesList, lastModifiedClassesMap)

        microModuleExtension.includeMicroModules.each {
            javaDir = new File(it.microModuleDir, "/src/main/java")
            getModifiedJavaFile(javaDir, modifiedClassesList, lastModifiedClassesMap)
        }
        return modifiedClassesList
    }

    void getModifiedJavaFile(File directory, List<File> modifiedClassesList, Map<String, ResourceFile> lastModifiedClassesMap) {
        directory.listFiles().each {
            if (it.isDirectory()) {
                getModifiedJavaFile(it, modifiedClassesList, lastModifiedClassesMap)
            } else {
                def currentModified = it.lastModified()
                ResourceFile resourceFile = lastModifiedClassesMap.get(it.absolutePath)
                if (resourceFile == null || resourceFile.lastModified.longValue() < currentModified) {
                    modifiedClassesList.add(it)

                    if (resourceFile == null) {
                        resourceFile = new ResourceFile()
                        resourceFile.name = it.name
                        resourceFile.path = it.absolutePath
                        resourceFile.microModuleName = getMicroModuleName(it.absolutePath)
                        lastModifiedClassesMap.put(it.absolutePath, resourceFile)
                    }
                    resourceFile.lastModified = it.lastModified()
                }
            }
        }
    }

    void handleModifiedClasses(List<File> modifiedClassesList) {
        Map<String, String> resourcesMap = resourceMerged.getResourcesMap()
        Map<String, String> classesMap = new HashMap<>()
        microManifest.getClassesMap().each {
            ResourceFile resourceFile = it.value
            def path = resourceFile.path
            def name = path.substring(path.indexOf("java") + 5, path.lastIndexOf(".")).replace(File.separator, ".")
            classesMap.put(name, resourceFile.microModuleName)
        }

        def resourcesPattern = /R.(dimen|drawable|color|string|style|id|mipmap|layout).[A-Za-z0-9_]+|import\s[A-Za-z0-9_.]+/
        modifiedClassesList.each {
            String text = it.text
            def matcher = (text =~ resourcesPattern)
            def microModuleName = getMicroModuleName(it.absolutePath)
            while (matcher.find()) {
                def find = matcher.group()
                def from, name
                if (find.startsWith("R")) {
                    name = find.substring(find.lastIndexOf(".") + 1)
                    from = resourcesMap.get(name)
                } else if (find.startsWith("import")) {
                    name = find.substring(find.lastIndexOf(" ") + 1, find.length())
                    from = classesMap.get(name)
                }

                if (from != null && microModuleName != from && !isReference(microModuleName, from)) {
                    def message = it.absolutePath + ':' + (text.substring(0, text.indexOf(find)).count(lineSeparator) + 1)
                    message += lineSeparator
                    message += "- can't use [" + find + "] which from microModule '${from}'."
                    message += lineSeparator
                    errorMessage += message
                }
            }
        }
    }

    // ----------------------------------------------------------------------------------------

    void initMicroModuleReference() {
        microModuleReferenceMap = new HashMap<>()

        getMicroModuleReference(microModuleExtension.mainMicroModule)

        microModuleExtension.includeMicroModules.each {
            getMicroModuleReference(it)
        }

    }

    void getMicroModuleReference(MicroModule microModule) {
        // check [micro.properties] exists or not
        File propertiesFile = new File(microModule.microModuleDir, "micro.properties")
        if (!propertiesFile.exists()) {
            return
        }
        // read micro-module properties
        def properties = new Properties()
        propertiesFile.withInputStream { properties.load(it) }
        properties = new ConfigSlurper().parse(properties)

        // check library reference
        properties.microModule.reference.each {
            MicroModule referenceMicroModule = microModuleExtension.buildMicroModule(it.value)
            if (referenceMicroModule == null) {
                throw new GradleException("can't find specified microModule '${it.value}', which is referenced by microModle${microModule.name}")
            }
            List<String> referenceList = microModuleReferenceMap.get(microModule.name)
            if (referenceList == null) {
                referenceList = new ArrayList<>()
                referenceList.add(referenceMicroModule.name)
                microModuleReferenceMap.put(microModule.name, referenceList)
            } else {
                if (!referenceList.contains(referenceMicroModule.name)) {
                    referenceList.add(referenceMicroModule.name)
                }
            }

            if (!microModuleReferenceMap.containsKey(referenceMicroModule.name)) {
                if (referenceMicroModule.name != microModule.name) {
                    getMicroModuleReference(referenceMicroModule)
                }
            }
        }
    }

    String getMicroModuleName(absolutePath) {
        String moduleName = absolutePath.replace(projectPath, "")
        moduleName = moduleName.substring(0, moduleName.indexOf(ResourceMerged.SRC))
        if (File.separator == "\\") {
            moduleName = moduleName.replaceAll("\\\\", ":")
        } else {
            moduleName = moduleName.replaceAll("/", ":")
        }
        return moduleName
    }

    boolean isReference(String microModuleName, String from) {
        List<String> original = new ArrayList<>()
        original.add(microModuleName)
        return isReference(microModuleName, from, original)
    }

    boolean isReference(String microModuleName, String from, List<String> original) {
        println microModuleName + " " + from + " " + original
        List<String> referenceList = microModuleReferenceMap.get(microModuleName)
        if (referenceList == null) return false
        if (referenceList.contains(from)) {
            return true
        }
        for (int i = 0; i < referenceList.size(); i++) {
            if (original.contains(referenceList[i])) {
                continue
            } else {
                original.add(referenceList[i])
            }
            if (isReference(referenceList[i], from, original)) {
                return true
            }
        }
        return false
    }

    String getMicroModulePackageName(File directory, String packageName) {
        if (directory == null) return packageName

        File[] files = directory.listFiles()
        if (files == null || files.length == 0) {
            return packageName + "." + directory.name
        } else if (files.length == 1) {
            if (files[0].isFile()) {
                return packageName + "." + directory.name
            } else {
                return getMicroModulePackageName(files[0], packageName + "." + directory.name)
            }
        } else {
            for (int i = 0; i < files.size(); i++) {
                if (files[i].isFile()) {
                    return packageName + "." + directory.name
                }
            }
        }
    }

    private AndroidManifest getMainManifest() {
        AndroidManifest mainManifest = new AndroidManifest()
        def manifest = new File(microModuleExtension.mainMicroModule.microModuleDir, "src/main/AndroidManifest.xml")
        mainManifest.load(manifest)
        return mainManifest
    }

    private MicroManifest getMicroManifest() {
        MicroManifest microManifest = new MicroManifest()
        microManifest.load(project.file("build/microModule/code-check-manifest.xml"))
        return microManifest
    }

    private MicroManifest saveMicroManifest() {
        if (microManifest == null) {
            microManifest = new MicroManifest()
        }
        return microManifest.save(project.file("build/microModule/code-check-manifest.xml"))
    }

}