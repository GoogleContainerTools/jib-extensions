# Jib Quarkus Extension

***Experimental***: may fail to work on complex projects. Does not cover "native" Quarkus packaging type.

Enables containerizing a Quarkus app built with [Quarkus Gradle Plugin](https://plugins.gradle.org/plugin/io.quarkus).

The Quarkus app framework has two different package types:
* legacy-jar: original Quarkus "runner" JAR
* fast-jar: default Quarkus package type since [1.12](https://quarkus.io/blog/quarkus-1-12-0-final-released/)

You can use either package type with Jib Quarkus extension by configuring the `pluginExtensions.pluginExtension.properties`. Default value is the legacy-jar, but can be easily changed to fast-jar.

## Examples

Check out the [general instructions](../../README.md#using-jib-plugin-extensions) for applying a Jib plugin extension.

Note that `container.mainClass` should be set to some placeholder value to suppress Jib warning about missing main class. Package type has two valid values: `fast-jar` and `legacy-jar`.

```gradle
// should be at the top of build.gradle
buildscript {
  dependencies {
    classpath('com.google.cloud.tools:jib-quarkus-extension-gradle:0.1.2')
  }
}

...

jib {
  ...
  container {
    mainClass = 'bogus'  // to suppress Jib warning about missing main class
    ...
    jvmFlags = ['-Dquarkus.http.host=0.0.0.0', '-Djava.util.logging.manager=org.jboss.logmanager.LogManager']
    ports = ['8080']
    user = '1001'
  }
  pluginExtensions {
    pluginExtension {
      implementation = 'com.google.cloud.tools.jib.gradle.extension.quarkus.JibQuarkusExtension'
      properties = [packageType: 'fast-jar'] // to use Quarkus fast-jar package type
    }
  }
}
```

## Standard Jib Configurations 

By the way Quarkus needs to run (via `java -jar quarkus-runner.jar` or `java -jar quarkus-app/quarkus-run.jar`), some standard Jib configurations will have no effect:

- `container.mainClass`
- `container.entrypoint` (Note `container.args` continues to work.)
- `container.extraClasspath`
- `containerizingMode`
