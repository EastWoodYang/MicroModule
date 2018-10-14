package com.eastwood.tools.plugins

import com.android.build.gradle.BaseExtension
import com.android.manifmerger.ManifestMerger2
import com.android.manifmerger.MergingReport
import com.android.manifmerger.XmlDocument
import com.android.utils.ILogger
import com.eastwood.tools.plugins.core.*
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

class MicroModulePlugin implements Plugin<Project> {

    public final static String RPath = "/build/generated/source/r/"
    public final static String mainManifestPath = "/src/main/AndroidManifest.xml"

    Project project
    DefaultMicroModuleExtension microModuleExtension

    MicroModule currentMicroModule
    Map<String, List<String>> microModuleDependencyMap

    ProductFlavorInfo productFlavorInfo

    boolean originSourceSetCleared

    def logger = new ILogger() {
        @Override
        void error(Throwable t, String msgFormat, Object... args) {
            println(msgFormat)
        }

        @Override
        void warning(String msgFormat, Object... args) {

        }

        @Override
        void info(String msgFormat, Object... args) {

        }

        @Override
        void verbose(String msgFormat, Object... args) {

        }
    }

    void apply(Project project) {
        this.project = project
        microModuleExtension = project.extensions.create(MicroModuleExtension, "microModule", DefaultMicroModuleExtension, project)
        checkMainMicroModule()
        microModuleExtension.onMicroModuleListener = new DefaultMicroModuleExtension.OnMicroModuleListener() {
            @Override
            void addMicroModule(MicroModule microModule, boolean mainMicroModule) {
                if (productFlavorInfo == null) {
                    productFlavorInfo = new ProductFlavorInfo(project)
                }

                if (!originSourceSetCleared) {
                    clearOriginSourceSet()
                    if (!mainMicroModule) {
                        includeMicroModule(microModuleExtension.mainMicroModule)
                    }
                    originSourceSetCleared = true
                }

                if (mainMicroModule) {
                    clearOriginSourceSet()
                    includeMicroModule(microModuleExtension.mainMicroModule)
                    microModuleExtension.includeMicroModules.each {
                        if (it.name.equals(microModuleExtension.mainMicroModule.name)) return
                        includeMicroModule(it)
                    }
                }
                includeMicroModule(microModule)
            }
        }

        project.dependencies.metaClass.microModule { path ->
            microModuleDependencyHandler(path)
            return []
        }

        project.afterEvaluate {
            microModuleDependencyMap = new HashMap<>()

            if (!originSourceSetCleared) {
                originSourceSetCleared = true
                clearOriginSourceSet()
                includeMicroModule(microModuleExtension.mainMicroModule)
            }

            // apply MicroModule build.gradle
            applyMicroModuleBuild(microModuleExtension.mainMicroModule)
            List<MicroModule> includeMicroModules = microModuleExtension.includeMicroModules.clone()
            includeMicroModules.each {
                if (it.name == microModuleExtension.mainMicroModule.name) return
                applyMicroModuleBuild(it)
            }

            // Check MicroModule dependency
            checkMicroModuleDependency(microModuleExtension.mainMicroModule)
            microModuleExtension.includeMicroModules.each {
                if (it.name == microModuleExtension.mainMicroModule.name) return
                checkMicroModuleDependency(it)
            }

            generateAndroidManifest()

            project.tasks.preBuild.doFirst {
                setMicroModuleDir()

                generateAndroidManifest()

                generateR()
            }
        }
    }

    def setMicroModuleDir() {
        clearModuleSourceSet("main")
        // include
        includeMicroModule(microModuleExtension.mainMicroModule)
        microModuleExtension.includeMicroModules.each {
            if (it.name == microModuleExtension.mainMicroModule.name) return
            includeMicroModule(it)
        }
    }

    def checkMainMicroModule() {
        if (microModuleExtension.mainMicroModule == null) {
            microModuleExtension.mainMicroModule = new MicroModule()
            def name = ":main"
            def microModuleDir = new File(project.projectDir, "/main")
            if (!microModuleDir.exists()) {
                throw new GradleException("can't find specified MicroModule '${name}' under path [${microModuleDir.absolutePath}].")
            }
            microModuleExtension.mainMicroModule.name = name
            microModuleExtension.mainMicroModule.microModuleDir = microModuleDir
        }
    }

