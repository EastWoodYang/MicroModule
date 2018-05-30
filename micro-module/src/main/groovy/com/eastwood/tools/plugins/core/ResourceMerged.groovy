package com.eastwood.tools.plugins.core

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

class ResourceMerged {

    static String SRC = File.separator + "src" + File.separator

    String projectPath
    File resourcesMergerFile
    NodeList resourcesNodeList
    Map<String, String> resourcesMap


    boolean load(File projectDir, String mergeTaskName) {
        projectPath = projectDir.absolutePath
        String mergedPath = "build/intermediates/incremental/${mergeTaskName}/merger.xml"
        resourcesMergerFile = new File(projectDir, mergedPath)
        return resourcesMergerFile.exists()
    }

    NodeList getResourcesNodeList() {
        if (resourcesNodeList != null) return resourcesNodeList

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance()
        DocumentBuilder builder = factory.newDocumentBuilder()

        FileInputStream inputStream = new FileInputStream(resourcesMergerFile)
        Document doc = builder.parse(inputStream)
        Element rootElement = doc.getDocumentElement()

        NodeList dataSetNodeList = rootElement.getElementsByTagName("dataSet")
        for (int i = 0; i < dataSetNodeList.getLength(); i++) {
            Element dataSetElement = (Element) dataSetNodeList.item(i)
            def config = dataSetElement.getAttribute("config")
            if (config == "main") {
                return dataSetElement.getElementsByTagName("source")
            }
        }
        return null
    }

    Map<String, String> getResourcesMap() {
        if (resourcesMap != null) return resourcesMap

        NodeList resourcesNodeList = getResourcesNodeList()
        Map<String, String> resourcesMap = new HashMap<String, String>()
        if (resourcesNodeList == null || resourcesNodeList.length == 0)
            return resourcesMap

        def resourcesNodeLength = resourcesNodeList.getLength()
        for (int i = 0; i < resourcesNodeLength; i++) {
            Element resourcesElement = (Element) resourcesNodeList.item(i)
            String path = resourcesElement.getAttribute("path")
            String moduleName = path.replace(projectPath, "")
            if (moduleName.startsWith(File.separator + "build")) continue

            moduleName = moduleName.substring(0, moduleName.indexOf(SRC))
            if (File.separator == "\\") {
                moduleName = moduleName.replaceAll("\\\\", ":")
            } else {
                moduleName = moduleName.replaceAll("/", ":")
            }
            NodeList fileNodeList = resourcesElement.getElementsByTagName("file")
            def fileNodeLength = fileNodeList.getLength()
            if (fileNodeLength <= 0) {
                continue
            }
            for (int j = 0; j < fileNodeLength; j++) {
                Element fileElement = (Element) fileNodeList.item(j)
                String name = fileElement.getAttribute("name")
                if (name == "") {
                    NodeList nodeList = fileElement.getChildNodes()
                    def nodeLength = nodeList.getLength()
                    if (nodeLength <= 0) {
                        continue
                    }
                    for (int k = 0; k < nodeLength; k++) {
                        Element childElement = (Element) nodeList.item(k)
                        name = childElement.getAttribute("name")
                        resourcesMap.put(name, moduleName)
                    }
                } else {
                    resourcesMap.put(name, moduleName)
                }
            }
        }

        return resourcesMap
    }

}