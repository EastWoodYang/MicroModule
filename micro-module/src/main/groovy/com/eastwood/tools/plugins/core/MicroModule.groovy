package com.eastwood.tools.plugins.core

import com.eastwood.tools.plugins.core.extension.MavenArtifact

class MicroModule {

    String name
    File microModuleDir

    boolean useMavenArtifact
    MavenArtifact mavenArtifact

    boolean appliedScript
    boolean addDependency

}