    def generateAndroidManifest() {
        mergeAndroidManifest("main")

        // buildTypes
        productFlavorInfo.buildTypes.each {
            mergeAndroidManifest(it.name)
        }

        if (!productFlavorInfo.singleDimension) {
            productFlavorInfo.productFlavors.each {
                mergeAndroidManifest(it.name)
            }
        }

        productFlavorInfo.combinedProductFlavors.each {
            mergeAndroidManifest(it)

            def productFlavor = it
            productFlavorInfo.buildTypes.each {
                mergeAndroidManifest(productFlavor + upperCase(it.name))
            }
        }

        // androidTest
        def testType = 'androidTest'
        mergeAndroidManifest(testType)
        mergeAndroidManifest(testType + "Debug")
        if (!productFlavorInfo.singleDimension) {
            productFlavorInfo.productFlavors.each {
                mergeAndroidManifest(testType + upperCase(it.name))
            }
        }

        productFlavorInfo.combinedProductFlavors.each {
            mergeAndroidManifest(testType + upperCase(it))
            mergeAndroidManifest(testType + upperCase(it) + "Debug")
        }
    }

    def mergeAndroidManifest(String variantName) {
        File mainManifestFile = new File(microModuleExtension.mainMicroModule.microModuleDir, "/src/${variantName}/AndroidManifest.xml")
        if (!mainManifestFile.exists()) return
        ManifestMerger2.MergeType mergeType = ManifestMerger2.MergeType.APPLICATION
        XmlDocument.Type documentType = XmlDocument.Type.MAIN
        ManifestMerger2.Invoker invoker = new ManifestMerger2.Invoker(mainManifestFile, logger, mergeType, documentType)
        invoker.withFeatures(ManifestMerger2.Invoker.Feature.NO_PLACEHOLDER_REPLACEMENT)
        microModuleExtension.includeMicroModules.each {
            if (it.name.equals(microModuleExtension.mainMicroModule.name)) return
            def microManifestFile = new File(it.microModuleDir, "/src/${variantName}/AndroidManifest.xml")
            if (microManifestFile.exists()) {
                invoker.addLibraryManifest(microManifestFile)
            }
        }
        def mergingReport = invoker.merge()
        if (!mergingReport.result.success) {
            mergingReport.log(logger)
            throw new GradleException(mergingReport.reportString)
        }
        def moduleAndroidManifest = mergingReport.getMergedDocument(MergingReport.MergedManifestKind.MERGED)
        moduleAndroidManifest = new String(moduleAndroidManifest.getBytes("UTF-8"))

        def saveDir = new File(project.projectDir, "build/microModule/${variantName}")
        saveDir.mkdirs()
        def AndroidManifestFile = new File(saveDir, "AndroidManifest.xml")
        AndroidManifestFile.createNewFile()
        AndroidManifestFile.write(moduleAndroidManifest)

        def extensionContainer = project.getExtensions()
        BaseExtension android = extensionContainer.getByName('android')
        def obj = android.sourceSets.findByName(variantName)
        if (obj == null) {
            return
        }
        obj.manifest.srcFile project.projectDir.absolutePath + "/build/microModule/${variantName}/AndroidManifest.xml"
    }

    def includeMicroModule(MicroModule microModule) {
        addModuleSourceSet(microModule, "main")

        // buildTypes
        productFlavorInfo.buildTypes.each {
            addModuleSourceSet(microModule, it.name)
        }

        if (!productFlavorInfo.singleDimension) {
            productFlavorInfo.productFlavors.each {
                addModuleSourceSet(microModule, it.name)
            }
        }

        productFlavorInfo.combinedProductFlavors.each {
            addModuleSourceSet(microModule, it)
            def flavorName = it
            productFlavorInfo.buildTypes.each {
                addModuleSourceSet(microModule, flavorName + upperCase(it.name))
            }
        }

        def testTypes = ['androidTest', 'test']
        testTypes.each {
            def testType = it
            addModuleSourceSet(microModule, testType)

            if (testType == "test") {
                productFlavorInfo.buildTypes.each {
                    addModuleSourceSet(microModule, testType + upperCase(it.name))
                }
            } else {
                addModuleSourceSet(microModule, testType + "Debug")
            }

            if (!productFlavorInfo.singleDimension) {
                productFlavorInfo.productFlavors.each {
                    addModuleSourceSet(microModule, testType + upperCase(it.name))
                }
            }


            productFlavorInfo.combinedProductFlavors.each {
                def productFlavorName = testType + upperCase(it)
                addModuleSourceSet(microModule, productFlavorName)

                if (testType == "test") {
                    productFlavorInfo.buildTypes.each {
                        addModuleSourceSet(microModule, productFlavorName + upperCase(it.name))
                    }
                } else {
                    addModuleSourceSet(microModule, productFlavorName + "Debug")
                }
            }
        }
    }

