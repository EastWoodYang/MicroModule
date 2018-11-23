package com.eastwood.tools.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project

@Deprecated
class MicroModuleCodeCheckPlugin implements Plugin<Project> {

    void apply(Project project) {
        println "The plugin 'micro-module-code-check' of MicroModule 1.2.0 or higher is applied by default.\nSee https://github.com/EastWoodYang/MicroModule"
    }

}
