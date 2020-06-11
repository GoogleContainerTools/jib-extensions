# Jib Layer-Filter Extension

A general-purpose layer-filter extension that enables fine-grained layer control, including deleting files and moving files into new layers. Note that the extension can only filters files put by Jib; it cannot filter files in the base image.

## Examples

```gradle
// should be at the top of build.gradle
buildscript {
  dependencies {
    classpath('com.google.cloud.tools:jib-layer-filter-extension-gradle:0.1.0')
  }
}

...

jib {
  ...
  pluginExtensions {
    pluginExtension {
      implementation = 'com.google.cloud.tools.jib.gradle.extension.layerfilter.JibLayerFilterExtension'
      configuration {
        filters {
          // Delete all jar files.
          filter {
            glob = '**/*.jar'
          }
          // However, retain and move google-*.jar into the new "google libraries" layer.
          filter {
            glob = '**/google-*.jar'
            toLayer = 'google libraries'
          }
          // Also retain and move in-house-*.jar into the new "in-house dependencies" layer.
          filter {
            glob = '/app/libs/in-house-*.jar'
            toLayer = 'in-house dependencies'
          }
          // These go into the same "in-house dependencies" layer.
          filter {
            glob = '/app/libs/other-in-house-*.jar'
            toLayer = 'in-house dependencies'
          }
          filter {
            glob = '/nothing/matches/this/filter'
            toLayer = 'this layer will not be created'
          }
        }
      }
    }
  }
}
```

## Detailed Filtering Rules

- If multiple filters match a file, the last filter in the order applies to the file.
- Omitting `toLayer` discards the matching files.
- You may write multiple filters moving files into the same layer. It does not create multiple layers with the same name.
- You cannot move files into Jib's built-in layers. You can only create new layers when moving files. If you see an error message "moving files into built-in layer is not supported", it means you accidentally chose a name already in use by Jib. Simply use a different `toLayer` name.
- New layers are created in the order they appear in `filters`.
- The extension does not create an empty layer when no files are matched.