    def clearOriginSourceSet() {
        clearModuleSourceSet("main")

        // buildTypes
        productFlavorInfo.buildTypes.each {
            clearModuleSourceSet(it.name)
        }

        if (!productFlavorInfo.singleDimension) {
            productFlavorInfo.productFlavors.each {
                clearModuleSourceSet(it.name)
            }
        }

        productFlavorInfo.combinedProductFlavors.each {
            clearModuleSourceSet(it)
            def flavorName = it
            productFlavorInfo.buildTypes.each {
                clearModuleSourceSet(flavorName + upperCase(it.name))
            }
        }

        def testTypes = ['androidTest', 'test']
        testTypes.each {
            def testType = it
            clearModuleSourceSet(testType)

            if (testType == "test") {
                productFlavorInfo.buildTypes.each {
                    clearModuleSourceSet(testType + upperCase(it.name))
                }
            } else {
                clearModuleSourceSet(testType + "Debug")
            }

            if (!productFlavorInfo.singleDimension) {
                productFlavorInfo.productFlavors.each {
                    clearModuleSourceSet(testType + upperCase(it.name))
                }
            }


            productFlavorInfo.combinedProductFlavors.each {
                def productFlavorName = testType + upperCase(it)
                clearModuleSourceSet(productFlavorName)

                if (testType == "test") {
                    productFlavorInfo.buildTypes.each {
                        clearModuleSourceSet(productFlavorName + upperCase(it.name))
                    }
                } else {
                    clearModuleSourceSet(productFlavorName + "Debug")
                }
            }
        }
    }

    def addModuleSourceSet(MicroModule microModule, def type) {
        def absolutePath = microModule.microModuleDir.absolutePath
        BaseExtension android = project.extensions.getByName('android')
        def obj = android.sourceSets.findByName(type)
        if (obj == null) {
            obj = android.sourceSets.create(type)
        }

        obj.java.srcDir(absolutePath + "/src/${type}/java")
        obj.res.srcDir(absolutePath + "/src/${type}/res")
        obj.jni.srcDir(absolutePath + "/src/${type}/jni")
        obj.jniLibs.srcDir(absolutePath + "/src/${type}/jniLibs")
        obj.aidl.srcDir(absolutePath + "/src/${type}/aidl")
        obj.assets.srcDir(absolutePath + "/src/${type}/assets")
        obj.shaders.srcDir(absolutePath + "/src/${type}/shaders")
        obj.resources.srcDir(absolutePath + "/src/${type}/resources")
        obj.renderscript.srcDir(absolutePath + "/src/${type}/rs")
    }

    def clearModuleSourceSet(def type) {
        def srcDirs = []
        BaseExtension android = project.extensions.getByName('android')
        def obj = android.sourceSets.findByName(type)
        if (obj == null) {
            return
        }
        obj.java.srcDirs = srcDirs
        obj.res.srcDirs = srcDirs
        obj.jni.srcDirs = srcDirs
        obj.jniLibs.srcDirs = srcDirs
        obj.aidl.srcDirs = srcDirs
        obj.assets.srcDirs = srcDirs
        obj.shaders.srcDirs = srcDirs
        obj.resources.srcDirs = srcDirs
        obj.renderscript.srcDirs = srcDirs
    }

    def checkMicroModuleDependency(MicroModule microModule) {
        List<String> dependencyList = microModuleDependencyMap.get(microModule.name)
        if (dependencyList == null) return

        for (String path : dependencyList) {
            checkMicroModuleDependency(microModule, path)
        }
    }

    def checkMicroModuleDependency(MicroModule microModule, String path) {
        MicroModule dependencyMicroModule = microModuleExtension.buildMicroModule(path)
        if (dependencyMicroModule == null) {
            throw new GradleException("can't find specified MicroModule '${path}', which is dependent by MicroModle '${microModule.name}'")
        }

        boolean include = false
        microModuleExtension.includeMicroModules.each {
            if (it.name == dependencyMicroModule.name) {
                include = true
            }
        }
        if (microModuleExtension.mainMicroModule.name == dependencyMicroModule.name) {
            include = true
        }

        if (!include) {
            throw new GradleException("MicroModle '${microModule.name}' dependency MicroModle '${dependencyMicroModule.name}', but its not included.")
        }
    }

    def generateR() {
        def microManifestFile = new File(microModuleExtension.mainMicroModule.microModuleDir, mainManifestPath)
        def mainPackageName = getPackageName(microManifestFile)
        productFlavorInfo.buildTypes.each {
            def buildType = it.name
            if (productFlavorInfo.productFlavors.size() == 0) {
                generateRByProductFlavorBuildType(mainPackageName, buildType, null)
            } else {
                if (!productFlavorInfo.singleDimension) {
                    productFlavorInfo.productFlavors.each {
                        generateRByProductFlavorBuildType(mainPackageName, buildType, it.name)
                    }
                }

                productFlavorInfo.combinedProductFlavors.each {
                    generateRByProductFlavorBuildType(mainPackageName, buildType, it)
                    def combinedProductFlavor = it
                    productFlavorInfo.buildTypes.each {
                        generateRByProductFlavorBuildType(mainPackageName, buildType, combinedProductFlavor + upperCase(it.name))
                    }
                }
            }
        }
    }

