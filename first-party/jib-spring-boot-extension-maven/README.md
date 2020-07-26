# Jib Spring Boot Extension

Provides extra support for Spring Boot applications. As of now, this extension provides the following functionalities:

- Including and excluding `spring-boot-devtools`

   Provides convenience resolution for the [`spring-boot-devtools` issue](https://github.com/GoogleContainerTools/jib/issues/2336) by including/excluding [`spring-boot-devtools`](https://docs.spring.io/spring-boot/docs/current/reference/html/using-spring-boot.html#using-boot-devtools) in the same way Spring Boot includes/excludes it in their Spring Boot-repackaged fat JAR. For example, Spring Boot by default excludes `spring-boot-devtols` in their repackaged JAR, so applying this extension by default excludes it in the image too. On the other hand, if you set `<excludeDevtools>false` in the Spring Boot Maven Plugin, the extension does nothing (resulting in having the dependency in the image).

   Note that one can still properly and correctly resolve this "issue" without this extension, for example, by setting up two Maven profiles, as explained in the issue link above.

## Examples

Check out the [genenal instructions](../../README.md#using-jib-plugin-extensions) for applying a Jib plugin extension.

```xml
<plugin>
  <groupId>com.google.cloud.tools</groupId>
  <artifactId>jib-maven-plugin</artifactId>
  <version>2.4.0</version>

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
