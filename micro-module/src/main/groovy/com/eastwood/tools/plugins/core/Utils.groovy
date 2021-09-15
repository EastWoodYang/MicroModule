package com.eastwood.tools.plugins.core

import org.gradle.api.Project
import org.w3c.dom.Element

import javax.xml.parsers.DocumentBuilderFactory

class Utils {

    static String upperCase(String str) {
        char[] ch = str.toCharArray()
        if (ch[0] >= 'a' && ch[0] <= 'z') {
            ch[0] -= 32
        }
        return String.valueOf(ch)
    }

    static String getAndroidManifestPackageName(File androidManifest) {
        def builderFactory = DocumentBuilderFactory.newInstance()
        builderFactory.setNamespaceAware(true)
        Element manifestXml = builderFactory.newDocumentBuilder().parse(androidManifest).documentElement
        return manifestXml.getAttribute("package")
    }

    static MicroModule buildMicroModule(Project project, String microModulePath) {
        String[] pathElements = removeTrailingColon(microModulePath).split(":")
        int pathElementsLen = pathElements.size()
        File parentMicroModuleDir = project.projectDir
        for (int j = 0; j < pathElementsLen; j++) {
            parentMicroModuleDir = new File(parentMicroModuleDir, pathElements[j])
        }
        File microModuleDir = parentMicroModuleDir.canonicalFile
        String microModuleName = microModuleDir.absolutePath.replace(project.projectDir.absolutePath, "")
        if (File.separator == "\\") {
            microModuleName = microModuleName.replaceAll("\\\\", ":")
        } else {
            microModuleName = microModuleName.replaceAll("/", ":")
        }
        // in windows and mac system, the file name is not case sensitive, micro-module name may not match file name but run correctly
        // in linux system(as ci server), this case is wrong
        // add this condition to find this error in windows
        if (!microModuleDir.exists() || microModuleName != microModulePath) {
            System.err.println("microModuleDir:" + microModuleDir.getAbsolutePath() + " not exit, or module name and file name case sensitivity wrong")
            return null
        }
        MicroModule microModule = new MicroModule()
        microModule.name = microModuleName
        microModule.microModuleDir = microModuleDir
        return microModule
    }

    private static String removeTrailingColon(String microModulePath) {
        return microModulePath.startsWith(":") ? microModulePath.substring(1) : microModulePath
    }


}
