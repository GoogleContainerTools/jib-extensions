# Jib GraalVM Native Image Extension

This extension containerizes a [GraalVM native-image](https://www.graalvm.org/docs/reference-manual/native-image/) application configured with [Native Image Gradle Plugin](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html).

The extension expects the `org.graalvm.buildtools.native` to do the heavy lifting of generating a "native image" (with the `nativeCompile` task). (The "image" in "native image" refers to an executable binary, not a container image.) Then the extension simply copies the binary, say, `<project root>/build/native/nativeCompile/com.example.mymainclass`, into a container image and sets executable bits. As of the moment, unlike its maven counterpart, it does `not` auto-set the container image entrypoint to the binary, say, `/app/com.example.mymainclass` so you will have to manually configure `<container><entrypoint>` in the main Jib configuration.


## Examples

Check out the [general instructions](../../README.md#using-jib-plugin-extensions) for applying a Jib plugin extension.

```gradle
// should be at the top of build.gradle
buildscript {
  dependencies {
    classpath('com.google.cloud.tools:jib-layer-filter-extension-gradle:0.3.0')
  }
}

...

jib {
  ...
  pluginExtensions {
    pluginExtension {
        implementation = 'com.google.cloud.tools.jib.gradle.extension.layerfilter.JibLayerFilterExtension'
        properties = [
            imageName: 'com.example.mymainclass'   
        ]
    }
  }
}
```

## Troubleshooting

Unlike Java bytecode, a native image is not portable but platform-specific. The Native Image Gradle Plugin doesn't support cross-compilation [See issue](https://github.com/oracle/graal/issues/407), so the native-image binary should be built on the same architecture as the runtime architecture. Otherwise, you may see a puzzling error like the following:

```
$ docker run -it --rm native-hello
standard_init_linux.go:211: exec user process caused "exec format error"
```
