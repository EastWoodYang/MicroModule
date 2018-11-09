package com.eastwood.tools.plugins.core.extension

import com.eastwood.tools.plugins.core.MicroModule
import com.eastwood.tools.plugins.core.Utils
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project

class DefaultMicroModuleExtension implements MicroModuleExtension {

    Project project
    OnMicroModuleListener onMicroModuleListener

    DefaultMicroModuleExtension(Project project, OnMicroModuleListener listener) {
        this.project = project
        this.onMicroModuleListener = listener
        MicroModule microModule = Utils.buildMicroModule(project, ':main')
        if (microModule != null) {
            onMicroModuleListener.addMicroModule(microModule, true)
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
    void mainMicroModule(String microModulePath) {
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
