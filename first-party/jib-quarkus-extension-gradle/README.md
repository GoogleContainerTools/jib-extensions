# Jib Quarkus Extension

***Experimental***: may fail to work on complex projects. Does not cover "native" Quarkus packaging type.

Enables containerizing a Quarkus app built with [Quarkus Gradle Plugin](https://plugins.gradle.org/plugin/io.quarkus).

The Quarkus app framework prepares a special "runner" JAR and aguments dependency JARs, where the standard Jib containerization does not fit. This extension takes the JARs prepared by Quakus (runner JAR and augmented dependency JARs) and sets the entrypoint to run the runner JAR.

## Examples

Check out the [genenal instructions](../../README.md#using-jib-plugin-extensions) for applying a Jib plugin extension.

```gradle
// should be at the top of build.gradle
buildscript {
  dependencies {
    classpath('com.google.cloud.tools:jib-quarkus-extension-gradle:0.1.0')
  }
}

...

jib {
  ...
  container {
    mainClass = 'bogus'  // to suppress Jib warning about missing main class
    ...
    jvmFlags = ['-Dquarkus.http.host=0.0.0.0', '-Djava.util.logging.manager=org.jboss.logmanager.LogManager']
    exposedPorts = [8080]
    user = '1001'
  }
  pluginExtensions {
    pluginExtension {
      implementation = 'com.google.cloud.tools.jib.gradle.extension.quarkus.JibQuarkusExtension'
    }
  }
}
```

## Standard Jib Configurations 

By the way Quarkus needs to run (via `java -jar quarkus-runner.jar`), some standard Jib configruations will have no effect:

- `container.mainClass`
- `container.entrypoint` (Note `container.args` continues to work.)
- `container.extraClasspath`
- `containerizingMode`
