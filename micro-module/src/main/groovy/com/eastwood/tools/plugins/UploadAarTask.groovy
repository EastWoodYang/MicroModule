package com.eastwood.tools.plugins

import com.eastwood.tools.plugins.core.MicroModule
import com.eastwood.tools.plugins.core.extension.MavenArtifact
import com.eastwood.tools.plugins.core.extension.MavenRepository
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class UploadAarTask extends DefaultTask {

    MicroModule microModule

    @TaskAction
    void upload() {
        MavenArtifact mavenArtifact = microModule.mavenArtifact
        MavenRepository mavenRepository = microModule.mavenRepository
        getLogger().error("\nUpload aar (" + mavenArtifact.groupId + ":" + mavenArtifact.artifactId + ":" + mavenArtifact.version + ") to repository (" + mavenRepository.url + ")")
    }

}