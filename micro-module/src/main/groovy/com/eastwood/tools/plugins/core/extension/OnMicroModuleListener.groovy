package com.eastwood.tools.plugins.core.extension

import com.eastwood.tools.plugins.core.MicroModule

interface OnMicroModuleListener {

    void addIncludeMicroModule(MicroModule microModule, boolean mainMicroModule)

    void addExportMicroModule(String... microModulePaths)

}
