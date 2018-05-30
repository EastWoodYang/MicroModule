package com.eastwood.tools.plugins

import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.TestedExtension
import com.eastwood.tools.plugins.core.MicroModuleCodeCheck
import org.gradle.api.Plugin
import org.gradle.api.Project

class MicroModuleCodeCheckPlugin implements Plugin<Project> {

    Project project
    Map<String, List<String>> microModuleReferenceMap

    void apply(Project project) {
        this.project = project

        project.afterEvaluate {
            microModuleReferenceMap = MicroModulePlugin.microModuleReferenceMap

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
        MicroModuleCodeCheck microModuleCodeCheck

        def buildTypeFirstUp = upperCase(buildType)
        def productFlavorFirstUp = productFlavor != null ? upperCase(productFlavor) : ""

        def mergeResourcesTaskName = taskPrefix + productFlavorFirstUp + buildTypeFirstUp + "Resources"
        def packageResourcesTask = project.tasks.findByName(mergeResourcesTaskName)
        if (packageResourcesTask != null) {
            microModuleCodeCheck = new MicroModuleCodeCheck(project, microModuleReferenceMap)
            packageResourcesTask.doLast {
                microModuleCodeCheck.checkResources(mergeResourcesTaskName)
            }
        }

        def compileJavaTaskName = "compile${productFlavorFirstUp}${buildTypeFirstUp}JavaWithJavac"
        def compileJavaTask = project.tasks.findByName(compileJavaTaskName)
        if (compileJavaTask != null) {
            compileJavaTask.doLast {
                if (microModuleCodeCheck == null) {
                    microModuleCodeCheck = new MicroModuleCodeCheck(project, microModuleReferenceMap)
                }
                def productFlavorBuildType = productFlavor != null ? (productFlavor + File.separator + buildType) : buildType
                microModuleCodeCheck.checkClasses(productFlavorBuildType, mergeResourcesTaskName)
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
