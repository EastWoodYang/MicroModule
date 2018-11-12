package com.eastwood.tools.plugins

import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.tasks.factory.AndroidJavaCompile
import com.android.manifmerger.ManifestMerger2
import com.android.manifmerger.MergingReport
import com.android.manifmerger.XmlDocument
import com.android.utils.ILogger
import com.eastwood.tools.plugins.core.MicroModule
import com.eastwood.tools.plugins.core.MicroModuleInfo
import com.eastwood.tools.plugins.core.ProductFlavorInfo
import com.eastwood.tools.plugins.core.Utils
import com.eastwood.tools.plugins.core.check.MicroModuleCodeCheck
import com.eastwood.tools.plugins.core.extension.*
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.tasks.TaskState

class MicroModulePlugin implements Plugin<Project> {

    final static String RPath = "/build/generated/source/r/"
    final static String RPath_3_2 = "/build/generated/not_namespaced_r_class_sources/"
    final static String mainManifestPath = "/src/main/AndroidManifest.xml"
    final static String UploadAarTaskPrefix = 'uploadAarForMicroModule_'

    final static String NORMAL = "normal"
    final static String UPLOAD_AAR = "upload_aar"
    final static String ASSEMBLE_OR_GENERATE = "assemble_or_generate"

    static TaskExecutionListener taskExecutionListener

    Project project

    String mStartTask = NORMAL
    boolean runUploadTask

    MicroModuleInfo microModuleInfo
    ProductFlavorInfo productFlavorInfo

    MicroModule currentMicroModule

    boolean clearOriginSourceSet
    boolean applyMainMicroModuleScript
    boolean applyDependencyMicroModuleScript

    String uploadMicroModuleName

    boolean appliedLibraryPlugin

