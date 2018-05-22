package com.eastwood.tools.plugins

import com.android.build.gradle.BaseExtension
import com.android.manifmerger.ManifestMerger2
import com.android.manifmerger.MergingReport
import com.android.manifmerger.XmlDocument
import com.android.utils.ILogger
import com.eastwood.tools.plugins.micromodule.AndroidManifest
import com.eastwood.tools.plugins.micromodule.DefaultMicroModuleExtension
import com.eastwood.tools.plugins.micromodule.MicroModule
import com.eastwood.tools.plugins.micromodule.MicroModuleExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

class MicroModulePlugin implements Plugin<Project> {

    Project project
    DefaultMicroModuleExtension microModuleExtension

    public final static String RPath = "/build/generated/source/r/"
    public final static String mainManifestPath = "/src/main/AndroidManifest.xml"

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

        project.afterEvaluate {
            handleMainMicroModule()

            project.tasks.preBuild.doFirst {
                handleMainMicroModule()
                generateR()
            }
        }
    }

    def handleMainMicroModule() {
        if (microModuleExtension.mainMicroModule == null) {
            microModuleExtension.mainMicroModule = new MicroModule()
            def name = ":main"
            def microModuleDir = new File(project.projectDir, "/main")
            if (!microModuleDir.exists()) {
                throw new GradleException("can't find specified micro-module [${name}] under path [${microModuleDir.absolutePath}].")
            }
            microModuleExtension.mainMicroModule.name = name
            microModuleExtension.mainMicroModule.microModuleDir = microModuleDir
        }

        clearModuleSrcDirs("main")

        microModuleExtension.includeMicroModules.each {
            includeMainMicroModule(it)
        }

        includeMainMicroModule(microModuleExtension.mainMicroModule)

        mergeMainAndroidManifest()
    }

    def mergeMainAndroidManifest() {
        File mainManifestFile = new File(microModuleExtension.mainMicroModule.microModuleDir, mainManifestPath)
        ManifestMerger2.MergeType mergeType = ManifestMerger2.MergeType.APPLICATION
        XmlDocument.Type documentType = XmlDocument.Type.MAIN
        ManifestMerger2.Invoker invoker = new ManifestMerger2.Invoker(mainManifestFile, logger, mergeType, documentType)
        invoker.withFeatures(ManifestMerger2.Invoker.Feature.NO_PLACEHOLDER_REPLACEMENT)
        microModuleExtension.includeMicroModules.each {
            def microManifestFile = new File(it.microModuleDir, mainManifestPath)
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

        def saveDir = new File(project.projectDir, "build/microModule/main")
        saveDir.mkdirs()
        def AndroidManifestFile = new File(saveDir, "AndroidManifest.xml")
        AndroidManifestFile.createNewFile()
        AndroidManifestFile.write(moduleAndroidManifest)

        def extensionContainer = project.getExtensions()
        BaseExtension android = extensionContainer.getByName('android')
        android.sourceSets.main.manifest.srcFile project.projectDir.absolutePath + "/build/microModule/main/AndroidManifest.xml"
    }

    def includeMainMicroModule(MicroModule microModule) {
        includeMicroModule(microModule, "main")
        includeMicroModule(microModule, "androidTest")
        includeMicroModule(microModule, "test")

        checkMicroModuleReference(microModule)
    }

    def includeMicroModule(MicroModule microModule, def type) {
        def absolutePath = microModule.microModuleDir.absolutePath
        BaseExtension android = project.extensions.getByName('android')
        def obj = android.sourceSets.getByName(type)
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

    def clearModuleSrcDirs(def type) {
        def srcDirs = []
        BaseExtension android = project.extensions.getByName('android')
        def obj = android.sourceSets.getByName(type)
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

    def checkMicroModuleReference(MicroModule microModule) {
        def microPropertiesFile = new File(microModule.microModuleDir, 'micro.properties')
        // check [micro.properties] exists or not
        if (!microPropertiesFile.exists()) return
        // read micro-module properties
        def microProperties = new Properties()
        microPropertiesFile.withInputStream { microProperties.load(it) }
        microProperties = new ConfigSlurper().parse(microProperties)
        // check micro-module reference
        microProperties.microModule.reference.each {
            isReferenceMicroModuleInclude(microModule, it.value)
        }
    }

    def isReferenceMicroModuleInclude(MicroModule microModule, String path) {
        MicroModule referenceMicroModule = microModuleExtension.buildMicroModule(path)
        if (referenceMicroModule == null) {
            throw new GradleException("can't find specified microModule '${path}', which is referenced by microModle${microModule.name}")
        }

        boolean include = false
        microModuleExtension.includeMicroModules.each {
            if (it.name == referenceMicroModule.name) {
                include = true
            }
        }
        if (microModuleExtension.mainMicroModule.name == referenceMicroModule.name) {
            include = true
        }
        if (!include) {
            throw new GradleException("microModle${referenceMicroModule.name} is referenced by microModle${microModule.name}, but its not included.")
        }
    }

    def generateR() {
        def microManifestFile = new File(microModuleExtension.mainMicroModule.microModuleDir, mainManifestPath)
        def mainPackageName = getPackageName(microManifestFile)
        BaseExtension extension = (BaseExtension) project.extensions.getByName("android")
        extension.buildTypes.each {
            def buildType = it.name
            if (extension.productFlavors.size() == 0) {
                generateRByProductFlavorBuildType(mainPackageName, buildType, null)
            } else {
                extension.productFlavors.each {
                    generateRByProductFlavorBuildType(mainPackageName, buildType, it.name)
                }
            }
        }
    }

    def generateRByProductFlavorBuildType(mainPackageName, buildType, productFlavor) {
        def buildTypeFirstUp = upperCase(buildType)
        def productFlavorFirstUp = productFlavor != null ? upperCase(productFlavor) : ""
        def taskName = "process${productFlavorFirstUp}${buildTypeFirstUp}Resources"
        def mergeResourcesTask = project.tasks.findByName(taskName)
        if (mergeResourcesTask != null) {
            mergeResourcesTask.doLast {
                def productFlavorBuildType = productFlavor != null ? (productFlavor + "/" + buildType) : buildType
                def path = project.projectDir.absolutePath + RPath + productFlavorBuildType + "/" + mainPackageName.replace(".", "/") + "/R.java"
                def file = project.file(path)
                def newR = file.text.replace("public final class R", "public class R")
                file.write(newR)
                generateMicroModuleResources(mainPackageName, productFlavorBuildType)
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

}