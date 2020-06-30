# Jib Spring Boot Extension

Spring Boot

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
    <container>
      <!-- to suppress Jib warning about missing main class -->
      <mainClass>bogus</mainClass>
      ...
      <jvmFlags>
        <flag>-Dquarkus.http.host=0.0.0.0</flag>
        <flag>-Djava.util.logging.manager=org.jboss.logmanager.LogManager</flag>
      </jvmFlags>
      <ports>
        <port>8080</port>
      </ports>
      <user>1001</user>
    </container>
    ...
    <pluginExtensions>
      <pluginExtension>
        <implementation>com.google.cloud.tools.jib.maven.extension.springboot.JibSpringBootExtension</implementation>
      </pluginExtension>
    </pluginExtensions>
  </configuration>
</plugin>
```
