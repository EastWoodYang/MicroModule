package com.eastwood.tools.plugins.core.extension

import com.eastwood.tools.plugins.core.MicroModule
import com.eastwood.tools.plugins.core.Utils
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.util.ConfigureUtil

class DefaultMicroModuleExtension implements MicroModuleExtension {

    Project project
    OnMicroModuleListener onMicroModuleListener
    OnMavenArtifactListener onMavenArtifactListener

    boolean codeCheckEnabled = true

    DefaultMicroModuleExtension(Project project) {
        this.project = project
    }

    @Override
    void codeCheckEnabled(boolean enabled) {
        this.codeCheckEnabled = enabled
    }

    @Override
    void export(String... microModulePaths) {
        if(onMicroModuleListener == null) return

        onMicroModuleListener.addExportMicroModule(microModulePaths)
    }

    @Override
    void include(String... microModulePaths) {
        if(onMicroModuleListener == null) return

        int size = microModulePaths.size()
        for (int i = 0; i < size; i++) {
            MicroModule microModule = Utils.buildMicroModule(project, microModulePaths[i])
            if (microModule == null) {
                throw new GradleException("MicroModule with path '${microModulePaths[i]}' could not be found in ${project.getDisplayName()}.")
            }
            onMicroModuleListener.addIncludeMicroModule(microModule, false)
        }
    }

    @Override
    void includeMain(String microModulePath) {
        if(onMicroModuleListener == null) return

        MicroModule microModule = Utils.buildMicroModule(project, microModulePath)
        if (microModule == null) {
            throw new GradleException("MicroModule with path '${microModulePath}' could not be found in ${project.getDisplayName()}.")
        }
        onMicroModuleListener.addIncludeMicroModule(microModule, true)
    }

    @Override
    void useMavenArtifact(boolean value) {
        if(onMavenArtifactListener == null) return

        onMavenArtifactListener.onUseMavenArtifactChanged(value)
    }

    @Override
    void mavenArtifact(Closure closure) {
        if(onMavenArtifactListener == null) return

        MavenArtifact mavenArtifact = new MavenArtifact()
        ConfigureUtil.configure(closure, mavenArtifact)
        onMavenArtifactListener.onMavenArtifactChanged(mavenArtifact)
    }

}
