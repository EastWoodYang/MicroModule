package com.eastwood.tools.plugins.micromodule

public interface MicroModuleExtension {

    void include(String... microModulePaths)

    void mainMicroModule(String microModulePath)

}
