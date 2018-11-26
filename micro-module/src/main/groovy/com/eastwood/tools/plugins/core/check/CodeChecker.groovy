package com.eastwood.tools.plugins.core.check

import com.eastwood.tools.plugins.core.MicroModule
import com.eastwood.tools.plugins.core.MicroModuleInfo
import com.eastwood.tools.plugins.core.ProductFlavorInfo
import com.eastwood.tools.plugins.core.Utils
import org.gradle.api.GradleScriptException
import org.gradle.api.Project
import org.w3c.dom.Element
import org.w3c.dom.NodeList

class CodeChecker {

    Project project
    MicroModuleInfo microModuleInfo
    ProductFlavorInfo productFlavorInfo

    String buildType
    String productFlavor

    CheckManifest checkManifest
    ResourceMerged resourceMerged

    String errorMessage = ""
    String lineSeparator = System.getProperty("line.separator")

    Map<String, List<String>> microModulePackageNameMap

    CodeChecker(Project project, MicroModuleInfo microModuleInfo, ProductFlavorInfo productFlavorInfo, String buildType, String productFlavor) {
        this.project = project
        this.microModuleInfo = microModuleInfo
        this.productFlavorInfo = productFlavorInfo
        this.buildType = buildType
        this.productFlavor = productFlavor
        this.checkManifest = getModuleCheckManifest()
    }

    void checkResources(String mergeTaskName, List<String> combinedProductFlavors) {
        resourceMerged = new ResourceMerged()
        if (!resourceMerged.load(project.projectDir, mergeTaskName)) {
            return
        }

        List<NodeList> resourceNodeLists = resourceMerged.getResourcesNodeList(combinedProductFlavors)
        List<File> modifiedResourcesList = getModifiedResourcesList(resourceNodeLists)
        if (modifiedResourcesList.size() == 0) {
            return
        }
        handleModifiedResources(modifiedResourcesList)
        if (errorMessage != "") {
            throw new GradleScriptException(errorMessage, null)
        }

        def manifest = new File(microModuleInfo.mainMicroModule.microModuleDir, "src/main/AndroidManifest.xml")
        String packageName = Utils.getAndroidManifestPackageName(manifest)
        checkManifest.packageName = packageName
        saveModuleCheckManifest()
    }