    void apply(Project project) {
        this.project = project
        this.microModuleInfo = new MicroModuleInfo(project)

        if(taskExecutionListener == null) {
            taskExecutionListener = new TaskExecutionListener() {
                @Override
                void beforeExecute(Task task) {

                }

                @Override
                void afterExecute(Task task, TaskState taskState) {
                    if (task instanceof AndroidJavaCompile && taskState.failure) {
                        println '\n* MicroModule Tip: The MicroModule to which classes or resources belong may not be dependent.\n' +
                                '============================================================================================='
                    }
                }
            }
            project.gradle.taskGraph.addTaskExecutionListener(taskExecutionListener)
        }

        project.gradle.getStartParameter().taskNames.each {
            if (it.startsWith(UploadAarTaskPrefix)) {
                mStartTask = UPLOAD_AAR
                uploadMicroModuleName = ':' + it.replace(UploadAarTaskPrefix, '')
            } else if (it.contains('assemble') || it.contains('generate')) {
                mStartTask = ASSEMBLE_OR_GENERATE
            }
        }

        if (mStartTask == UPLOAD_AAR) {
            runUploadTask = project.gradle.getStartParameter().getProjectDir() == project.getProjectDir()
        }

        if (mStartTask != NORMAL) {
            project.getConfigurations().whenObjectAdded {
                Configuration configuration = it
                configuration.dependencies.whenObjectAdded {
                    if (applyDependencyMicroModuleScript) {
                        configuration.dependencies.remove(it)
                        return
                    }

                    if (mStartTask == UPLOAD_AAR || mStartTask == ASSEMBLE_OR_GENERATE) {
                        if (applyMainMicroModuleScript || it instanceof ProjectDependency) {
                            return
                        }
                        configuration.dependencies.remove(it)
                    }
                }
            }
        }

        OnMicroModuleListener onMicroModuleListener = new OnMicroModuleListener() {

            @Override
            void addMicroModule(MicroModule microModule, boolean mainMicroModule) {
                if (mStartTask == UPLOAD_AAR) {
                    if (microModule.name == uploadMicroModuleName) {
                        microModuleInfo.setMainMicroModule(microModule)
                    } else {
                        microModuleInfo.addMicroModule(microModule)
                    }
                } else {
                    if (mainMicroModule) {
                        microModuleInfo.setMainMicroModule(microModule)
                    } else {
                        microModuleInfo.addMicroModule(microModule)
                    }
                }
            }

            @Override
            void onUseMavenArtifactChanged(boolean value) {
                if (currentMicroModule != null) {
                    currentMicroModule.useMavenArtifact = value
                }
            }

            @Override
            void onMavenArtifactChanged(MavenArtifact artifact) {
                if (currentMicroModule != null) {
                    currentMicroModule.mavenArtifact = artifact
                }
            }

            @Override
            void onMavenRepositoryChanged(MavenRepository repository) {
                if (currentMicroModule != null) {
                    currentMicroModule.mavenRepository = repository
                }
            }
        }
        DefaultMicroModuleExtension microModuleExtension = project.extensions.create(MicroModuleExtension, "microModule", DefaultMicroModuleExtension, project, onMicroModuleListener)

        project.dependencies.metaClass.microModule { String path ->
            if (currentMicroModule == null) {
                return []
            }

            if (!applyMainMicroModuleScript || applyDependencyMicroModuleScript) return []

            MicroModule microModule = mStartTask == UPLOAD_AAR ? Utils.buildMicroModule(project, path) : microModuleInfo.getMicroModule(path)
            if (microModule == null) {
                throw new GradleException("MicroModule with path '${path}' could not be found in ${project.getDisplayName()}.")
            }

            def result = []
            if (mStartTask == UPLOAD_AAR) {
                MicroModule temp = currentMicroModule
                applyDependencyMicroModuleScript = true
                applyMicroModuleScript(microModule)
                applyDependencyMicroModuleScript = false
                currentMicroModule = temp

                if (microModule.useMavenArtifact) {
                    result = microModule.mavenArtifact.groupId + ':' + microModule.mavenArtifact.artifactId + ':' + microModule.mavenArtifact.version
                } else {
                    throw new GradleException("make sure the MicroModule '" + path + "' has been uploaded to Maven and add 'microModule { useMavenArtifact true }' to its build.gradle. " +
                            "\nif not, add follow config to its build.gradle and upload :\n\nmicroModule {\n" +
                            "\n" + "    useMavenArtifact true\n" + "\n" + "    mavenArtifact {\n" + "        groupId ...\n" + "        artifactId ...\n" +
                            "        version ...\n" + "    }\n" + "\n" + "    mavenRepository {\n" + "        url ...\n" +
                            "        authentication(userName: ..., password: ...)\n" + "    }\n" + "\n" +
                            "}\n\n* Get more help at https://github.com/EastWoodYang/MicroModule")
                }
            } else if (mStartTask == ASSEMBLE_OR_GENERATE) {
                MicroModule temp = currentMicroModule
                applyDependencyMicroModuleScript = true
                applyMicroModuleScript(microModule)
                applyDependencyMicroModuleScript = false

                if (microModule.useMavenArtifact) {
                    result = microModule.mavenArtifact.groupId + ':' + microModule.mavenArtifact.artifactId + ':' + microModule.mavenArtifact.version
                } else {
                    addMicroModuleSourceSet(microModule)
                    applyMicroModuleScript(microModule)
                    microModule.applyScript = true
                }
                currentMicroModule = temp
            }

            microModuleInfo.setMicroModuleDependency(currentMicroModule.name, microModule.name)

            return result
        }

        project.afterEvaluate {
            if (microModuleInfo.mainMicroModule == null) {
                throw new GradleException("the main MicroModule could not be found in ${project.getDisplayName()}.")
            }

            if (mStartTask == UPLOAD_AAR && !runUploadTask) return

            appliedLibraryPlugin = project.pluginManager.hasPlugin('com.android.library')
            if (productFlavorInfo == null) {
                productFlavorInfo = new ProductFlavorInfo(project)
            }

            clearOriginSourceSet()
            if (mStartTask == NORMAL) {
                microModuleInfo.includeMicroModules.each {
                    addMicroModuleSourceSet(it)
                    applyMicroModuleScript(it)
                    createUploadMicroModuleAarTask(it)
                }
            } else {
                addMicroModuleSourceSet(microModuleInfo.mainMicroModule)
                applyMainMicroModuleScript = true
                applyMicroModuleScript(microModuleInfo.mainMicroModule)
                microModuleInfo.mainMicroModule.applyScript = true

                if (mStartTask == ASSEMBLE_OR_GENERATE) {
                    microModuleInfo.includeMicroModules.each {
                        if (it.applyScript) {
                            checkMicroModuleDependency(it)
                        }
                    }
                } else {
                    if (microModuleInfo.mainMicroModule.name == uploadMicroModuleName) {
                        createUploadMicroModuleAarTask(microModuleInfo.mainMicroModule)
                    }
                }
            }

            generateAndroidManifest()

            project.tasks.preBuild.doFirst {
                clearOriginSourceSet()
                if (mStartTask == UPLOAD_AAR) {
                    addMicroModuleSourceSet(microModuleInfo.mainMicroModule)
                } else if (mStartTask == ASSEMBLE_OR_GENERATE) {
                    microModuleInfo.includeMicroModules.each {
                        if (it.applyScript) {
                            addMicroModuleSourceSet(it)
                        }
                    }
                } else {
                    microModuleInfo.includeMicroModules.each {
                        addMicroModuleSourceSet(it)
                    }
                }

                generateAndroidManifest()

                generateR()
            }

            if (!microModuleExtension.codeCheckEnabled) {
                project.gradle.taskGraph.whenReady {
                    BaseExtension extension = (BaseExtension) project.extensions.getByName("android")
                    def taskNamePrefix = extension instanceof LibraryExtension ? 'package' : 'merge'
                    extension.buildTypes.each {
                        def buildType = it.name
                        if (productFlavorInfo.combinedProductFlavors.size() == 0) {
                            List<String> combinedProductFlavors = new ArrayList<>()
                            combinedProductFlavors.add('main')
                            combinedProductFlavors.add(buildType)
                            check(taskNamePrefix, buildType, null, combinedProductFlavors)
                        } else {
                            productFlavorInfo.combinedProductFlavors.each {
                                List<String> combinedProductFlavors = new ArrayList<>()
                                combinedProductFlavors.add('main')
                                combinedProductFlavors.addAll(productFlavorInfo.combinedProductFlavorsMap.get(it))
                                combinedProductFlavors.add(buildType)
                                combinedProductFlavors.add(it)
                                combinedProductFlavors.add(it + Utils.upperCase(buildType))
                                check(taskNamePrefix, buildType, it, combinedProductFlavors)
                            }
                        }
                    }
                }
            }

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
                mergeAndroidManifest(productFlavor + Utils.upperCase(it.name))
            }
        }

