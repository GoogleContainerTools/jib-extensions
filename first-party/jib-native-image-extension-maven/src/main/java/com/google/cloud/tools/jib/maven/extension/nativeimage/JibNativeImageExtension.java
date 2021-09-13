/*
 * Copyright 2020 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.tools.jib.maven.extension.nativeimage;

import com.google.cloud.tools.jib.api.JavaContainerBuilder;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import com.google.cloud.tools.jib.maven.extension.JibMavenPluginExtension;
import com.google.cloud.tools.jib.maven.extension.MavenData;
import com.google.cloud.tools.jib.maven.extension.nativeimage.ConfigValueLocation.ValueContainer;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger.LogLevel;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

public class JibNativeImageExtension implements JibMavenPluginExtension<Void> {

  private static final ConfigValueLocation JIB_APP_ROOT =
      new ConfigValueLocation(
          "com.google.cloud.tools:jib-maven-plugin",
          ValueContainer.CONFIGURATION,
          new String[] {"container", "appRoot"});

  private static final ConfigValueLocation JIB_ENTRYPOINT =
      new ConfigValueLocation(
          "com.google.cloud.tools:jib-maven-plugin",
          ValueContainer.CONFIGURATION,
          new String[] {"container", "entrypoint"});

  private static final String MAIN_CLASS = "mainClass";

  // If <imageName> is not specified, then native-image uses the mainClass name as the executable.
  // If <mainClass> is not specified, then native-image-maven-plugin looks at the configurations
  // for a set of other well-known plugins.
  // https://github.com/oracle/graal/blob/master/substratevm/src/native-image-maven-plugin/src/main/java/com/oracle/substratevm/NativeImageMojo.java
  private static final ConfigValueLocation NATIVE_IMAGE_PLUGIN_IMAGE_NAME =
      new ConfigValueLocation(
          "org.graalvm.nativeimage:native-image-maven-plugin",
          ValueContainer.CONFIGURATION,
          new String[] {"imageName"});

  private static final ImmutableList<ConfigValueLocation> MAIN_CLASS_LOCATIONS =
      ImmutableList.of(
          new ConfigValueLocation(
              "org.graalvm.nativeimage:native-image-maven-plugin",
              ValueContainer.CONFIGURATION,
              new String[] {MAIN_CLASS}),
          new ConfigValueLocation(
              "org.apache.maven.plugins:maven-shade-plugin",
              ValueContainer.EXECUTIONS,
              new String[] {"transformers", "transformer", MAIN_CLASS}),
          new ConfigValueLocation(
              "org.apache.maven.plugins:maven-assembly-plugin",
              ValueContainer.CONFIGURATION,
              new String[] {"archive", "manifest", MAIN_CLASS}),
          new ConfigValueLocation(
              "org.apache.maven.plugins:maven-jar-plugin",
              ValueContainer.CONFIGURATION,
              new String[] {"archive", "manifest", MAIN_CLASS}));

  @Override
  public Optional<Class<Void>> getExtraConfigType() {
    return Optional.empty();
  }

  @Override
  public ContainerBuildPlan extendContainerBuildPlan(
      ContainerBuildPlan buildPlan,
      Map<String, String> properties,
      Optional<Void> config,
      MavenData mavenData,
      ExtensionLogger logger)
      throws JibPluginExtensionException {
    logger.log(LogLevel.LIFECYCLE, "Running Jib Native Image extension");

    MavenProject project = mavenData.getMavenProject();
    Optional<String> executableName = getExecutableName(project, properties);
    if (!executableName.isPresent()) {
      throw new JibPluginExtensionException(
          getClass(),
          "cannot auto-detect native-image executable name; consider setting 'imageName' property");
    }

    String outputDirectory = project.getBuild().getDirectory();
    Path localExecutable = Paths.get(outputDirectory, executableName.get());
    if (!Files.isRegularFile(localExecutable)) {
      throw new JibPluginExtensionException(
          getClass(),
          "Native-image executable does not exist or not a file: "
              + localExecutable
              + "\nDid you run the 'native-image:native-image' goal?");
    }

    // TODO: also check system and Maven properties (e.g., -Djib.container.appRoot).
    String appRoot = getPluginConfigValue(project, JIB_APP_ROOT).orElse("/app");
    AbsoluteUnixPath targetExecutable = AbsoluteUnixPath.get(appRoot).resolve(executableName.get());

    ContainerBuildPlan.Builder planBuilder = buildPlan.toBuilder();
    FileEntriesLayer nativeImageLayer =
        FileEntriesLayer.builder()
            .setName("native image")
            .addEntry(localExecutable, targetExecutable, FilePermissions.fromOctalString("755"))
            .build();
    planBuilder.setLayers(Collections.singletonList(nativeImageLayer));

    // Preserve extra directories layers.
    String extraFilesLayerName = JavaContainerBuilder.LayerType.EXTRA_FILES.getName();
    buildPlan.getLayers().stream()
        .filter(layer -> layer.getName().startsWith(extraFilesLayerName))
        .forEach(planBuilder::addLayer);

    // TODO: also check system and Maven properties (e.g., -Djib.container.entrypoint).
    if (!getPluginConfigValue(project, JIB_ENTRYPOINT).isPresent()) {
      planBuilder.setEntrypoint(Collections.singletonList(targetExecutable.toString()));
    }
    return planBuilder.build();
  }

  @VisibleForTesting
  static Optional<String> getExecutableName(MavenProject project, Map<String, String> properties) {
    String customName = properties.get("imageName");
    if (!Strings.isNullOrEmpty(customName)) {
      return Optional.of(customName);
    }

    Optional<String> imageName = getPluginConfigValue(project, NATIVE_IMAGE_PLUGIN_IMAGE_NAME);
    if (imageName.isPresent()) {
      return imageName;
    }

    return MAIN_CLASS_LOCATIONS.stream()
        .map(location -> getPluginConfigValue(project, location))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .map(name -> name.toLowerCase(Locale.US))
        .findFirst();
  }

  private static Optional<String> getPluginConfigValue(
      MavenProject project, ConfigValueLocation location) {
    Plugin plugin = project.getPlugin(location.pluginId);
    if (plugin == null) {
      return Optional.empty();
    }

    switch (location.valueContainer) {
      case CONFIGURATION:
        return getDomValue((Xpp3Dom) plugin.getConfiguration(), location.domPath);

      case EXECUTIONS:
        return plugin.getExecutions().stream()
            .map(execution -> getDomValue((Xpp3Dom) execution.getConfiguration(), location.domPath))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst();

      default:
        throw new IllegalArgumentException("unknown enum value: " + location.valueContainer);
    }
  }

  @VisibleForTesting
  static Optional<String> getDomValue(@Nullable Xpp3Dom dom, String... nodePath) {
    if (dom == null) {
      return Optional.empty();
    }

    Xpp3Dom node = dom;
    for (String child : nodePath) {
      node = node.getChild(child);
      if (node == null) {
        return Optional.empty();
      }
    }
    return Optional.ofNullable(node.getValue());
  }
}