    List<File> getModifiedResourcesList(List<NodeList> resourcesNodeList) {
        Map<String, MicroModuleFile> lastModifiedResourcesMap = checkManifest.getResourcesMap()
        List<File> modifiedResourcesList = new ArrayList<>()
        if (resourcesNodeList == null || resourcesNodeList.length == 0) return modifiedResourcesList

        resourcesNodeList.each {
            for (int i = 0; i < it.getLength(); i++) {
                Element resourcesElement = (Element) it.item(i)
                NodeList fileNodeList = resourcesElement.getElementsByTagName("file")
                for (int j = 0; j < fileNodeList.getLength(); j++) {
                    Element fileElement = (Element) fileNodeList.item(j)
                    String filePath = fileElement.getAttribute("path")
                    if (filePath != null && filePath.endsWith(".xml")) {
                        File file = project.file(filePath)
                        MicroModuleFile resourceFile = lastModifiedResourcesMap.get(filePath)
                        def currentModified = file.lastModified()
                        if (resourceFile == null || resourceFile.lastModified.longValue() < currentModified) {
                            modifiedResourcesList.add(file)

                            if (resourceFile == null) {
                                resourceFile = new MicroModuleFile()
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
        }

        return modifiedResourcesList
    }

    void handleModifiedResources(List<File> modifiedResourcesList) {
        Map<String, String> resourcesMap = resourceMerged.getResourcesMap()
        def resourcesPattern = /@(dimen|drawable|color|string|style|id|mipmap|layout)\/[A-Za-z0-9_]+/
        modifiedResourcesList.each {
            String text = it.text
            List<String> textLines = text.readLines()
            def matcher = (text =~ resourcesPattern)
            def absolutePath = it.absolutePath
            def microModuleName = getMicroModuleName(absolutePath)
            while (matcher.find()) {
                def find = matcher.group()
                def name = find.substring(find.indexOf("/") + 1)
                def from = resourcesMap.get(name)
                if (from != null && microModuleName != from && !microModuleInfo.hasDependency(microModuleName, from)) {
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
                            message += "- cannot use [" + find + "] which from MicroModule '${from}'."
                            message += lineSeparator
                            errorMessage += message
                        }
                    }
                }
            }
        }
    }

    void checkClasses(String mergeTaskName, List<String> combinedProductFlavors) {
        List<File> modifiedClassesList = getModifiedClassesList(combinedProductFlavors)
        if (modifiedClassesList.size() == 0) {
            return
        }

        if (resourceMerged == null) {
            resourceMerged = new ResourceMerged()
            if (!resourceMerged.load(project.projectDir, mergeTaskName)) {
                return
            }
        }
        handleModifiedClasses(modifiedClassesList)
        if (errorMessage != "") {
            throw new GradleScriptException(errorMessage, null)
        }
        saveModuleCheckManifest()
    }

    List<File> getModifiedClassesList(List<String> combinedProductFlavors) {
        Map<String, MicroModuleFile> lastModifiedClassesMap = checkManifest.getClassesMap()
        List<File> modifiedClassesList = new ArrayList<>()
        microModuleInfo.includeMicroModules.each {
            MicroModule microModule = it.value
            combinedProductFlavors.each {
                File javaDir = new File(microModule.microModuleDir, "/src/${it}/java")
                getModifiedJavaFile(javaDir, modifiedClassesList, lastModifiedClassesMap)
            }
        }
        return modifiedClassesList
    }

    void getModifiedJavaFile(File directory, List<File> modifiedClassesList, Map<String, MicroModuleFile> lastModifiedClassesMap) {
        directory.listFiles().each {
            if (it.isDirectory()) {
                getModifiedJavaFile(it, modifiedClassesList, lastModifiedClassesMap)
            } else {
                def currentModified = it.lastModified()
                MicroModuleFile resourceFile = lastModifiedClassesMap.get(it.absolutePath)
                if (resourceFile == null || resourceFile.lastModified.longValue() < currentModified) {
                    modifiedClassesList.add(it)

                    if (resourceFile == null) {
                        resourceFile = new MicroModuleFile()
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
        checkManifest.getClassesMap().each {
            MicroModuleFile resourceFile = it.value
            def path = resourceFile.path
            def name = path.substring(path.indexOf("java") + 5, path.lastIndexOf(".")).replace(File.separator, ".")
            classesMap.put(name, resourceFile.microModuleName)
        }

        initMicroModulePackageName()

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
                    if (name.endsWith('.R') || name.endsWith('.BuildConfig')) {
                        handleMicroModuleRAndBuildConfig(microModuleName, name, find, textLines, absolutePath)
                        continue
                    }
                    from = classesMap.get(name)
                }

                if (from != null && microModuleName != from && !microModuleInfo.hasDependency(microModuleName, from)) {
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
                            message += "- cannot use [" + find + "] which from MicroModule '${from}'."
                            message += lineSeparator
                            errorMessage += message
                        }
                    }
                }
            }
        }
    }

    String getMicroModuleName(absolutePath) {
        String moduleName = absolutePath.replace(project.projectDir.absolutePath, "")
        moduleName = moduleName.substring(0, moduleName.indexOf(ResourceMerged.SRC))
        if (File.separator == "\\") {
            moduleName = moduleName.replaceAll("\\\\", ":")
        } else {
            moduleName = moduleName.replaceAll("/", ":")
        }
        return moduleName
    }

    private CheckManifest getModuleCheckManifest() {
        CheckManifest checkManifest = new CheckManifest()
        StringBuilder stringBuilder = new StringBuilder('build/microModule/code-check/')
        if (productFlavor != null) {
            stringBuilder.append(productFlavor)
            stringBuilder.append('/')
        }
        stringBuilder.append(buildType)
        project.file(stringBuilder.toString()).mkdirs()
        stringBuilder.append('/check-manifest.xml')
        File manifest = project.file(stringBuilder.toString())
        checkManifest.load(manifest)
        return checkManifest
    }

    private CheckManifest saveModuleCheckManifest() {
        if (checkManifest == null) {
            checkManifest = new CheckManifest()
        }
        StringBuilder stringBuilder = new StringBuilder('build/microModule/code-check/')
        if (productFlavor != null) {
            stringBuilder.append(productFlavor)
            stringBuilder.append('/')
        }
        stringBuilder.append(buildType)
        project.file(stringBuilder.toString()).mkdirs()
        stringBuilder.append('/check-manifest.xml')
        File manifest = project.file(stringBuilder.toString())
        return checkManifest.save(manifest)
    }

    private void handleMicroModuleRAndBuildConfig(String microModuleName, String name, String find, List<String> textLines, String absolutePath) {
        String packageName = name.substring(0, name.lastIndexOf('.'))
        List<String> microModules = microModulePackageNameMap.get(packageName)
        if (microModules == null || microModules.contains(name)) return

        boolean hasDependency
        List<String> withoutDependency = new ArrayList<>()
        for (String from : microModules) {
            hasDependency = microModuleInfo.hasDependency(microModuleName, from)
            if (hasDependency) {
                break
            }
            withoutDependency.add(from)
        }

        if (hasDependency) {
            return
        }

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
                message += "- cannot use [" + find + "] which from MicroModule '${withoutDependency.get(0)}'."
                message += lineSeparator
                errorMessage += message
            }
        }
    }

