package com.eastwood.tools.plugins.create

import com.eastwood.tools.plugins.micromodule.AndroidManifest
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

class CreateMicroModuleTask extends DefaultTask {

    String prefixName = 'p'
    String moduleName
    String packageName

    @TaskAction
    void microModule() {
        if (moduleName == null) return

        Project project = getProject();
        def moduleRootDir = project.file(prefixName + '_' + moduleName)
        if (moduleRootDir.exists()) return
        moduleRootDir.mkdir()

        project.file(moduleRootDir.absolutePath + '/micro.properties').createNewFile()

        if (packageName == null) {
            AndroidManifest androidManifest = new AndroidManifest()
            File file = project.file(project.projectDir.absolutePath + "/" + MicroModulePlugin.mainManifestPath)
            if (!file.exists()) {
                throw new GradleException("Module " + project.name + " without 'main' micro-module directory")
            }
            androidManifest.load(file)
            packageName = androidManifest.packageName
        }

        def mainDir = new File(moduleRootDir, 'src/main')
        mainDir.mkdirs()

        def javaDir = new File(mainDir, 'java')
        javaDir.mkdir()

        def packageDir = new File(javaDir, packageName.replace(".", "/"))
        packageDir.mkdirs()

        def resDir = new File(mainDir, 'res')
        resDir.mkdir()

        def resDirs = ['drawable', 'layouts', 'values']
        resDirs.each {
            def itDir = new File(resDir, it)
            itDir.mkdir()
        }

        def manifestFile = new File(mainDir, 'AndroidManifest.xml')
        String content = '<manifest xmlns:android="http://schemas.android.com/apk/res/android"' + '\n' + '    package="' + packageName + '">' + '\n' + '        <application></application>' + '\n' + '</manifest>';
        manifestFile.write(content, 'utf-8')
    }
}