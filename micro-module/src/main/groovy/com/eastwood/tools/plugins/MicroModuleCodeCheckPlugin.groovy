package com.eastwood.tools.plugins

import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.TestedExtension
import com.eastwood.tools.plugins.micromodule.DefaultMicroModuleExtension
import com.eastwood.tools.plugins.micromodule.MicroModuleCodeCheck
import com.eastwood.tools.plugins.micromodule.MicroModuleExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class MicroModuleCodeCheckPlugin implements Plugin<Project> {

    Project project

    void apply(Project project) {
        this.project = project

        project.afterEvaluate {
            def taskNamePrefix
            TestedExtension extension = (TestedExtension) project.extensions.getByName("android")
            if (extension instanceof LibraryExtension) {
                taskNamePrefix = 'package'
            } else {
                taskNamePrefix = 'merge'
            }
            extension.buildTypes.each {
                def buildType = it.name
                if (extension.productFlavors.size() == 0) {
                    check(taskNamePrefix, buildType, null)
                } else {
                    extension.productFlavors.each {
                        check(taskNamePrefix, buildType, it.name)
                    }
                }
            }
        }
    }

    def check(taskPrefix, buildType, productFlavor) {
        def buildTypeFirstUp = upperCase(buildType)
        def productFlavorFirstUp = productFlavor != null ? upperCase(productFlavor) : ""
        def mergeTaskName = taskPrefix + productFlavorFirstUp + buildTypeFirstUp + "Resources"
        def compileTaskName = "compile${productFlavorFirstUp}${buildTypeFirstUp}JavaWithJavac"

        MicroModuleCodeCheck microModuleCodeCheck
        def mergeResourcesTask = project.tasks.findByName(mergeTaskName)
        if (mergeResourcesTask != null) {
            microModuleCodeCheck = new MicroModuleCodeCheck(project)
            mergeResourcesTask.doLast {
                microModuleCodeCheck.checkResources(mergeTaskName)
            }
        }

        def compileJavaTask = project.tasks.findByName(compileTaskName)
        if (compileJavaTask != null) {
            compileJavaTask.doLast {
                if (microModuleCodeCheck == null) {
                    microModuleCodeCheck = new MicroModuleCodeCheck(project)
                }
                def productFlavorBuildType = productFlavor != null ? (productFlavor + File.separator + buildType) : buildType
                microModuleCodeCheck.checkClasses(productFlavorBuildType, mergeTaskName)
            }
        }
    }

    def upperCase(String str) {
        char[] ch = str.toCharArray()
        if (ch.size() == 0) return str

        if (ch[0] >= 'a' && ch[0] <= 'z') {
            ch[0] -= 32
        }
        return String.valueOf(ch)
    }

}