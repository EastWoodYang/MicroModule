# MicroModule
重新定义Android模块结构，在模块内部可以创建多个和模块结构一致的微模块（MicroModule）。每一个MicroModule的结构和Android模块结构保持一致，也会有自己的`build.gradle`。另外，你可以很方便的配置哪些MicroModule参与APK的编译。

<img src='https://github.com/EastWoodYang/MicroModule/blob/master/picture/1.png'/>

## Usage

### 在根项目`build.gradle`中添加MicroModule插件依赖：

```
buildscript {
    dependencies {
        ...
        classpath 'com.eastwood.tools.plugins:micro-module:1.4.0'
    }
}
```

### 在`application`或`library`类型的模块`build.gradle`中添加MicroModule插件：

```
apply plugin: 'micro-module'
apply plugin: 'com.android.library' // or 'com.android.application'

android {}

microModule {
    ...
}

dependencies {}
```

注意：MicroModule插件需要添加在android相关插件之前，相关配置`microModule {}` 需要添加在 `android {}` 和 `dependencies {}`之间。

### microModule属性说明

* **`include`**

    声明一个或多个MicroModule，类似于`setting.gradle`中的`include`，MicroModule目录名即为MicroModule的名称。

    ```
    microModule {
        include 'p_base', 'p_common'

        // 可以根据条件动态声明
        if(debug) {
            include 'debug'
        } else {
            include 'debug'
        }
    }
    ```

* **`export`**

    配置参与APK编译的MicroModule。如果未配置`export`，则所有`include`的MicroModule都会参与APK编译。

    ```
    microModule {
        include 'feature_A', 'feature_B', 'feature_C'

        export 'feature_A', 'feature_B'
    }
    ```

* **`includeMain`**

    指定主MicroModule。
    当前模块的其他MicroModule的`AndroidManifest.xml`，将会合入主MicroModule的`AndroidManifest.xml`，并存放在`build/microModule/merge-manifest/`下。另外，当前模块的R类包名也将由主模块`AndroidManifest.xml`的`package`决定。

    默认主MicroModule为目录名为`main`的MicroModule。通过MicroModule Android Studio插件的转换功能，将模块转换成MicroModule格式时，无需指定主模块。转换功能工作只是创建一个`main`目录，并将原先`src`移动到`main`目录下，以及其他操作。


* **`codeCheckEnabled`**

    是否开启MicroModule代码边界检查，默认不开启检查。

	有些场景下可能想使MicroModule在模块中保持独立，其类或资源不被该模块的其他MicroModule引用。代码边界检查在`sync&build`的时候进行，检测到没有依赖而存在引用时，会报错以及停止`sync&build`,并输出相应日志提示。

    开启代码边界检查后，一个模块内的MicroModule之间，需要声明依赖关系。例如：
    ```
    // Module build.gradle

    microModule {
        codeCheckEnabled true

        include 'p_base', 'p_common'
        include 'feature_A', 'feature_B'

        export 'feature_A'
    }

    // MicroModule feature_A build.gradle

    dependencies {
        implementation microModule(':p_base')
        implementation microModule(':p_common')
    }

    // MicroModule feature_B build.gradle

    dependencies {
        implementation microModule(':p_base')
        implementation microModule(':p_common')
    }
    ```

    另外MicroModule所需的依赖，也可以在各自的`build.gradle` `dependencies {}`中声明（此处的依赖不在代码边界检查范围之内）。

    ```
    dependencies {
        implementation fileTree(dir: 'main/libs', include: ['*.jar'])
        implementation 'com.android.support:appcompat-v7:27.1.1'
        implementation 'com.android.support.constraint:constraint-layout:1.1.0'

        implementation microModule(':p_base')
        implementation microModule(':p_common')
    }
    ```

## MicroModule Android Studio Plugin
Provides an action which allow you quickly create MicroModule or convert module to MicroModule.
* Right click at module dir, in [New] group, you will find "MicroModule" action.
* Right click at module dir, in [Refactor] group, you will find "Convert to MicroModule" action.

<img src='https://github.com/EastWoodYang/MicroModule/blob/master/picture/3-1.png'/>

<img src='https://github.com/EastWoodYang/MicroModule/blob/master/picture/3-2.png'/>

**Install Step**:
1. open [File] -> [Settings...] -> [plugins] -> [Browse repositories...]
2. and search name **MicroModule**

<img src='https://github.com/EastWoodYang/MicroModule/blob/master/picture/4.png'/>

**Plugin detail**:

[https://plugins.jetbrains.com/plugin/10785-micromodule](https://plugins.jetbrains.com/plugin/10785-micromodule)

## Question or Idea
有问题或想法可以直接加我微信: EastWoodYang

## License

```
   Copyright 2018 EastWood Yang

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
```
