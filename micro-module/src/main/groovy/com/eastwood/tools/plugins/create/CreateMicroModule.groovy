package com.eastwood.tools.plugins.create

import org.gradle.api.Incubating

@Incubating
public interface CreateMicroModule {

    void prefixName(String name);

    void moduleName(String name);

    void packageName(String name);

    String prefixName();

    String moduleName();

    String packageName();

}