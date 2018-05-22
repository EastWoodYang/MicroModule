package com.eastwood.tools.plugins.micromodule

import com.eastwood.tools.plugins.create.CreateMicroModule
import org.gradle.api.Action

public interface MicroModuleExtension {

    void include(String... microModulePaths)

    void mainMicroModule(String microModulePath)

    void createMicroModule(Action<CreateMicroModule> configureAction);

}
