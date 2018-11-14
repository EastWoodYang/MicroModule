package com.eastwood.tools.plugins.core.extension

import org.gradle.util.ConfigureUtil

class MavenArtifact {

    String groupId
    String artifactId
    String version

    MavenRepository repository

    void groupId(String groupId) {
        this.groupId = groupId
    }

    void artifactId(String artifactId) {
        this.artifactId = artifactId
    }

    void version(String version) {
        this.version = version
    }

    void repository(Closure closure) {
        repository = new MavenRepository()
        ConfigureUtil.configure(closure, repository)
    }

}