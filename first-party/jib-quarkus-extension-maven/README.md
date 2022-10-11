# Jib Quarkus Extension

***Experimental***: may fail to work on complex projects. Does not cover "native" Quarkus packaging type.

Enables containerizing a Quarkus app built with [Quarkus Maven Plugin](https://search.maven.org/artifact/io.quarkus/quarkus-maven-plugin).

The Quarkus app framework prepares a special "runner" JAR and augments dependency JARs, where the standard Jib containerization does not fit. This extension takes the JARs prepared by Quarkus (runner JAR and augmented dependency JARs) and sets the entrypoint to run the runner JAR.

## Examples

Check out the [general instructions](../../README.md#using-jib-plugin-extensions) for applying a Jib plugin extension.

Note that `<container><mainClass>` should be set to some placeholder value to suppress Jib warning about missing main class.

```xml
<plugin>
  <groupId>com.google.cloud.tools</groupId>
  <artifactId>jib-maven-plugin</artifactId>
  <version>3.3.0</version>

  <dependencies>
    <dependency>
      <groupId>com.google.cloud.tools</groupId>
      <artifactId>jib-quarkus-extension-maven</artifactId>
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
        <implementation>com.google.cloud.tools.jib.maven.extension.quarkus.JibQuarkusExtension</implementation>
      </pluginExtension>
    </pluginExtensions>
  </configuration>
</plugin>
```

## Standard Jib Configurations Being Ignored

By the way Quarkus needs to run (via `java -jar quarkus-runner.jar`), some standard Jib configurations will have no effect:

- `<container><mainClass>`
- `<container><entrypoint>` (Note `<container><args>` continues to work.)
- `<container><extraClasspath>`
- `<containerizingMode>`

Additionally, overriding the following configurations using Maven and Java system properties (for example, passing `-Djib.container.jvmFlags=...` on the command-line) is not yet supported.

- `<container><appRoot>`
- `<container><jvmFlags>`
