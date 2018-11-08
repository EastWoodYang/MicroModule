package com.eastwood.tools.plugins.core.extension

class MavenRepository {

    String url
    Object authentication

    void url(String url) {
        this.url = url
    }

    void authentication(Object values) {
        this.authentication = values
    }

}