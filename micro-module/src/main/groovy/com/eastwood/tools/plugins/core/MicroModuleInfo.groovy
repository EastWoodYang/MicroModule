package com.eastwood.tools.plugins.core

import org.gradle.api.GradleException
import org.gradle.api.Project

class MicroModuleInfo {

    Project project
    MicroModule mainMicroModule
    List<MicroModule> includeMicroModules

    Map<String, List<String>> microModuleDependency

    MicroModuleInfo(Project project) {
        this.project = project
        this.includeMicroModules = new ArrayList<>()
        microModuleDependency = new HashMap<>()
    }

    void setMainMicroModule(String name) {
        MicroModule microModule = Utils.buildMicroModule(project, name)
        if (microModule == null) {
            throw new GradleException("cannot find main MicroModule '${name}'.")
        }
        this.mainMicroModule = microModule
        addMicroModule(microModule)
    }

    void setMainMicroModule(MicroModule microModule) {
        if (microModule == null) {
            throw new GradleException("main MicroModule cannot be null.")
        }
        this.mainMicroModule = microModule
        addMicroModule(microModule)
    }

    void addMicroModule(MicroModule microModule) {
        for (int i = 0; i < includeMicroModules.size(); i++) {
            if (includeMicroModules.get(i).name.equals(microModule.name)) {
                return
            }
        }
        includeMicroModules.add(microModule)
    }

    MicroModule getMicroModule(String name) {
        for (int i = 0; i < includeMicroModules.size(); i++) {
            MicroModule microModule = includeMicroModules.get(i)
            if (includeMicroModules.get(i).name.equals(name)) {
                return microModule
            }
        }
        return null
    }

    List<String> getMicroModuleDependency(String name) {
        return microModuleDependency.get(name)
    }

    void setMicroModuleDependency(String targetMicroModule, String dependencyMicroModule) {
        List<String> dependencyList = microModuleDependency.get(dependencyMicroModule)
        if (dependencyList == null) {
            dependencyList = new ArrayList<>()
            dependencyList.add(dependencyMicroModule)
            microModuleDependency.put(targetMicroModule, dependencyList)
        } else {
            if (!dependencyList.contains(dependencyMicroModule)) {
                dependencyList.add(dependencyMicroModule)
            }
        }
    }

    boolean isMicroModuleIncluded(String name) {
        boolean included = false
        includeMicroModules.each {
            if (it.name == name) {
                included = true
            }
        }
        return included
    }

}