    def generateRByProductFlavorBuildType(mainPackageName, buildType, productFlavor) {
        def buildTypeFirstUp = upperCase(buildType)
        def productFlavorFirstUp = productFlavor != null ? upperCase(productFlavor) : ""
        def processResourcesTaskName = "process${productFlavorFirstUp}${buildTypeFirstUp}Resources"
        def processResourcesTask = project.tasks.findByName(processResourcesTaskName)
        if (processResourcesTask != null) {
            processResourcesTask.doLast {
                def productFlavorBuildType = productFlavor != null ? (productFlavor + "/" + buildType) : buildType
                def path = project.projectDir.absolutePath + RPath + productFlavorBuildType + "/" + mainPackageName.replace(".", "/") + "/R.java"
                def file = project.file(path)
                def newR = file.text.replace("public final class R", "public class R")
                file.write(newR)
                generateMicroModuleResources(mainPackageName, productFlavorBuildType)
            }
        } else {
            def generateRFileTaskName = "generate${productFlavorFirstUp}${buildTypeFirstUp}RFile"
            def generateRFileTask = project.tasks.findByName(generateRFileTaskName)
            if (generateRFileTask != null) {
                generateRFileTask.doLast {
                    def productFlavorBuildType = productFlavor != null ? (productFlavor + "/" + buildType) : buildType
                    def path = project.projectDir.absolutePath + RPath + productFlavorBuildType + "/" + mainPackageName.replace(".", "/") + "/R.java"
                    def file = project.file(path)
                    def newR = file.text.replace("public final class R", "public class R")
                    file.write(newR)
                    generateMicroModuleResources(mainPackageName, productFlavorBuildType)
                }
            }
        }
    }

    def upperCase(String str) {
        char[] ch = str.toCharArray()
        if (ch[0] >= 'a' && ch[0] <= 'z') {
            ch[0] -= 32
        }
        return String.valueOf(ch)
    }

    def getPackageName(File androidManifestFile) {
        AndroidManifest androidManifest = new AndroidManifest()
        androidManifest.load(androidManifestFile)
        return androidManifest.packageName
    }

    def generateMicroModuleResources(packageName, productFlavorBuildType) {
        def packageNames = []
        microModuleExtension.includeMicroModules.each {
            def microManifestFile = new File(it.microModuleDir, mainManifestPath)
            if (!microManifestFile.exists()) {
                return
            }
            def microModulePackageName = getPackageName(microManifestFile)
            if (microModulePackageName == null || packageNames.contains(microModulePackageName)) return

            packageNames << microModulePackageName
            def path = project.projectDir.absolutePath + RPath + productFlavorBuildType + "/" + microModulePackageName.replace(".", "/")
            File file = project.file(path + "/R.java")
            if (project.file(path).exists()) return
            project.file(path).mkdirs()
            file.write("package " + microModulePackageName + ";\n\n/** This class is generated by micro-module plugin, DO NOT MODIFY. */\npublic class R extends " + packageName + ".R {\n\n}")
            println "[micro-module] - microModule${it.name} generate " + microModulePackageName + '.R.java'
        }
    }

    void applyMicroModuleBuild(MicroModule microModule) {
        def microModuleBuild = new File(microModule.microModuleDir, "build.gradle")
        if (microModuleBuild.exists()) {
            currentMicroModule = microModule
            project.apply from: microModuleBuild.absolutePath
        }
    }

    def microModuleDependencyHandler(String path) {
        if (microModuleExtension == null || currentMicroModule == null) {
            return
        }
        MicroModule microModule = microModuleExtension.buildMicroModule(path)
        if (microModule == null) {
            throw new GradleException("can't find specified MicroModule '${path}', which is dependent by MicroModle '${currentMicroModule.name}'")
        }
        List<String> dependencyList = microModuleDependencyMap.get(currentMicroModule.name)
        if (dependencyList == null) {
            dependencyList = new ArrayList<>()
            dependencyList.add(microModule.name)
            microModuleDependencyMap.put(currentMicroModule.name, dependencyList)
        } else {
            if (!dependencyList.contains(microModule.name)) {
                dependencyList.add(microModule.name)
            }
        }
    }

}