package com.eastwood.tools.plugins.core.extension

import com.eastwood.tools.plugins.core.MicroModule
import com.eastwood.tools.plugins.core.MicroModuleInfo
import com.eastwood.tools.plugins.core.Utils
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project

class DefaultMicroModuleExtension implements MicroModuleExtension {

    Project project
    MicroModuleInfo microModuleInfo
    OnMicroModuleListener onMicroModuleListener

    DefaultMicroModuleExtension(Project project, MicroModuleInfo microModuleInfo) {
        this.project = project
        this.microModuleInfo = microModuleInfo
        microModuleInfo.setMainMicroModule(':main')
    }

    @Override
    void include(String... microModulePaths) {
        int microModulePathsLen = microModulePaths.size()
        for (int i = 0; i < microModulePathsLen; i++) {
            MicroModule microModule = Utils.buildMicroModule(project, microModulePaths[i])
            if (microModule == null) {
                throw new GradleException("cannot find specified MicroModule '${microModulePaths[i]}'.")
            }
            microModuleInfo.addMicroModule(microModule)
            if (onMicroModuleListener != null) {
                onMicroModuleListener.addMicroModule(microModule)
            }
        }
    }

    @Override
    void mainMicroModule(String microModulePath) {
        MicroModule microModule = Utils.buildMicroModule(project, microModulePath)
        if (microModule == null) {
            throw new GradleException("cannot find specified MicroModule '${microModulePath}'.")
        }
        microModuleInfo.setMainMicroModule(microModule)
        if (onMicroModuleListener != null) {
            onMicroModuleListener.addMicroModule(microModule)
        }
    }

    @Override
    void useMavenArtifact(boolean value) {
        if (onMicroModuleListener != null) {
            onMicroModuleListener.onUseMavenArtifactChanged(value)
        }
    }

    @Override
    void mavenArtifact(Action<? super MavenArtifact> action) {
        if (onMicroModuleListener != null) {
            MavenArtifact artifact = new MavenArtifact()
            action.execute(artifact)
            onMicroModuleListener.onMavenArtifactChanged(artifact)
        }
    }

    @Override
    void mavenRepository(Action<? super MavenRepository> action) {
        if (onMicroModuleListener != null) {
            MavenRepository repository = new MavenRepository()
            action.execute(repository)
            onMicroModuleListener.onMavenRepositoryChanged(repository)
        }
    }

}
