# Third-Party Extensions

This page serves as a central portal for Jib plugin extensions developed by the Jib community. Note that this page merely provides links to third-party extensions, and the Jib team does not give any kind of endorsement on these extensions.

If you have written a useful extension that you think will benefit the Jib community, file a PR to add a link to the list. Jib users will greatly appreciate it!

Interested in writing an extension? It's easy! Take a look at ["Writing Your Own Extensions"](../README.md#writing-your-own-extensions).

- AWS Lambda (+ general file path customizer) ([Maven](https://github.com/jdimeo/jib-extension-aws-lambda)): an extension to change file paths to conform to the convention that AWS Java Lambda expects. However, the extension is general and can customize any files that Jib puts. 
- Layer With Modification Time ([Maven](https://github.com/infobip/jib-layer-with-modification-time-extension-maven)): an extension for selectively setting file timestamps to build time (eg. for hosted web resources)
- OSGi Bundle Packaging Plugin Extension ([Maven](https://github.com/thought-gang/jib-maven-plugin-extension.git)): an extension to containerize an OSGI bundle (Maven packaging type `bundle`)
- Javaagent Attachment Plugin Extension ([Gradle](https://github.com/ryandens/javaagent-gradle-plugin#jib-integration)): An extension that allows you to automatically add a javaagent from a Maven repository as a layer in your container image built by Jib and modifies the entrypoint of the image to include the `-javaagent` flag.
- JVM Flags Extension ([Maven](https://github.com/softleader/jib-jvm-flags-extension-maven)): An extension that outputs the configured `jvmFlags` into the `/app/jib-jvm-flags-file` file. This allows a custom entrypoint to access these flags, such as using a shell script to launch the app.

- to be added
- ... 
- ...
