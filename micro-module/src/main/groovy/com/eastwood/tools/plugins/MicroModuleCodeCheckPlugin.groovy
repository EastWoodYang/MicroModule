package com.eastwood.tools.plugins

import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.TestedExtension
import com.eastwood.tools.plugins.core.MicroModuleInfo
import com.eastwood.tools.plugins.core.ProductFlavorInfo
import com.eastwood.tools.plugins.core.Utils
import com.eastwood.tools.plugins.core.check.MicroModuleCodeCheck
import org.gradle.api.Plugin
import org.gradle.api.Project

class MicroModuleCodeCheckPlugin implements Plugin<Project> {

    Project project

    MicroModuleInfo microModuleInfo
    ProductFlavorInfo productFlavorInfo

    void apply(Project project) {
        this.project = project

        project.afterEvaluate {
            MicroModulePlugin microModulePlugin = project.getPlugins().findPlugin("micro-module")
            if (microModulePlugin == null) {

            }
            if (microModulePlugin.mStartTask == MicroModulePlugin.UPLOAD_AAR && !microModulePlugin.runUploadTask) {
                return
            }

            microModuleInfo = microModulePlugin.microModuleInfo
            productFlavorInfo = microModulePlugin.productFlavorInfo

            def taskNamePrefix
            TestedExtension extension = (TestedExtension) project.extensions.getByName("android")
            if (extension instanceof LibraryExtension) {
                taskNamePrefix = 'package'
            } else {
                taskNamePrefix = 'merge'
            }
            extension.buildTypes.each {
                def buildType = it.name
                if (productFlavorInfo.combinedProductFlavors.size() == 0) {
                    List<String> combinedProductFlavors = new ArrayList<>()
                    combinedProductFlavors.add('main')
                    combinedProductFlavors.add(buildType)
                    check(taskNamePrefix, buildType, null, combinedProductFlavors)
                } else {
                    productFlavorInfo.combinedProductFlavors.each {
                        List<String> combinedProductFlavors = new ArrayList<>()
                        combinedProductFlavors.add('main')
                        combinedProductFlavors.addAll(productFlavorInfo.combinedProductFlavorsMap.get(it))
                        combinedProductFlavors.add(buildType)
                        combinedProductFlavors.add(it)
                        combinedProductFlavors.add(it + Utils.upperCase(buildType))
                        check(taskNamePrefix, buildType, it, combinedProductFlavors)
                    }
                }
            }
        }
    }

    def check(taskPrefix, buildType, productFlavor, combinedProductFlavors) {
        MicroModuleCodeCheck microModuleCodeCheck

        def buildTypeFirstUp = Utils.upperCase(buildType)
        def productFlavorFirstUp = productFlavor != null ? Utils.upperCase(productFlavor) : ""

        def mergeResourcesTaskName = taskPrefix + productFlavorFirstUp + buildTypeFirstUp + "Resources"
        def packageResourcesTask = project.tasks.findByName(mergeResourcesTaskName)
        if (packageResourcesTask != null) {
            microModuleCodeCheck = new MicroModuleCodeCheck(project, microModuleInfo, buildType, productFlavor)
            packageResourcesTask.doLast {
                microModuleCodeCheck.checkResources(mergeResourcesTaskName, combinedProductFlavors)
            }
        }

        def compileJavaTaskName = "compile${productFlavorFirstUp}${buildTypeFirstUp}JavaWithJavac"
        def compileJavaTask = project.tasks.findByName(compileJavaTaskName)
        if (compileJavaTask != null) {
            compileJavaTask.doLast {
                if (microModuleCodeCheck == null) {
                    microModuleCodeCheck = new MicroModuleCodeCheck(project, microModuleInfo, buildType, productFlavor)
                }
                microModuleCodeCheck.checkClasses(mergeResourcesTaskName, combinedProductFlavors)
            }
        }
    }

}
