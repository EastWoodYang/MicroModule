package com.eastwood.tools.plugins.core.extension

import com.eastwood.tools.plugins.core.MicroModule
import com.eastwood.tools.plugins.core.Utils
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.util.ConfigureUtil

class DefaultMicroModuleExtension implements MicroModuleExtension {

    Project project
    OnMicroModuleListener onMicroModuleListener

    boolean codeCheckEnabled

    DefaultMicroModuleExtension(Project project, OnMicroModuleListener listener) {
        this.project = project
        this.onMicroModuleListener = listener
        MicroModule microModule = Utils.buildMicroModule(project, ':main')
        if (microModule != null) {
            onMicroModuleListener.addMicroModule(microModule, true)
        }
    }

    @Override
    void codeCheckEnabled(boolean enabled) {
        this.codeCheckEnabled = enabled
    }

    @Override
    void export(String... microModulePaths) {
        if (onMicroModuleListener != null) {
            onMicroModuleListener.exportMicroModule(microModulePaths)
        }
    }

    @Override
    void include(String... microModulePaths) {
        int microModulePathsLen = microModulePaths.size()
        for (int i = 0; i < microModulePathsLen; i++) {
            MicroModule microModule = Utils.buildMicroModule(project, microModulePaths[i])
            if (microModule == null) {
                throw new GradleException("MicroModule with path ':${microModulePaths[i]}' could not be found in ${project.getDisplayName()}.")
            }
            onMicroModuleListener.addMicroModule(microModule, false)
        }
    }

    @Override
    void includeMain(String microModulePath) {
        MicroModule microModule = Utils.buildMicroModule(project, microModulePath)
        if (microModule == null) {
            throw new GradleException("MicroModule with path ':${microModulePath}' could not be found in ${project.getDisplayName()}.")
        }
        onMicroModuleListener.addMicroModule(microModule, true)
    }

    @Override
    void useMavenArtifact(boolean value) {
        if (onMicroModuleListener != null) {
            onMicroModuleListener.onUseMavenArtifactChanged(value)
        }
    }

    @Override
    void mavenArtifact(Closure closure) {
        if (onMicroModuleListener != null) {
            MavenArtifact mavenArtifact = new MavenArtifact()
            ConfigureUtil.configure(closure, mavenArtifact)
            onMicroModuleListener.onMavenArtifactChanged(mavenArtifact)
        }
    }

}
