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
import com.eastwood.tools.plugins.core.check.CodeChecker
import com.eastwood.tools.plugins.core.extension.*
import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.tasks.TaskState
import org.gradle.api.tasks.compile.JavaCompile

class MicroModulePlugin implements Plugin<Project> {

    private final static String R_PATH = "/build/generated/source/r/"
    private final static String R_PATH_3_2 = "/build/generated/not_namespaced_r_class_sources/"
    private final static String MAIN_MANIFEST_PATH = "/src/main/AndroidManifest.xml"
    private final static String UPLOAD_AAR_TASK_PREFIX = "uploadMicroModule"
    private final static String EXCLUDE_BUILD_CONFIG = "**/BuildConfig.java"

    private final static String NORMAL = "normal"
    private final static String UPLOAD_AAR = "upload_aar"
    private final static String ASSEMBLE_OR_GENERATE = "assemble_or_generate"

    private final static String APPLY_NORMAL_MICRO_MODULE_SCRIPT = "apply_normal_micro_module_script"
    private final static String APPLY_INCLUDE_MICRO_MODULE_SCRIPT = "apply_include_micro_module_script"
    private final static String APPLY_UPLOAD_MICRO_MODULE_SCRIPT = "apply_upload_micro_module_script"
    private final static String APPLY_EXPORT_MICRO_MODULE_SCRIPT = "apply_export_micro_module_script"

    private final static TaskExecutionListener taskExecutionListener = new TaskExecutionListener() {
        @Override
        void beforeExecute(Task task) {

        }

        @Override
        void afterExecute(Task task, TaskState taskState) {
            if (task instanceof AndroidJavaCompile && taskState.failure) {
                if(taskState.failure.getCause().getMessage().startsWith("Compilation failed;")) {
                    println '\n* MicroModule Tip: The MicroModule which the classes or resources belong to may not be dependent or included.\n' +
                            '============================================================================================================='
                }
            }
        }
    }
    private final static BuildListener buildListener = new BuildListener() {

        @Override
        void buildStarted(Gradle gradle) {

        }

        @Override
        void settingsEvaluated(Settings settings) {

        }

        @Override
        void projectsLoaded(Gradle gradle) {

        }

        @Override
        void projectsEvaluated(Gradle gradle) {

        }

        @Override
        void buildFinished(BuildResult buildResult) {
            // generate microModules.xml for MicroModule IDEA plugin.
            def ideaFile = new File(buildResult.gradle.rootProject.rootDir, '.idea')
            if (!ideaFile.exists()) return

            def microModuleInfo = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<modules>\n"
            buildResult.gradle.rootProject.allprojects.each {
                MicroModulePlugin microModulePlugin = it.plugins.findPlugin('micro-module')
                if (microModulePlugin == null) return

                def displayName = it.displayName
                microModuleInfo += "    <module name=\"" + displayName.substring(displayName.indexOf("'") + 1, displayName.lastIndexOf("'")) + "\" path=\"" + it.projectDir.getCanonicalPath() + "\">\n"
                microModulePlugin.microModuleInfo.includeMicroModules.each {
                    MicroModule microModule = it.value
                    microModuleInfo += "        <microModule name=\"" + microModule.name + "\" path=\"" + microModule.microModuleDir.getCanonicalPath() + "\" useMavenArtifact=\"" + microModule.useMavenArtifact + "\" />\n"
                }
                microModuleInfo += "    </module>\n"
            }
            microModuleInfo += "</modules>"

            def microModules = new File(ideaFile, 'microModules.xml')
            microModules.write(microModuleInfo, 'utf-8')
        }
    }

    Project project

    String startTaskState = NORMAL
    boolean executeUploadTask
    String uploadMicroModuleName

    MicroModuleInfo microModuleInfo
    ProductFlavorInfo productFlavorInfo

    MicroModule currentMicroModule
    String applyScriptState

    boolean appliedLibraryPlugin

