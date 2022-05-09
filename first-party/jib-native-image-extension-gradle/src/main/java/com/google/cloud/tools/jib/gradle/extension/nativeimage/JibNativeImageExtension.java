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

package com.google.cloud.tools.jib.gradle.extension.nativeimage;

import com.google.cloud.tools.jib.api.JavaContainerBuilder;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import com.google.cloud.tools.jib.gradle.extension.GradleData;
import com.google.cloud.tools.jib.gradle.extension.JibGradlePluginExtension;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger.LogLevel;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.gradle.api.Project;
import org.gradle.internal.impldep.com.google.common.annotations.VisibleForTesting;

public class JibNativeImageExtension implements JibGradlePluginExtension<Void> {

  private static final String MAIN_CLASS = "mainClass";
  /*
  If <imageName> is not specified, then native-image uses the mainClass name as the executable.
  If <mainClass> is not specified, then native-image-maven-plugin looks at the configurations
  for a set of other well-known plugins.
   */


  @VisibleForTesting
  static Optional<String> getExecutableName(Map<String, String> properties) {
    String customName = properties.get("imageName");
    return Optional.ofNullable(customName);
  }

  @Override
  public Optional<Class<Void>> getExtraConfigType() {
    return Optional.empty();
  }

  @Override
  public ContainerBuildPlan extendContainerBuildPlan(ContainerBuildPlan buildPlan,
      Map<String, String> properties, Optional<Void> config, GradleData gradleData,
      ExtensionLogger logger) throws JibPluginExtensionException {
    logger.log(LogLevel.LIFECYCLE, "Running Jib Native Image extension");

    Project project = gradleData.getProject();

    Optional<String> executableName = getExecutableName(properties);
    if (!executableName.isPresent()) {
      throw new JibPluginExtensionException(getClass(),
          "cannot auto-detect native-image executable name; consider setting 'imageName' property");
    }

    String outputDirectory = project.getBuildDir().getAbsolutePath();
    Path localExecutable = Paths.get(outputDirectory, "native/nativeCompile", executableName.get());
    logger.log(LogLevel.LIFECYCLE, "Local: " + localExecutable);
    if (!Files.isRegularFile(localExecutable)) {
      throw new JibPluginExtensionException(getClass(),
          "Native-image executable does not exist or not a file: " + localExecutable
              + "\nDid you run the 'native-image:native-image' goal?");
    }

    // TODO: also check system and Maven properties (e.g., -Djib.container.appRoot).
    String appRoot = "/app";
    AbsoluteUnixPath targetExecutable = AbsoluteUnixPath.get(appRoot).resolve(executableName.get());
    ContainerBuildPlan.Builder planBuilder = buildPlan.toBuilder();
    FileEntriesLayer nativeImageLayer = FileEntriesLayer.builder().setName("native image")
        .addEntry(localExecutable, targetExecutable, FilePermissions.fromOctalString("755"))
        .build();
    planBuilder.setLayers(Collections.singletonList(nativeImageLayer));

    // Preserve extra directories layers.
    String extraFilesLayerName = JavaContainerBuilder.LayerType.EXTRA_FILES.getName();
    buildPlan.getLayers().stream().filter(layer -> layer.getName().startsWith(extraFilesLayerName))
        .forEach(planBuilder::addLayer);

    // TODO: also check system and Maven properties (e.g., -Djib.container.entrypoint).
    planBuilder.setEntrypoint(Collections.singletonList(targetExecutable.toString()));
    return planBuilder.build();
  }
}
