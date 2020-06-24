# Jib Layer-Filter Extension

A general-purpose layer-filter extension that enables fine-grained layer control, including deleting files and moving files into new layers. Note that the extension can only filters files put by Jib; it cannot filter files in the base image.

## Examples

```xml
<plugin>
  <groupId>com.google.cloud.tools</groupId>
  <artifactId>jib-maven-plugin</artifactId>
  <version>2.4.0</version>

  <dependencies>
    <dependency>
      <groupId>com.google.cloud.tools</groupId>
      <artifactId>jib-layer-filter-extension-maven</artifactId>
      <version>0.1.0</version>
    </dependency>
  </dependencies>

  <configuration>
    ...
    <pluginExtensions>
      <pluginExtension>
        <implementation>com.google.cloud.tools.jib.maven.extension.layerfilter.JibLayerFilterExtension</implementation>
        <configuration implementation="com.google.cloud.tools.jib.maven.extension.layerfilter.Configuration">
          <filters>
            <!-- Delete all jar files (unless they match the filters below). -->
            <filter>
              <glob>**/*.jar</glob>
            </filter>
            <!-- However, retain and move google-*.jar into the new "google libraries" layer. -->
            <filter>
              <glob>**/google-*.jar</glob>
              <toLayer>google libraries</toLayer>
            </filter>
            <!-- Also retain and move in-house-*.jar into the new "in-house dependencies" layer. -->
            <filter>
              <glob>/app/libs/in-house-*.jar</glob>
              <toLayer>in-house dependencies</toLayer>
            </filter>
            <!-- These go into the same "in-house dependencies" layer. -->
            <filter>
              <glob>/app/libs/other-in-house-*.jar</glob>
              <toLayer>in-house dependencies</toLayer>
            </filter>
            <filter>
              <glob>/nothing/matches/this/filter</glob>
              <toLayer>this layer will not be created</toLayer>
            </filter>
          </filters>
        </configuration>
      </pluginExtension>
    </pluginExtensions>
  </configuration>
</plugin>
```

## Detailed Filtering Rules

- If multiple filters match a file, the last filter in the order applies to the file.
- Omitting `toLayer` discards the matching files.
- You may write multiple filters moving files into the same layer. It does not create multiple layers with the same name.
- You cannot move files into Jib's built-in layers. You can only create new layers when moving files. If you see an error message "moving files into built-in layer is not supported", it means you accidentally chose a name already in use by Jib. Simply use a different `toLayer` name.
- New layers are created in the order they appear in `filters`.
- The extension does not create an empty layer when no files are matched.
