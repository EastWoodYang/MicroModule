package com.eastwood.tools.plugins.micromodule

import com.eastwood.tools.plugins.create.CreateMicroModule
import com.eastwood.tools.plugins.create.CreateMicroModuleTask
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.TaskContainer

public class DefaultMicroModuleExtension implements MicroModuleExtension {

    Project project

    MicroModule mainMicroModule
    List<MicroModule> includeMicroModules

    DefaultMicroModuleExtension(Project project) {
        this.project = project
        this.includeMicroModules = new ArrayList<>()
    }

    @Override
    void include(String... microModulePaths) {
        int microModulePathsLen = microModulePaths.size()
        for (int i = 0; i < microModulePathsLen; i++) {
            MicroModule microModule = buildMicroModule(microModulePaths[i])
            if(microModule == null) {
                throw new GradleException("can't find specified micro-module '${microModulePaths[i]}'.")
            }
            includeMicroModules.add(microModule)
        }
    }

    @Override
    void mainMicroModule(String microModulePath) {
        mainMicroModule = buildMicroModule(microModulePath)
        if(mainMicroModule == null) {
            throw new GradleException("can't find specified micro-module '${microModulePath}'.")
        }
    }

    public MicroModule buildMicroModule(String microModulePath) {
        String[] pathElements = removeTrailingColon(microModulePath).split(":")
        int pathElementsLen = pathElements.size()
        File parentMicroModuleDir = project.projectDir
        for (int j = 0; j < pathElementsLen; j++) {
            parentMicroModuleDir = new File(parentMicroModuleDir, pathElements[j])
        }
        File microModuleDir = parentMicroModuleDir.canonicalFile
        String microModuleName = microModuleDir.absolutePath.replace(project.projectDir.absolutePath, "")
        if (File.separator == "\\") {
            microModuleName = microModuleName.replaceAll("\\\\", ":")
        } else {
            microModuleName = microModuleName.replaceAll("/", ":")
        }
        if (!microModuleDir.exists()) {
            return null
        }
        MicroModule microModule = new MicroModule()
        microModule.name = microModuleName
        microModule.microModuleDir = microModuleDir
        return microModule
    }


    private String removeTrailingColon(String microModulePath) {
        return microModulePath.startsWith(":") ? microModulePath.substring(1) : microModulePath
    }


    private CreateMicroModule createMicroModule = new CreateMicroModule() {

        def prefixName
        def moduleName
        def packageName

        @Override
        void prefixName(String name) {
            prefixName = name
        }

        @Override
        void moduleName(String name) {
            moduleName = name
        }

        @Override
        void packageName(String name) {
            packageName = name
        }

        @Override
        String prefixName() {
            return prefixName
        }

        @Override
        String moduleName() {
            return moduleName
        }

        @Override
        String packageName() {
            return packageName
        }
    };

    @Override
    void createMicroModule(Action<CreateMicroModule> configureAction) {
        configureAction.execute(createMicroModule)
        if(createMicroModule.moduleName() != null) {
            configureCreateMicroModuleTask()
        }
    }

    def configureCreateMicroModuleTask() {
        TaskContainer tasks = project.getTasks();
        CreateMicroModuleTask createMicroModuleTask = tasks.create('createMicroModule', CreateMicroModuleTask.class);
        createMicroModuleTask.setDescription("Create Micro Module.");
        createMicroModuleTask.setGroup('create');

        if(createMicroModule.prefixName() != null) {
            createMicroModuleTask.prefixName = createMicroModule.prefixName()
        }
        createMicroModuleTask.moduleName = createMicroModule.moduleName()
        createMicroModuleTask.packageName = createMicroModule.packageName()
    }
}
