package com.eastwood.tools.plugins.core

import org.gradle.api.GradleException
import org.gradle.api.Project

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
            if (microModule == null) {
                throw new GradleException("can't find specified micro-module '${microModulePaths[i]}'.")
            }
            addMicroModule(microModule)
        }
    }

    @Override
    void mainMicroModule(String microModulePath) {
        mainMicroModule = buildMicroModule(microModulePath)
        if (mainMicroModule == null) {
            throw new GradleException("can't find specified micro-module '${microModulePath}'.")
        }
    }

    MicroModule buildMicroModule(String microModulePath) {
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

    private void addMicroModule(MicroModule microModule) {
        for (int i = 0; i < includeMicroModules.size(); i++) {
            if (includeMicroModules.get(i).name.equals(microModule.name)) {
                return
            }
        }
        includeMicroModules.add(microModule)
    }

    private String removeTrailingColon(String microModulePath) {
        return microModulePath.startsWith(":") ? microModulePath.substring(1) : microModulePath
    }

}
