package com.eastwood.tools.plugins.core.extension

import com.eastwood.tools.plugins.core.MicroModule

interface OnMicroModuleListener {

    void addMicroModule(MicroModule microModule, boolean mainMicroModule)

    void exportMicroModule(String... microModulePaths)

    void onUseMavenArtifactChanged(boolean value)

    void onMavenArtifactChanged(MavenArtifact artifact)

}
