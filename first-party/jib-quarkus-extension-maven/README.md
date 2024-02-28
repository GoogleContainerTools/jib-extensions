# Jib Quarkus Extension

***Experimental***: may fail to work on complex projects. Does not cover "native" Quarkus packaging type.

Enables containerizing a Quarkus app built with [Quarkus Maven Plugin](https://search.maven.org/artifact/io.quarkus/quarkus-maven-plugin).

The Quarkus app framework has two different package types:
* legacy-jar: original Quarkus "runner" JAR
* fast-jar: default Quarkus package type since [1.12](https://quarkus.io/blog/quarkus-1-12-0-final-released/)

You can use either package type with Jib Quarkus extension by configuring the `<pluginExtensions><pluginExtension><properties>`. Default value is the legacy-jar, but can be easily changed to fast-jar.


## Examples

Check out the [general instructions](../../README.md#using-jib-plugin-extensions) for applying a Jib plugin extension.

Note that `<container><mainClass>` should be set to some placeholder value to suppress Jib warning about missing main class. Package type has two valid values: `fast-jar` and `legacy-jar`.

```xml
<plugin>
  <groupId>com.google.cloud.tools</groupId>
  <artifactId>jib-maven-plugin</artifactId>
  <version>3.4.1</version>

  <dependencies>
    <dependency>
      <groupId>com.google.cloud.tools</groupId>
      <artifactId>jib-quarkus-extension-maven</artifactId>
      <version>0.1.1</version>
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
          <properties>
              <packageType>fast-jar</packageType> <!-- to use Quarkus fast-jar package type -->
          </properties>
      </pluginExtension>
    </pluginExtensions>
  </configuration>
</plugin>
```

## Standard Jib Configurations Being Ignored

By the way Quarkus needs to run (via `java -jar quarkus-runner.jar` or `java -jar quarkus-app/quarkus-run.jar`), some standard Jib configurations will have no effect:

- `<container><mainClass>`
- `<container><entrypoint>` (Note `<container><args>` continues to work.)
- `<container><extraClasspath>`
- `<containerizingMode>`

Additionally, overriding the following configurations using Maven and Java system properties (for example, passing `-Djib.container.jvmFlags=...` on the command-line) is not yet supported.

- `<container><appRoot>`
- `<container><jvmFlags>`
