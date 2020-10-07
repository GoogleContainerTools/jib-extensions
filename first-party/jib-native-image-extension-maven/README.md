# Jib GraalVM Native Image Extension

This extension containerizes a [GraalVM native-imgae](https://www.graalvm.org/docs/reference-manual/native-image/) application configured with [Native Image Maven Plugin](https://www.graalvm.org/docs/reference-manual/native-image/#integration-with-maven).

The extension expects the `native-image-maven-plugin` to do the the heavy lifting of generating a "native image" (with the `native-image:native-image` goal). (The "image" in "native image" refers to an executable binary, not a container image.) Then the extension simply copies the binary, say, `<project root>/target/com.example.mymainclass`, into a container image and sets executable bits. It also auto-sets the container image entrypoint to the binary, say, `/app/com.example.mymainclass` (unless you manually configure `<container><entrypoint>` in the main Jib configuration).

You can still put extra files into a container image using Jib's [`<extraDirectories>` feature](https://github.com/GoogleContainerTools/jib/tree/master/jib-maven-plugin#adding-arbitrary-files-to-the-image).

## Examples

Check out the [genenal instructions](../../README.md#using-jib-plugin-extensions) for applying a Jib plugin extension.

```xml
<plugin>
  <groupId>com.google.cloud.tools</groupId>
  <artifactId>jib-maven-plugin</artifactId>
  <version>2.6.0</version>

  <dependencies>
    <dependency>
      <groupId>com.google.cloud.tools</groupId>
      <artifactId>jib-native-image-extension-maven</artifactId>
      <version>0.1.0</version>
    </dependency>
  </dependencies>

  <configuration>
    ...
    <pluginExtensions>
      <pluginExtension>
        <implementation>com.google.cloud.tools.jib.maven.extension.nativeimage.JibNativeImageExtension</implementation>
      </pluginExtension>
    </pluginExtensions>
  </configuration>
</plugin>
```

If for some reason the extension fails to auto-detect the native-image binary name, you can manually set the `<imageName>` property.
```xml
      <pluginExtension>
        <implementation>com.google.cloud.tools.jib.maven.extension.nativeimage.JibNativeImageExtension</implementation>
        <properties>
          <!-- Normally you won't need to set this. Let us know if the extension
               fails to auto-detect the binary name. -->
          <imageName>my-binary-name</imageName>
        </properties>
      </pluginExtension>
```

## Troubleshooting

Unlike Java bytecode, a native image is not portable but platform-specific. The Native Image Maven Plugin doesn't support cross-compilation, so the native-image binary should be built on the same architecture as the runtime architecture. Otherwise, you may see a puzzling error like the following:

```
$ docker run -it --rm native-hello
standard_init_linux.go:211: exec user process caused "exec format error"
```
