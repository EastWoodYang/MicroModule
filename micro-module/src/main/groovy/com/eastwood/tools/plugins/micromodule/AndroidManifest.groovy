package com.eastwood.tools.plugins.micromodule

import org.w3c.dom.Element

import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

class AndroidManifest {

    private static final String ANDROID_NAME_SPACE = "http://schemas.android.com/apk/res/android"
    String packageName
    Integer versionCode
    String versionName
    Element manifestXml

    void load(File sourceFile) {
        def builderFactory = DocumentBuilderFactory.newInstance()
        builderFactory.setNamespaceAware(true)
        manifestXml = builderFactory.newDocumentBuilder().parse(sourceFile).documentElement
        packageName = manifestXml.getAttribute("package")
        String versionCodeStr = manifestXml.getAttributeNS(ANDROID_NAME_SPACE, "versionCode")
        if (versionCodeStr != "") {
            versionCode = versionCodeStr.toInteger()
        } else {
            versionCode = 0
        }
        versionName = manifestXml.getAttributeNS(ANDROID_NAME_SPACE, "versionName")
    }

    void save(File destFile) {
        if (manifestXml == null) {
            def builderFactory = DocumentBuilderFactory.newInstance()
            builderFactory.setNamespaceAware(true)
            def doc = builderFactory.newDocumentBuilder().newDocument()
            manifestXml = doc.createElement("manifest")
            manifestXml.setAttributeNS(ANDROID_NAME_SPACE, "versionCode", "")
            manifestXml.setAttributeNS(ANDROID_NAME_SPACE, "versionName", "")
        }
        manifestXml.setAttribute("package", packageName)
        manifestXml.getAttributeNodeNS(ANDROID_NAME_SPACE, "versionCode").setValue(versionCode.toString())
        manifestXml.getAttributeNodeNS(ANDROID_NAME_SPACE, "versionName").setValue(versionName)
        TransformerFactory.newInstance().newTransformer().transform(new DOMSource(manifestXml), new StreamResult(destFile))
    }
}