package com.eastwood.tools.plugins.core.extension

interface OnMavenArtifactListener {

    void onUseMavenArtifactChanged(boolean value)

    void onMavenArtifactChanged(MavenArtifact artifact)

}
