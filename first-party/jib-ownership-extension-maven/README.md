# Jib Onwership Extension

_A word of caution: use of this extension for production images is against the container best practices, which 1) increases security risks; and 2) may result in improper tracking and/or lifecycle management of ephemeral files dynamically generated at runtime (for example, log files or temp files). This is the main reason that the Jib plugins do not have built-in support for changing file ownership. However, there are a few legitimate use-cases to change ownership for non-proudction images such as when using [Skaffold](https://skaffold.dev/) to dynamically update files on Kubernetes during development. For more details, see the discussions in [jib#1257](https://github.com/GoogleContainerTools/jib/issues/1257)._

This extension enables changing ownership (not to be confused with file and directory permissions) of files and directories in the container image. Note the extension can only set ownership on files and directories that are put by Jib.

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
      <artifactId>jib-ownership-extension-maven</artifactId>
      <version>0.1.0</version>
    </dependency>
  </dependencies>

  <configuration>
    ...
    <pluginExtensions>
      <pluginExtension>
        <implementation>com.google.cloud.tools.jib.maven.extension.ownership.JibOwnershipExtension</implementation>
        <configuration implementation="com.google.cloud.tools.jib.maven.extension.ownership.Configuration">
          <rules>
            <rule>
              <glob>/app/classes/**</glob>
              <ownership>300</ownership>
            </rule>
            <rule>
              <glob>/static/**</glob>
              <ownership>300:500</ownership>
            </rule>
          </rules>
        </configuration>
      </pluginExtension>
    </pluginExtensions>
  </configuration>
</plugin>
```

## Known Issues

#### Unable to change ownership of some parent directories.

When the Jib plugin assemables files into an image layer tarball, it automatically creates [supplemental parent directory entries](https://github.com/GoogleContainerTools/jib/issues/1270) for each file. The extension cannot change the ownership of these directories. For a workaround, you can create empty directories under `<project root>/src/main/jib/` (that is, utilizing the [`<extraDirectories>` feature](ihttps://github.com/GoogleContainerTools/jib/tree/master/jib-gradle-plugin#adding-arbitrary-files-to-the-image)) to explicitly list the directories, after which the extension can see and change the onwership of such directories. For example, if you create an empty directory structure with `<project root>/src/main/jib/app/classes/`, you can change the ownership of `/app` and `/app/classes`.
