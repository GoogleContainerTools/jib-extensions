# Jib Layer-Filter Extension

A general-purpose layer-filter extension that enables fine-grained layer control, including deleting files and moving files into new layers. Note that the extension can only filter files put by Jib; it cannot filter files in the base image.

## Examples

Check out the [genenal instructions](../../README.md#using-jib-plugin-extensions) for applying a Jib plugin extension.

```xml
<plugin>
  <groupId>com.google.cloud.tools</groupId>
  <artifactId>jib-maven-plugin</artifactId>
  <version>3.4.3</version>

  <dependencies>
    <dependency>
      <groupId>com.google.cloud.tools</groupId>
      <artifactId>jib-layer-filter-extension-maven</artifactId>
      <version>0.3.0</version>
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
          <!-- To create separate layers for parent dependencies-->
          <createParentDependencyLayers>true</createParentDependencyLayers>
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
- You cannot move files into existing layers. You can only create new layers when moving files. If you see an error message "moving files into existing layer '...' is prohibited", it means you accidentally chose a conflicting name. Simply use a different `toLayer` name.
- New layers are created in the order they appear in `filters`.
- The extension does not create an empty layer when no files are matched.

## Separate Layers for Parent Dependencies

Setting `createParentDependencyLayers` to `true` will move all dependencies that come from the parent POM to separate layers with layer name suffixed by `-parent`.

- This runs after the filtering. Hence, it also considers each `toLayer` that has been created.
- The extension will never create an empty parent dependency layer.
- If a layer contains only parent dependencies, it will be removed, since all its content will be moved to its corresponding parent dependency layer. 
