# Jib Onwership Extension

_A word of caution: use of this extension for production images is against the container best practices, which 1) increases security risks; and 2) may result in improper tracking and/or lifecycle management of ephemeral files dynamically generated at runtime (for example, as log files or temp files). This is the main reason that the Jib plugins do not have built-in support for changing file ownership. However, there is a few legitimate use-cases to change ownership for non-proudction images such as when using [Skaffold](https://skaffold.dev/) to dynamically update files on Kubernetes during development. For more details, see the discussions in [jib#1257](https://github.com/GoogleContainerTools/jib/issues/1257)._

This extension enables changing ownership (not to be confused with file and directory permissions) of files and directories in the container image. Note the extension can only set ownership on files and directories that are put by Jib.

## Examples

```gradle
pluginExtension {
  implementation = 'com.google.cloud.tools.jib.gradle.extension.ownership.JibOwnershipExtension'
  configuration {
    rules {
      rule {
        glob = '/app/classes/**'
        ownership = '300'
      }
      rule {
        glob = '/static/**'
        ownership = '300:500'
      }
    }
  }
}
```

## Known Issues

#### Unable to change ownership of some parent directories.

When the Jib plugin assemables files into an image layer tarball, it automatically creates [supplemental parent directory entries](https://github.com/GoogleContainerTools/jib/issues/1270) for each file. The extension cannot change the ownership of these directories. For a workaround, you can create empty directories under `<project root>/src/main/jib/` (that is, utilizing the [`jib.extraDirectories` feature](ihttps://github.com/GoogleContainerTools/jib/tree/master/jib-gradle-plugin#adding-arbitrary-files-to-the-image)) to explicitly list the directories, after which the extension can see and change the onwership of such directories. For example, if you create an empty directory structure with `<project root>/src/main/jib/app/classes/`, you can change the ownership of `/app` and `/app/classes`.
