package com.eastwood.tools.plugins.core.extension

import org.gradle.api.Action

interface MicroModuleExtension {

    void include(String... microModulePaths)

    void mainMicroModule(String microModulePath)

    void useMavenArtifact(boolean value)

    void mavenArtifact(Action<? super MavenArtifact> action)

    void mavenRepository(Action<? super MavenRepository> action)

}
