package com.eastwood.tools.plugins.core

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

}