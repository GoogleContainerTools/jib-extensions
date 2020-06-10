# Jib Extensions

This repository contains extensions to the
[Jib](https://github.com/GoogleContainerTools/jib) Maven and Gradle build plugins.

The Jib Extension Framework enables anyone to easily extend and tailor the Jib plugins behavior to their liking. Jib extensions are supported from Jib Maven 2.3.0 and Jib Gradle 2.4.0.

- [1st-party extensions](first-party): extensions developed and maintained by the Jib team
- [3rd-party extensions](third-party): links to externally developed extensions

## Using Jib Plugin Extensions

### Maven

1. Add extensions as dependencies to the Jib `<plugin>` block in `pom.xml`
2. Specify extension implementation classes with `<pluginExtensions>` in Jib's `<configuration>`.

The following example adds and runs the [Jib Layer-Filter Extension](first-party/jib-layer-filter-extension-maven).

```xml
  <plugin>
    <groupId>com.google.cloud.tools</groupId>
    <artifactId>jib-maven-plugin</artifactId>
    <version>2.4.0</version>

    <!-- 1. have extension classes available on Jib's runtime classpath -->
    <dependencies>
      <dependency>
        <groupId>com.google.cloud.tools</groupId>
        <artifactId>jib-layer-filter-extension-maven</artifactId>
        <version>0.1.0</version>
      </dependency>
    </dependencies>

    <configuration>
      ...
      <pluginExtensions>
        <!-- 2. specify extension implementation classes to load -->
        <pluginExtension>
          <implementation>com.google.cloud.tools.jib.maven.extension.layerfilter.JibLayerFilterExtension</implementation>
        </pluginExtension>
      </pluginExtensions>
    </configuration>
  </plugin>
```

When properly configured and loaded, Jib outputs loaded extension in the log. When you configure multiple `<pluginExtension>`s, Jib runs the extensions in the given order.
```
[INFO] --- jib-maven-plugin:2.4.0:build (default-cli) @ helloworld ---
[INFO] Running extension: com.google.cloud.tools.jib.maven.extension.layerfilter.JibLayerFilterExtension
```

Some extensions may expect you to provide extension-specific user configuration.

- For extensions that accept simple simple properties (map), use `<pluginExtension><properties>`. For example,
   ```xml
           <pluginExtension>
             <implementation>com.example.ExtensionAcceptingMapConfig</implementation>
             <properties>
               <customFlag>true</customFlag>
               <layerName>samples</layerName>
             </properties>
           </pluginExtension>
   ```
- For extensions that define a complex configuration, use `<pluginExtension><configuration implementation=...>` (not Jib's `<configuration>`). Note that the class for the `implementation` XML attribute should be the extension-supplied configuration class and not the main extension class. For example,
   ```xml
           <pluginExtension>
             <implementation>com.google.cloud.tools.jib.maven.extension.layerfilter.JibLayerFilterExtension</implementation>
             <configuration implementation="com.google.cloud.tools.jib.maven.extension.layerfilter.Configuration">
               <filters>
                 <filter>
                   <glob>**/google-*.jar</glob>
                   <toLayer>google libraries</toLayer>
                 </filter>
                 <filter>
                   <glob>/app/libs/in-house-*.jar</glob>
                   <toLayer>in-house dependencies</toLayer>
                 </filter>
               </filters>
             </configuration>
         </pluginExtension>
   ```

### Gradle

1. Have extensions available to the build script (`build.gradle`) by adding them with `buildscript.dependencies` at the beginning of the build script.
2. Configure extension implementation classes with `jib.pluginExtensions`.

The following example adds and runs the [Jib Layer-Filter Extension](first-party/jib-layer-filter-extension-gradle).

```gradle
// should be at the top of build.gradle
buildscript {
  dependencies {
    classpath('com.google.cloud.tools:jib-layer-filter-extension-gradle:0.1.0')
  }
}

...

jib {
  ...
  pluginExtensions {
    pluginExtension {
      implementation = 'com.google.cloud.tools.jib.gradle.extension.layerfilter.JibLayerFilterExtension'
    }
  }
}
```

When properly configured and loaded, Jib outputs loaded extension in the log. When you configure multiple `jib.pluginExtension`s, Jib runs the extensions in the given order.
```
Running extension: com.google.cloud.tools.jib.gradle.extension.layerfilter.JibLayerFilterExtension
```

Some extensions may expect you to provide extension-specific user configuration.

- For extensions that accept simple string properties (map), use `<pluginExtension><properties>`. For example,
   ```gradle
     pluginExtensions {
       pluginExtension {
         implementation = 'com.example.ExtensionAcceptingMapConfig'
         properties = [customFlag: 'true', layerName: 'samples']
       }
     }
   ```
- For extensions that define a complex configuration, use `pluginExtension.configuration` (not `jib.configuration`). For example,
   ```gradle
       pluginExtension {
         implementation = 'com.google.cloud.tools.jib.gradle.extension.layerfilter.JibLayerFilterExtension'
         configuration {
           filters {
             filter {
               glob = '**/google-*.jar'
               toLayer = 'google libraries'
             }
             filter {
               glob = '/app/libs/in-house-*.jar'
               toLayer = 'in-house dependencies'
             }
           }
         }
       }
   ```

## Writing Your Own Extensions

It is easy to write an extension! If you have written a useful extension, let us know and we will put a link in this repo under [`third-party/`](third-party). Or, consider contributing to this repo. Either way, Jib users will greatly appreciate it!

### Project Setup

1. Create a new Java project and add Jib Maven/Gradle Plugin Extension API to the project dependencies.
   - Maven: ['jib-maven-plugin-extension-api`](https://search.maven.org/artifact/com.google.cloud.tools/jib-maven-plugin-extension-api) with `<scope>provided`.
   ```xml
   <dependencies>
      <dependency>
        <groupId>com.google.cloud.tools</groupId>
        <artifactId>jib-maven-plugin-extension-api</artifactId>
        <version>0.3.0</version>
        <scope>provided</scope>
      </dependency>
   </dependencies>
   ```
   - Gradle: ['jib-gradle-plugin-extension-api'](https://search.maven.org/artifact/com.google.cloud.tools/jib-gradle-plugin-extension-api) with `compileOnly`.
   ```gradle
   dependencies {
     compileOnly 'com.google.cloud.tools:jib-gradle-plugin-extension-api:0.3.0'
   }
   ```
2. Add a text file `src/main/resources/com.google.cloud.tools.jib.maven.extension.JibMavenPluginExtension` (Maven) / `src/main/resources/com.google.cloud.tools.jib.gradle.extension.JibGradlePluginExtension` (Gradle) and list your classes that implements the Jib Maven/Gradle Plugin Extension API below. See the [Maven](first-party/jib-ownership-extension-maven/src/main/resources/META-INF/services/com.google.cloud.tools.jib.maven.extension.JibMavenPluginExtension) and [Gradle](first-party/jib-ownership-extension-gradle/src/main/resources/META-INF/services/com.google.cloud.tools.jib.gradle.extension.JibGradlePluginExtension) examples.
3. Implement [`JibMavenPluginExtension`](https://github.com/GoogleContainerTools/jib/blob/master/jib-maven-plugin-extension-api/src/main/java/com/google/cloud/tools/jib/maven/extension/JibMavenPluginExtension.java) (Maven) / [`JibGradlePluginExtension`](https://github.com/GoogleContainerTools/jib/blob/master/jib-gradle-plugin-extension-api/src/main/java/com/google/cloud/tools/jib/gradle/extension/JibGradlePluginExtension.java) (Gradle).

### Updating Container Build Plan

The extension API passes in [`ContainerBuildPlan`](https://github.com/GoogleContainerTools/jib/blob/master/jib-build-plan/src/main/java/com/google/cloud/tools/jib/api/buildplan/ContainerBuildPlan.java), which is the container build plan originally prepared by Jib plugins. The build plan describes in a declarative way how it plans to build a container image.

The class is a Java API for [Container Build Plan Specification](https://github.com/GoogleContainerTools/jib/blob/master/proposals/container-build-plan-spec.md). The Container Build Plan Specification is a general specification independent of Jib. Likewise, the Container Build Plan Java API is a light-weight, standalone API implementing the spec published to Maven Central ([`jib-build-plan`](https://search.maven.org/artifact/com.google.cloud.tools/jib-build-plan). The Build Plan classes, once instantiated, are all stateless, immutable "value classes" (holding only simple values). You can inspect the values using simple getters, and when you want to "modify" values, use `toBuilder()` to create new instances.

### Defining Extension-Specific Configuration

Sometimes, you may want to make your extension configurable by the extension end-users. See #using-jib-plugin-extensions to understand how end-users can provide extra configuration to an extension.

- Simple string properties (map): the Extension API has a built-in support for end-users passing simple string map. If your extension does not need complex configuration structure, prefer this approach.

- Complex configuration structure: define your configuration class and have `getExtraConfigType()` return the class. See the [Maven](first-party/jib-ownership-extension-maven/src/main/java/com/google/cloud/tools/jib/maven/extension/ownership/Configuration.java) and [Gradle](https://github.com/GoogleContainerTools/jib-extensions/blob/master/first-party/jib-ownership-extension-gradle/src/main/java/com/google/cloud/tools/jib/gradle/extension/ownership/Configuration.java) examples.
   - Gradle-specific: your configuration class must have a 1-arg constructor accepting a Gradle [`Project`](https://docs.gradle.org/current/javadoc/org/gradle/api/Project.html).

### Version Matrix

| jib-maven-plugin | jib-maven-plugin-extension-api |
|:----------------:|:------------------------------:|
| 2.3.0 - current  | 0.3.0                          |

| jib-gradle-plugin | jib-gradle-plugin-extension-api |
|:-----------------:|:-------------------------------:|
| 2.4.0 - current   | 0.3.0                           |
