package com.eastwood.tools.plugins.core

interface MicroModuleExtension {

    void include(String... microModulePaths)

    void mainMicroModule(String microModulePath)

}
