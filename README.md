# MicroModule
Rebuild multiple complete module structures within the module. Each complete module structure we called it MicroModule, Each MicroModule has its own `build.gradle` file where you can declare MicroModule dependencies. In addition, you can decide which MicroModules participate in the compilation of the module.

<img src='https://github.com/EastWoodYang/MicroModule/blob/master/picture/1.png'/>

## Usage
### Add MicroModule plugin **classpath** in root project build.gradle:

    buildscript {
        dependencies {
	        ...
            classpath 'com.eastwood.tools.plugins:micro-module:1.4.0'
        }
    }

### Apply MicroModule plugin in application or library module build.gradle and add configuration options：

    apply plugin: 'micro-module'
    apply plugin: 'com.android.library' // or 'com.android.application'

    android {}

	microModule {
	    ...
	}

	dependencies {}

Apply MicroModule plugin **must before** apply android plugin, and `microModule {}` should between `android {}` and `dependencies {}`.

The MicroModule plugin defines the following methods in `microModule {}`:
* **`codeCheckEnabled`**--`boolean`

    Prevent two non-dependent MicroModules from generating references. Use `codeCheckEnabled` to declared code check enable state, 'true' as default.

* **`includeMain`**--`String`

    Declare main MicroModule, affects the package name of the generated R class, and the AndroidManifest.xml merge. if not declared, will be declared as default if file with name `main` exist.

* **`include`**--`String[]`

    Declare other MicroModules.

* **`export`**--`String[]`

    Use `export` to decide which MicroModules participate in the compilation of the module. if not declared, all MicroModules which decleard by `include`, will participate in the compilation of the module.


*Example 1. build.gradle file of library module in the dome.*

	microModule {
	    codeCheckEnabled true
	    include ':p_base'
	    include ':p_common'
	    include ':p_utils'
	    export ':main'
	}

### Declare MicroModule dependencies in MicroModule build.gradle:
The MicroModule plugin provides a simple method for declaring dependencies on other MicroModules in `dependencies {}`.

	dependencies {
	    implementation microModule(':p_common')
	}


The method **`microModule`** has a only `string` parameter, the name of the MicroModule.

You can also declare dependencies on the other third party libraries in `dependencies {}`.

*Example 2. build.gradle file of main MicroModule in the demo.*

	dependencies {
	    implementation fileTree(dir: 'main/libs', include: ['*.jar'])
	    implementation 'com.android.support:appcompat-v7:27.1.1'
	    implementation 'com.android.support.constraint:constraint-layout:1.1.0'

	    implementation microModule(':p_common')
	}


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
