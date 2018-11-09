package com.eastwood.tools.plugins.core.extension

import com.eastwood.tools.plugins.core.MicroModule

interface OnMicroModuleListener {

    void addMicroModule(MicroModule microModule, boolean mainMicroModule)

    void onUseMavenArtifactChanged(boolean value)

    void onMavenArtifactChanged(MavenArtifact artifact)

    void onMavenRepositoryChanged(MavenRepository repository)

}
