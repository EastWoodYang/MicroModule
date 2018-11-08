package com.eastwood.tools.plugins.core.extension

class MavenArtifact {

    String groupId
    String artifactId
    String version

    void groupId(String groupId) {
        this.groupId = groupId
    }

    void artifactId(String artifactId) {
        this.artifactId = artifactId
    }

    void version(String version) {
        this.version = version
    }

}