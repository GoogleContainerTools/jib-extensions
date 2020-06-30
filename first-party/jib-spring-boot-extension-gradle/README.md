# Jib Spring Boot Extension

Spring Boot

## Examples

Check out the [genenal instructions](../../README.md#using-jib-plugin-extensions) for applying a Jib plugin extension.

```gradle
// should be at the top of build.gradle
buildscript {
  dependencies {
    classpath('com.google.cloud.tools:jib-spring-boot-extension-gradle:0.1.0')
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
      implementation = 'com.google.cloud.tools.jib.gradle.extension.springboot.JibSpringBootExtension'
    }
  }
}
```
