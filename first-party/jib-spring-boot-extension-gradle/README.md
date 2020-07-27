# Jib Spring Boot Extension

Provides extra support for Spring Boot applications. As of now, this extension provides the following functionalities:

- Including and excluding `spring-boot-devtools`

   Handles the [Spring Boot developer tools issue](https://github.com/GoogleContainerTools/jib/issues/2336) that Jib always (correctly) packages the [`spring-boot-devtools`](https://docs.spring.io/spring-boot/docs/current/reference/html/using-spring-boot.html#using-boot-devtools) dependency. Applying this extension makes Jib include or exclude the dependency in the same way Spring Boot does for their repackaged fat JAR. For example, Spring Boot by default excludes `spring-boot-devtools` from the repackaged JAR, so the extension by default excludes it from an image too. On the other hand, if you include the dependency in the classpath of the `bootJar` task (with Spring Boot 2.3.0 or later), the extension does nothing (keeps the dependency in the image).

   Note that one can still properly and correctly resolve this "issue" without this extension, for example, by setting up two Gradle profiles, as explained in the issue link above.

## Examples

Check out the [genenal instructions](../../README.md#using-jib-plugin-extensions) for applying a Jib plugin extension.

- Spring Boot 2.3.0 and later

Starting with 2.3.0, they changed the way the Spring Boot plugin includes or excludes `spring-boot-devtools` in the Spring Boot-repackaged JAR. Spring Boot now ignores the `excludeDevtools` option and requires the user to [explicitly include or exclude the dependency in the task classpath](https://docs.spring.io/spring-boot/docs/2.3.2.RELEASE/gradle-plugin/reference/html/#packaging-executable-configuring-including-development-only-dependencies). As such, the Jib extension also ignores `excludeDevtools` and only checks if `bootJar.classpath` contains `spring-boot-devtools`.

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
  pluginExtensions {
    pluginExtension {
      implementation = 'com.google.cloud.tools.jib.gradle.extension.springboot.JibSpringBootExtension'
    }
  }
}
```

- Pre-Spring Boot 2.3.0

Pre-Spring Boot 2.3.0 had been checking the `excludeDevtools` option (true by default) of Spring Boot tasks. To make the Jib extension also check the option, set the `useDeprecatedExcludeDevtoolsOption` property.

```gradle
  pluginExtensions {
    pluginExtension {
      implementation = 'com.google.cloud.tools.jib.gradle.extension.springboot.JibSpringBootExtension'
      properties = [useDeprecatedExcludeDevtoolsOption: 'true']
    }
  }
```