    private String initMicroModulePackageName() {
        microModulePackageNameMap = new HashMap<>()
        microModuleInfo.includeMicroModules.each {
            MicroModule microModule = it.value
            boolean find = false
            List<String> flavorList = productFlavorInfo.combinedProductFlavorsMap.get(productFlavor)
            if (flavorList != null && !flavorList.isEmpty()) {
                for (String flavor : flavorList) {
                    File manifest = new File(microModule.microModuleDir, "/src/${flavor}/AndroidManifest.xml")
                    if (manifest.exists()) {
                        String packageName = Utils.getAndroidManifestPackageName(manifest)
                        if (packageName != null && !packageName.isEmpty()) {
                            List<String> microModuleList = microModulePackageNameMap.get(packageName)
                            if (microModuleList == null) {
                                microModuleList = new ArrayList<>()
                                microModulePackageNameMap.put(packageName, microModuleList)
                            }
                            microModuleList.add(microModule.name)
                            find = true
                            break
                        }
                    }
                }
            }

            if (!find) {
                File manifest = new File(microModule.microModuleDir, "/src/${buildType}/AndroidManifest.xml")
                if (manifest.exists()) {
                    String packageName = Utils.getAndroidManifestPackageName(manifest)
                    if (packageName != null && !packageName.isEmpty()) {
                        List<String> microModuleList = microModulePackageNameMap.get(packageName)
                        if (microModuleList == null) {
                            microModuleList = new ArrayList<>()
                            microModulePackageNameMap.put(packageName, microModuleList)
                        }
                        microModuleList.add(microModule.name)
                        find = true
                    }
                }
            }

            if (!find) {
                File manifest = new File(microModule.microModuleDir, "/src/main/AndroidManifest.xml")
                if (manifest.exists()) {
                    String packageName = Utils.getAndroidManifestPackageName(manifest)
                    if (packageName != null && !packageName.isEmpty()) {
                        List<String> microModuleList = microModulePackageNameMap.get(packageName)
                        if (microModuleList == null) {
                            microModuleList = new ArrayList<>()
                            microModulePackageNameMap.put(packageName, microModuleList)
                        }
                        microModuleList.add(microModule.name)
                    }
                }
            }
        }
    }

}