        def androidTest = 'androidTest'
        mergeAndroidManifest(androidTest)
        mergeAndroidManifest(androidTest + "Debug")
        if (!productFlavorInfo.singleDimension) {
            productFlavorInfo.productFlavors.each {
                mergeAndroidManifest(androidTest + Utils.upperCase(it.name))
            }
        }
        productFlavorInfo.combinedProductFlavors.each {
            mergeAndroidManifest(androidTest + Utils.upperCase(it))
            mergeAndroidManifest(androidTest + Utils.upperCase(it) + "Debug")
        }
    }

    def mergeAndroidManifest(String variantName) {
        File mainManifestFile = new File(microModuleInfo.mainMicroModule.microModuleDir, "/src/${variantName}/AndroidManifest.xml")
        if (!mainManifestFile.exists()) return
        ManifestMerger2.MergeType mergeType = ManifestMerger2.MergeType.APPLICATION
        XmlDocument.Type documentType = XmlDocument.Type.MAIN
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
        ManifestMerger2.Invoker invoker = new ManifestMerger2.Invoker(mainManifestFile, logger, mergeType, documentType)
        invoker.withFeatures(ManifestMerger2.Invoker.Feature.NO_PLACEHOLDER_REPLACEMENT)

        if (mStartTask != UPLOAD_AAR) {
            microModuleInfo.includeMicroModules.each {
                if (mStartTask == ASSEMBLE_OR_GENERATE && !it.applyScript) return
                if (it.name == microModuleInfo.mainMicroModule.name) return
                def microManifestFile = new File(it.microModuleDir, "/src/${variantName}/AndroidManifest.xml")
                if (microManifestFile.exists()) {
                    invoker.addLibraryManifest(microManifestFile)
                }
            }
        }

        def mergingReport = invoker.merge()
        if (!mergingReport.result.success) {
            mergingReport.log(logger)
            throw new GradleException(mergingReport.reportString)
        }
        def moduleAndroidManifest = mergingReport.getMergedDocument(MergingReport.MergedManifestKind.MERGED)
        moduleAndroidManifest = new String(moduleAndroidManifest.getBytes("UTF-8"))

        def saveDir = new File(project.projectDir, "build/microModule/merge-manifest/${variantName}")
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
        obj.manifest.srcFile project.projectDir.absolutePath + "/build/microModule/merge-manifest/${variantName}/AndroidManifest.xml"
    }

    def addMicroModuleSourceSet(MicroModule microModule) {
        addVariantSourceSet(microModule, "main")

        // buildTypes
        productFlavorInfo.buildTypes.each {
            addVariantSourceSet(microModule, it.name)
        }

        if (!productFlavorInfo.singleDimension) {
            productFlavorInfo.productFlavors.each {
                addVariantSourceSet(microModule, it.name)
            }
        }

        productFlavorInfo.combinedProductFlavors.each {
            addVariantSourceSet(microModule, it)
            def flavorName = it
            productFlavorInfo.buildTypes.each {
                addVariantSourceSet(microModule, flavorName + Utils.upperCase(it.name))
            }
        }

        def testTypes = ['androidTest', 'test']
        testTypes.each {
            def testType = it
            addVariantSourceSet(microModule, testType)

            if (testType == "test") {
                productFlavorInfo.buildTypes.each {
                    addVariantSourceSet(microModule, testType + Utils.upperCase(it.name))
                }
            } else {
                addVariantSourceSet(microModule, testType + "Debug")
            }

            if (!productFlavorInfo.singleDimension) {
                productFlavorInfo.productFlavors.each {
                    addVariantSourceSet(microModule, testType + Utils.upperCase(it.name))
                }
            }

            productFlavorInfo.combinedProductFlavors.each {
                def productFlavorName = testType + Utils.upperCase(it)
                addVariantSourceSet(microModule, productFlavorName)

                if (testType == "test") {
                    productFlavorInfo.buildTypes.each {
                        addVariantSourceSet(microModule, productFlavorName + Utils.upperCase(it.name))
                    }
                } else {
                    addVariantSourceSet(microModule, productFlavorName + "Debug")
                }
            }
        }
    }

    def clearOriginSourceSet() {
        clearOriginSourceSet = true
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
                clearModuleSourceSet(flavorName + Utils.upperCase(it.name))
            }
        }

        def testTypes = ['androidTest', 'test']
        testTypes.each {
            def testType = it
            clearModuleSourceSet(testType)

            if (testType == "test") {
                productFlavorInfo.buildTypes.each {
                    clearModuleSourceSet(testType + Utils.upperCase(it.name))
                }
            } else {
                clearModuleSourceSet(testType + "Debug")
            }

            if (!productFlavorInfo.singleDimension) {
                productFlavorInfo.productFlavors.each {
                    clearModuleSourceSet(testType + Utils.upperCase(it.name))
                }
            }

            productFlavorInfo.combinedProductFlavors.each {
                def productFlavorName = testType + Utils.upperCase(it)
                clearModuleSourceSet(productFlavorName)

                if (testType == "test") {
                    productFlavorInfo.buildTypes.each {
                        clearModuleSourceSet(productFlavorName + Utils.upperCase(it.name))
                    }
                } else {
                    clearModuleSourceSet(productFlavorName + "Debug")
                }
            }
        }
    }

    def addVariantSourceSet(MicroModule microModule, def type) {
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
        List<String> dependencyList = microModuleInfo.getMicroModuleDependency(microModule.name)
        if (dependencyList == null) return

        for (String name : dependencyList) {
            if (!microModuleInfo.isMicroModuleIncluded(name)) {
                throw new GradleException("MicroModle '${microModule.name}' dependency MicroModle '${name}', but its not included.")
            }
        }
    }

    def generateR() {
        def microManifestFile = new File(microModuleInfo.mainMicroModule.microModuleDir, mainManifestPath)
        def mainPackageName = Utils.getAndroidManifestPackageName(microManifestFile)
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
                        generateRByProductFlavorBuildType(mainPackageName, buildType, combinedProductFlavor + Utils.upperCase(it.name))
                    }
                }
            }
        }
    }

    def generateRByProductFlavorBuildType(mainPackageName, buildType, productFlavor) {
        def buildTypeFirstUp = Utils.upperCase(buildType)
        def productFlavorFirstUp = productFlavor != null ? Utils.upperCase(productFlavor) : ""
        def processResourcesTaskName = "process${productFlavorFirstUp}${buildTypeFirstUp}Resources"
        def processResourcesTask = project.tasks.findByName(processResourcesTaskName)
        if (processResourcesTask != null) {
            processResourcesTask.doLast {
                def path
                if (project.file(RPath).exists()) {
                    def productFlavorBuildType = productFlavor != null ? (productFlavor + "/" + buildType) : buildType
                    path = project.projectDir.absolutePath + RPath + productFlavorBuildType + "/"
                } else if (project.file(RPath_3_2).exists()) {
                    def productFlavorBuildType = productFlavor != null ? (productFlavor + Utils.upperCase(buildType)) : buildType
                    path = project.projectDir.absolutePath + RPath_3_2 + productFlavorBuildType + "/" + processResourcesTaskName + "/r/"
                } else {
                    return
                }
                def file = project.file(path + mainPackageName.replace(".", "/") + "/R.java")
                def newR = file.text.replace("public final class R", "public class R").replace("private R() {}", "")
                file.write(newR)
                generateMicroModuleResources(mainPackageName, path)
            }
        } else {
            def generateRFileTaskName = "generate${productFlavorFirstUp}${buildTypeFirstUp}RFile"
            def generateRFileTask = project.tasks.findByName(generateRFileTaskName)
            if (generateRFileTask != null) {
                generateRFileTask.doLast {
                    def path
                    if (project.file(RPath).exists()) {
                        def productFlavorBuildType = productFlavor != null ? (productFlavor + "/" + buildType) : buildType
                        path = project.projectDir.absolutePath + RPath + productFlavorBuildType + "/"
                    } else if (project.file(RPath_3_2).exists()) {
                        def productFlavorBuildType = productFlavor != null ? (productFlavor + Utils.upperCase(buildType)) : buildType
                        path = project.projectDir.absolutePath + RPath_3_2 + productFlavorBuildType + "/" + generateRFileTaskName + "/out/"
                    } else {
                        return
                    }
                    def file = project.file(path + mainPackageName.replace(".", "/") + "/R.java")
                    def newR = file.text.replace("public final class R", "public class R").replace("private R() {}", "")
                    file.write(newR)
                    generateMicroModuleResources(mainPackageName, path)
                }
            }
        }
    }

    def generateMicroModuleResources(packageName, path) {
        def packageNames = []
        microModuleInfo.includeMicroModules.each {
            if (mStartTask == ASSEMBLE_OR_GENERATE && !it.applyScript) {
                return
            }
            def microManifestFile = new File(it.microModuleDir, mainManifestPath)
            if (!microManifestFile.exists()) {
                return
            }
            def microModulePackageName = Utils.getAndroidManifestPackageName(microManifestFile)
            if (microModulePackageName == null || packageNames.contains(microModulePackageName)) return

            packageNames << microModulePackageName
            def RPath = path + microModulePackageName.replace(".", "/")
            File RFile = project.file(RPath + "/R.java")
            if (RFile.exists()) return
            project.file(RPath).mkdirs()
            RFile.write("package " + microModulePackageName + ";\n\n/** This class is generated by micro-module plugin, DO NOT MODIFY. */\npublic class R extends " + packageName + ".R {\n\n}")
        }
    }

    void applyMicroModuleScript(MicroModule microModule) {
        def microModuleBuild = new File(microModule.microModuleDir, "build.gradle")
        if (microModuleBuild.exists()) {
            currentMicroModule = microModule
            project.apply from: microModuleBuild.absolutePath
            currentMicroModule = null
        }
    }

    def createUploadMicroModuleAarTask(MicroModule microModule) {
        if (!appliedLibraryPlugin || microModule.mavenArtifact == null) return

        def taskName = UploadAarTaskPrefix + microModule.microModuleDir.name
        UploadAarTask task = project.getTasks().create(taskName, UploadAarTask.class)
        task.setMicroModule(microModule)
        task.setGroup('upload')

        if (microModule.name == uploadMicroModuleName) {
            project.pluginManager.apply('maven')
            project.uploadArchives {
                repositories {
                    mavenDeployer {
                        MavenArtifact mavenArtifact = microModule.mavenArtifact
                        pom.groupId = mavenArtifact.groupId
                        pom.artifactId = mavenArtifact.artifactId
                        pom.version = mavenArtifact.version

                        MavenRepository mavenRepository = microModule.mavenRepository
                        repository(url: mavenRepository.url) {
                            if (mavenRepository.authentication != null) {
                                authentication(mavenRepository.authentication)
                            }
                        }
                    }
                }
            }
            def uploadArchives = project.getTasks().getByName('uploadArchives')
            task.dependsOn uploadArchives
        }
    }

    def check(taskPrefix, buildType, productFlavor, combinedProductFlavors) {
        MicroModuleCodeCheck microModuleCodeCheck

        def buildTypeFirstUp = Utils.upperCase(buildType)
        def productFlavorFirstUp = productFlavor != null ? Utils.upperCase(productFlavor) : ""

        def mergeResourcesTaskName = taskPrefix + productFlavorFirstUp + buildTypeFirstUp + "Resources"
        def packageResourcesTask = project.tasks.findByName(mergeResourcesTaskName)
        if (packageResourcesTask != null) {
            microModuleCodeCheck = new MicroModuleCodeCheck(project, microModuleInfo, buildType, productFlavor)
            packageResourcesTask.doLast {
                microModuleCodeCheck.checkResources(mergeResourcesTaskName, combinedProductFlavors)
            }
        }

        def compileJavaTaskName = "compile${productFlavorFirstUp}${buildTypeFirstUp}JavaWithJavac"
        def compileJavaTask = project.tasks.findByName(compileJavaTaskName)
        if (compileJavaTask != null) {
            compileJavaTask.doLast {
                if (microModuleCodeCheck == null) {
                    microModuleCodeCheck = new MicroModuleCodeCheck(project, microModuleInfo, buildType, productFlavor)
                }
                microModuleCodeCheck.checkClasses(mergeResourcesTaskName, combinedProductFlavors)
            }
        }
    }

}