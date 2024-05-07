# Jib Spring Boot Extension

Provides extra support for Spring Boot applications. As of now, this extension provides the following functionalities:

- Including and excluding `spring-boot-devtools`

   Handles the [Spring Boot developer tools issue](https://github.com/GoogleContainerTools/jib/issues/2336) that Jib always (correctly) packages the [`spring-boot-devtools`](https://docs.spring.io/spring-boot/docs/current/reference/html/using-spring-boot.html#using-boot-devtools) dependency. Applying this extension makes Jib include or exclude the dependency in the same way Spring Boot does for their repackaged fat JAR. For example, Spring Boot by default excludes `spring-boot-devtools` from the repackaged JAR, so the extension by default excludes it from an image too. On the other hand, if you set `<excludeDevtools>false` in Spring Boot, the extension does nothing (keeps the dependency in the image).

   Note that one can still properly and correctly resolve this "issue" without this extension, for example, by setting up two Maven profiles, as explained in the issue link above.

## Examples

Check out the [genenal instructions](../../README.md#using-jib-plugin-extensions) for applying a Jib plugin extension.

```xml
<plugin>
  <groupId>com.google.cloud.tools</groupId>
  <artifactId>jib-maven-plugin</artifactId>
  <version>3.4.2</version>

  <dependencies>
    <dependency>
      <groupId>com.google.cloud.tools</groupId>
      <artifactId>jib-spring-boot-extension-maven</artifactId>
      <version>0.1.0</version>
    </dependency>
  </dependencies>

  <configuration>
    ...
    <pluginExtensions>
      <pluginExtension>
        <implementation>com.google.cloud.tools.jib.maven.extension.springboot.JibSpringBootExtension</implementation>
      </pluginExtension>
    </pluginExtensions>
  </configuration>
</plugin>
```