    void apply(Project project) {
        this.project = project
        this.microModuleInfo = new MicroModuleInfo(project)

        project.gradle.removeListener(buildListener)
        project.gradle.addBuildListener(buildListener)

        project.gradle.taskGraph.removeTaskExecutionListener(taskExecutionListener)
        project.gradle.taskGraph.addTaskExecutionListener(taskExecutionListener)

        project.gradle.getStartParameter().taskNames.each {
            if (it.startsWith(UPLOAD_AAR_TASK_PREFIX)) {
                startTaskState = UPLOAD_AAR
                uploadMicroModuleName = ':' + it.substring(it.indexOf('[') + 1, it.lastIndexOf(']'))
            } else if (it.contains('assemble') || it.contains('generate')) {
                startTaskState = ASSEMBLE_OR_GENERATE
            }
        }

        if (startTaskState == UPLOAD_AAR) {
            executeUploadTask = project.gradle.getStartParameter().getProjectDir() == project.getProjectDir()
        }

        if (startTaskState != NORMAL) {
            project.getConfigurations().whenObjectAdded {
                Configuration configuration = it
                configuration.dependencies.whenObjectAdded {
                    if (applyScriptState == APPLY_INCLUDE_MICRO_MODULE_SCRIPT) {
                        configuration.dependencies.remove(it)
                        return
                    } else if (applyScriptState == APPLY_UPLOAD_MICRO_MODULE_SCRIPT
                            || applyScriptState == APPLY_NORMAL_MICRO_MODULE_SCRIPT
                            || applyScriptState == APPLY_EXPORT_MICRO_MODULE_SCRIPT) {
                        return
                    } else if (currentMicroModule == null && startTaskState == ASSEMBLE_OR_GENERATE) {
                        return
                    }

                    configuration.dependencies.remove(it)
                }
            }
        }

        DefaultMicroModuleExtension microModuleExtension = project.extensions.create(MicroModuleExtension, "microModule", DefaultMicroModuleExtension, project)
        microModuleExtension.onMicroModuleListener = new OnMicroModuleListener() {

            @Override
            void addIncludeMicroModule(MicroModule microModule, boolean mainMicroModule) {
                if (startTaskState == UPLOAD_AAR) {
                    if (microModule.name == uploadMicroModuleName) {
                        microModuleInfo.setMainMicroModule(microModule)
                    } else {
                        microModuleInfo.addIncludeMicroModule(microModule)
                    }
                } else {
                    if (mainMicroModule) {
                        microModuleInfo.setMainMicroModule(microModule)
                    } else {
                        microModuleInfo.addIncludeMicroModule(microModule)
                    }
                }
            }

            @Override
            void addExportMicroModule(String... microModulePaths) {
                microModulePaths.each {
                    microModuleInfo.addExportMicroModule(it)
                }
            }

        }

        project.dependencies.metaClass.microModule { String path ->
            if (currentMicroModule == null || applyScriptState == APPLY_NORMAL_MICRO_MODULE_SCRIPT) {
                return []
            }

            if (applyScriptState == APPLY_INCLUDE_MICRO_MODULE_SCRIPT) {
                microModuleInfo.setMicroModuleDependency(currentMicroModule.name, path)
                return []
            }

            MicroModule microModule = microModuleInfo.getMicroModule(path)

            def result = []
            if (startTaskState == UPLOAD_AAR) {
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
            } else if (startTaskState == ASSEMBLE_OR_GENERATE) {
                if (microModule.useMavenArtifact) {
                    checkMicroModuleMavenArtifact(microModule.name)

                    if (!microModule.addDependency) {
                        MavenArtifact mavenArtifact = microModule.mavenArtifact
                        result = mavenArtifact.groupId + ":" + mavenArtifact.artifactId + ":" + mavenArtifact.version
                    }
                } else {
                    addMicroModuleSourceSet(microModule)
                    applyMicroModuleScript(microModule)
                    microModule.appliedScript = true
                }
            }
            return result
        }

        project.afterEvaluate {
            microModuleExtension.onMicroModuleListener = null
            if (microModuleInfo.mainMicroModule == null) {
                throw new GradleException("the main MicroModule could not be found in ${project.getDisplayName()}.")
            }

            if (startTaskState == UPLOAD_AAR && !executeUploadTask) return

            appliedLibraryPlugin = project.pluginManager.hasPlugin('com.android.library')

            productFlavorInfo = new ProductFlavorInfo(project)

            microModuleExtension.onMavenArtifactListener = new OnMavenArtifactListener() {
                @Override
                void onUseMavenArtifactChanged(boolean value) {
                    currentMicroModule.useMavenArtifact = value
                }

                @Override
                void onMavenArtifactChanged(MavenArtifact artifact) {
                    currentMicroModule.mavenArtifact = artifact
                }
            }
            applyScriptState = APPLY_INCLUDE_MICRO_MODULE_SCRIPT
            microModuleInfo.includeMicroModules.each {
                MicroModule microModule = it.value
                microModuleInfo.dependencyGraph.add(microModule.name)
                applyMicroModuleScript(microModule)
            }
            microModuleExtension.onMavenArtifactListener = null

            clearOriginSourceSet()
            if (startTaskState == ASSEMBLE_OR_GENERATE) {
                applyScriptState = APPLY_EXPORT_MICRO_MODULE_SCRIPT
                boolean hasExportMainMicroModule = false
                boolean isEmpty = microModuleInfo.exportMicroModules.isEmpty()
                List<String> dependencySort = microModuleInfo.dependencyGraph.topSort()
                dependencySort.each {
                    if (isEmpty || microModuleInfo.exportMicroModules.containsKey(it)) {
                        MicroModule microModule = microModuleInfo.getMicroModule(it)
                        if (microModule == null) {
                            throw new GradleException("MicroModule with path '${it}' could not be found in ${project.getDisplayName()}.")
                        }

                        if (microModule == microModuleInfo.mainMicroModule) {
                            hasExportMainMicroModule = true
                        }

                        if (microModule.useMavenArtifact) {
                            checkMicroModuleMavenArtifact(it)
                            if (microModule.addDependency) return

                            MavenArtifact mavenArtifact = microModule.mavenArtifact
                            project.dependencies.add('api', mavenArtifact.groupId + ":" + mavenArtifact.artifactId + ":" + mavenArtifact.version)
                            microModule.addDependency = true
                        } else {
                            if(microModule.appliedScript) return

                            addMicroModuleSourceSet(microModule)
                            applyMicroModuleScript(microModule)
                            microModule.appliedScript = true
                        }
                    }
                }

                if (!hasExportMainMicroModule) {
                    throw new GradleException("the main MicroModule '${microModuleInfo.mainMicroModule.name}' is not in the export list.")
                }

                if (microModuleInfo.mainMicroModule.useMavenArtifact) {
                    excludeBuildConfig()
                }
            } else if (startTaskState == UPLOAD_AAR) {
                applyScriptState = APPLY_UPLOAD_MICRO_MODULE_SCRIPT
                MicroModule mainMicroModule = microModuleInfo.mainMicroModule
                checkMicroModuleMavenArtifact(mainMicroModule.name)

                addMicroModuleSourceSet(mainMicroModule)
                createUploadMicroModuleAarTask(mainMicroModule)
                applyMicroModuleScript(mainMicroModule)
            } else {
                applyScriptState = APPLY_NORMAL_MICRO_MODULE_SCRIPT
                microModuleInfo.includeMicroModules.each {
                    MicroModule microModule = it.value
                    addMicroModuleSourceSet(microModule)
                    createUploadMicroModuleAarTask(microModule)
                    applyMicroModuleScript(microModule)
                }
            }

            generateAndroidManifest()

            project.tasks.preBuild.doFirst {
                clearOriginSourceSet()
                if (startTaskState == UPLOAD_AAR) {
                    addMicroModuleSourceSet(microModuleInfo.mainMicroModule)
                } else if (startTaskState == ASSEMBLE_OR_GENERATE) {
                    microModuleInfo.includeMicroModules.each {
                        MicroModule microModule = it.value
                        if (microModule.appliedScript) {
                            addMicroModuleSourceSet(microModule)
                        }
                    }
                } else {
                    microModuleInfo.includeMicroModules.each {
                        addMicroModuleSourceSet(it.value)
                    }
                }

                generateAndroidManifest()

                generateR()
            }

            if (microModuleExtension.codeCheckEnabled) {
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

    def checkMicroModuleMavenArtifact(String name) {
        microModuleInfo.dependencyGraph.bfsDistance(name).each {
            if(it.value == null || it.value == 0) return

            MicroModule childMicroModule = microModuleInfo.getMicroModule(it.key)
            if (!childMicroModule.useMavenArtifact) {
                throw new GradleException("MicroModule '${childMicroModule.name}' should use maven artifact.")
            }
            childMicroModule.addDependency = true
        }
    }

    def generateAndroidManifest() {
        if ((startTaskState == ASSEMBLE_OR_GENERATE || !microModuleInfo.exportMicroModules.isEmpty()) && isMainSourceSetEmpty()) {
            setMainSourceSetManifest()
            return
        }
        mergeAndroidManifest("main")

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

        if (startTaskState != UPLOAD_AAR) {
            microModuleInfo.includeMicroModules.each {
                MicroModule microModule = it.value
                if (startTaskState == ASSEMBLE_OR_GENERATE && !microModule.appliedScript) return
                if (microModule.name == microModuleInfo.mainMicroModule.name) return
                def microManifestFile = new File(microModule.microModuleDir, "/src/${variantName}/AndroidManifest.xml")
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

    def isMainSourceSetEmpty() {
        BaseExtension android = project.extensions.getByName('android')
        def obj = android.sourceSets.findByName("main")
        if (obj == null) {
            return true
        }
        return obj.java.srcDirs.size() == 0;
    }

    def setMainSourceSetManifest() {
        BaseExtension android = project.extensions.getByName('android')
        def obj = android.sourceSets.findByName('main')
        if (obj == null) {
            obj = android.sourceSets.create('main')
        }
        File mainManifestFile = new File(microModuleInfo.mainMicroModule.microModuleDir, "/src/main/AndroidManifest.xml")
        obj.manifest.srcFile mainManifestFile
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

    def generateR() {
        def microManifestFile = new File(microModuleInfo.mainMicroModule.microModuleDir, MAIN_MANIFEST_PATH)
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
                if (project.file(R_PATH).exists()) {
                    def productFlavorBuildType = productFlavor != null ? (productFlavor + "/" + buildType) : buildType
                    path = project.projectDir.absolutePath + R_PATH + productFlavorBuildType + "/"
                } else if (project.file(R_PATH_3_2).exists()) {
                    def productFlavorBuildType = productFlavor != null ? (productFlavor + Utils.upperCase(buildType)) : buildType
                    path = project.projectDir.absolutePath + R_PATH_3_2 + productFlavorBuildType + "/" + processResourcesTaskName + "/r/"
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
                    if (project.file(R_PATH).exists()) {
                        def productFlavorBuildType = productFlavor != null ? (productFlavor + "/" + buildType) : buildType
                        path = project.projectDir.absolutePath + R_PATH + productFlavorBuildType + "/"
                    } else if (project.file(R_PATH_3_2).exists()) {
                        def productFlavorBuildType = productFlavor != null ? (productFlavor + Utils.upperCase(buildType)) : buildType
                        path = project.projectDir.absolutePath + R_PATH_3_2 + productFlavorBuildType + "/" + generateRFileTaskName + "/out/"
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
            MicroModule microModule = it.value
            def microManifestFile = new File(microModule.microModuleDir, MAIN_MANIFEST_PATH)
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

        def taskName = UPLOAD_AAR_TASK_PREFIX + '[' + microModule.microModuleDir.name + ']aar'
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

                        MavenRepository mavenRepository = microModule.mavenArtifact.repository
                        repository(url: mavenRepository.url) {
                            if (mavenRepository.authentication != null) {
                                authentication(mavenRepository.authentication)
                            }
                        }
                    }
                }
            }
            task.dependsOn('clean', 'uploadArchives')
        }
    }

    def excludeBuildConfig() {
        project.gradle.taskGraph.whenReady {
            BaseExtension extension = (BaseExtension) project.extensions.getByName("android")
            extension.buildTypes.each {
                def buildType = Utils.upperCase(it.name)
                if (productFlavorInfo.combinedProductFlavors.size() == 0) {
                    def compileJavaTaskName = "compile${buildType}JavaWithJavac"
                    JavaCompile javaCompile = project.tasks.findByName(compileJavaTaskName)
                    if (javaCompile != null) {
                        javaCompile.exclude(EXCLUDE_BUILD_CONFIG)
                    }
                } else {
                    productFlavorInfo.combinedProductFlavors.each {
                        def compileJavaTaskName = "compile${Utils.upperCase(it)}${buildType}JavaWithJavac"
                        JavaCompile javaCompile = project.tasks.findByName(compileJavaTaskName)
                        if (javaCompile != null) {
                            javaCompile.exclude(EXCLUDE_BUILD_CONFIG)
                        }
                    }
                }
            }
        }

    }

    def check(taskPrefix, buildType, productFlavor, combinedProductFlavors) {
        CodeChecker codeChecker

        def buildTypeFirstUp = Utils.upperCase(buildType)
        def productFlavorFirstUp = productFlavor != null ? Utils.upperCase(productFlavor) : ""

        def mergeResourcesTaskName = taskPrefix + productFlavorFirstUp + buildTypeFirstUp + "Resources"
        def packageResourcesTask = project.tasks.findByName(mergeResourcesTaskName)
        if (packageResourcesTask != null) {
            codeChecker = new CodeChecker(project, microModuleInfo, productFlavorInfo, buildType, productFlavor)
            packageResourcesTask.doLast {
                codeChecker.checkResources(mergeResourcesTaskName, combinedProductFlavors)
            }
        }

        def compileJavaTaskName = "compile${productFlavorFirstUp}${buildTypeFirstUp}JavaWithJavac"
        def compileJavaTask = project.tasks.findByName(compileJavaTaskName)
        if (compileJavaTask != null) {
            compileJavaTask.doLast {
                if (codeChecker == null) {
                    codeChecker = new CodeChecker(project, microModuleInfo, productFlavorInfo, buildType, productFlavor)
                }
                codeChecker.checkClasses(mergeResourcesTaskName, combinedProductFlavors)
            }
        }
    }

}