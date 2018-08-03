package com.eastwood.tools.plugins.core

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
    Map<String, String> microModuleResourcePrefixMap

    String errorMessage = ""
    String lineSeparator = System.getProperty("line.separator")

    public MicroModuleCodeCheck(Project project, Map<String, List<String>> microModuleReferenceMap,
                                Map<String, String> microModuleResourcePrefixMap) {
        this.project = project
        this.microModuleReferenceMap = microModuleReferenceMap
        this.microModuleResourcePrefixMap = microModuleResourcePrefixMap
        projectPath = project.projectDir.absolutePath
        buildDir = new File(project.projectDir, "build")

        microModuleExtension = project.extensions.getByName("microModule")

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
                if (filePath != null) {
                    ResourceFile resourceFile = lastModifiedResourcesMap.get(filePath)
                    File file = project.file(filePath)
                    def microModuleName = getMicroModuleName(filePath)
                    def prefix = microModuleResourcePrefixMap.get(microModuleName, null)
                    if (resourceFile == null) {
                        resourceFile = new ResourceFile()
                        resourceFile.name = file.name
                        resourceFile.path = filePath
                        resourceFile.microModuleName = microModuleName
                        resourceFile.lastModified = file.lastModified()
                        lastModifiedResourcesMap.put(filePath, resourceFile)
                    }
                    resourceFile.prefix = prefix
                    if(filePath.endsWith(".xml")){
                        def currentModified = file.lastModified()
                        if ((prefix != null && !resourceFile.name.startsWith(prefix))
                                || resourceFile.lastModified.longValue() < currentModified) {
                            modifiedResourcesList.add(file)
                            resourceFile.lastModified = currentModified
                        }
                    }
                    if(resourceFile.microModuleName != null && prefix != null){
                        def resourcesPattern = /\${File.separator}(drawable|layout)[A-Za-z0-9-]*\${File.separator}(?!${prefix})./
                        def matcher = (filePath =~ resourcesPattern)
                        if (matcher.find()) {
                            recordErrorMsg(file.absolutePath, file.name, prefix, 2)
                        }
                    }
                }
            }
        }
        return modifiedResourcesList
    }

    private String recordErrorMsg(String absolutePath, String name, String prefix, int lineNumber) {
        def message = "${absolutePath}:${lineNumber}: ${lineSeparator}"
        message += "Error: Resource named ${name} does not start with the project's resource" +
                " prefix '${prefix}', pls rename to '${prefix}${name}'"
        message += lineSeparator
        if (!errorMessage.contains(message)) {
            errorMessage += message
        }
    }

    void handleModifiedResources(List<File> modifiedResourcesList) {
        Map<String, String> resourcesMap = resourceMerged.getResourcesMap()
        def resourcesPattern = /@(dimen|drawable|color|string|style|id|mipmap|layout)\/[A-Za-z0-9_]+/
        modifiedResourcesList.each {
            String text = it.text
            List<String> textLines = text.readLines()
            def absolutePath = it.absolutePath
            def microModuleName = getMicroModuleName(absolutePath)
            def prefix = microModuleResourcePrefixMap.get(microModuleName)
            if(prefix != null && (it.name.endsWith("strings.xml") || it.name.endsWith("colors.xml"))){
                textLines.each {
                    if(it.contains("name")){
                        def splits = it.split('"')
                        if(splits.length >= 3 && !splits[1].startsWith(prefix)){
                            List<Number> lines = textLines.findIndexValues { it.contains(splits[1]) }
                            lines.each {
                                def lineIndex = it.intValue()
                                def lineContext = textLines.get(lineIndex).trim()
                                if (lineContext.startsWith("<!--")) {
                                    return
                                }

                                recordErrorMsg(absolutePath, splits[1], prefix, lineIndex + 1)
                            }
                        }
                    }
                }
            }

            def matcher = (text =~ resourcesPattern)
            while (matcher.find()) {
                def find = matcher.group()
                def name = find.substring(find.indexOf("/") + 1)
                def from = resourcesMap.get(name)

                if (from != null && microModuleName != from && !isReference(microModuleName, from)) {
                    List<Number> lines = textLines.findIndexValues { it.contains(find) }
                    lines.each {
                        def lineIndex = it.intValue()
                        def lineContext = textLines.get(lineIndex).trim()
                        if (lineContext.startsWith("<!--")) {
                            return
                        }

                        def message = absolutePath + ':' + (lineIndex + 1)
                        if (!errorMessage.contains(message)) {
                            message += lineSeparator
                            message += "- can't use [" + find + "] which from microModule '${from}'."
                            message += lineSeparator
                            errorMessage += message
                        }
                    }
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
                        resourceFile.prefix = microModuleResourcePrefixMap.get(resourceFile.microModuleName, null)
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
            List<String> textLines = text.readLines()
            def matcher = (text =~ resourcesPattern)
            def absolutePath = it.absolutePath
            def microModuleName = getMicroModuleName(absolutePath)
            while (matcher.find()) {
                matcher
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
                    List<Number> lines = textLines.findIndexValues { it.contains(find) }
                    lines.each {
                        def lineIndex = it.intValue()
                        def lineContext = textLines.get(lineIndex).trim()
                        if (lineContext.startsWith("//") || lineContext.startsWith("/*")) {
                            return
                        }

                        def message = absolutePath + ':' + (lineIndex + 1)
                        if (!errorMessage.contains(message)) {
                            message += lineSeparator
                            message += "- can't use [" + find + "] which from microModule '${from}'."
                            message += lineSeparator
                            errorMessage += message
                        }
                    }
                }
            }
        }
    }

    // ----------------------------------------------------------------------------------------

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