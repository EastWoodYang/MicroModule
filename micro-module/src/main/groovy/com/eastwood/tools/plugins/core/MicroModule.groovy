package com.eastwood.tools.plugins.core

import com.eastwood.tools.plugins.core.extension.MavenArtifact
import com.eastwood.tools.plugins.core.extension.MavenRepository

class MicroModule {

    String name
    File microModuleDir

    boolean useMavenArtifact
    MavenArtifact mavenArtifact

    boolean applyScript
    boolean addDependency

}