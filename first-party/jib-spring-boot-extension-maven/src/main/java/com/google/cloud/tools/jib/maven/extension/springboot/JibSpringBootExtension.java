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

package com.google.cloud.tools.jib.maven.extension.springboot;

import com.google.cloud.tools.jib.api.JavaContainerBuilder;
import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FileEntry;
import com.google.cloud.tools.jib.api.buildplan.LayerObject;
import com.google.cloud.tools.jib.maven.extension.JibMavenPluginExtension;
import com.google.cloud.tools.jib.maven.extension.MavenData;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger.LogLevel;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

public class JibSpringBootExtension implements JibMavenPluginExtension<Void> {

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
    logger.log(LogLevel.LIFECYCLE, "Running Jib Spring Boot extension");

    List<String> exclusions = excludeDependencies(mavenData.getMavenProject(), logger);
    if (exclusions == null || exclusions.isEmpty()) {
      logger.log(LogLevel.INFO, "Keeping dependencies (if any)");
      return buildPlan;
    }
    logger.log(LogLevel.INFO, "Removing dependencies (if any)");

    List<LayerObject> newLayers =
        buildPlan.getLayers().stream()
            .map(layerObject -> filterOutDependencies(layerObject, exclusions))
            .collect(Collectors.toList());
    return buildPlan.toBuilder().setLayers(newLayers).build();
  }

  @VisibleForTesting
  static boolean isDependencyJar(File file, List<String> exclusions) {
    return exclusions.stream().anyMatch(e -> file.getName().startsWith(e)) && file.getName().endsWith(".jar");
  }

  @VisibleForTesting
  static List<String> excludeDependencies(MavenProject project, ExtensionLogger logger) {
    List<String> exclusions = new ArrayList<>();

    Plugin bootPlugin = project.getPlugin("org.springframework.boot:spring-boot-maven-plugin");
    if (bootPlugin == null) {
      logger.log(
          LogLevel.WARN,
          "Jib Spring Boot extension: project doesn't have spring-boot-maven-plugin?");
      return exclusions;
    }

    Xpp3Dom configuration = (Xpp3Dom) bootPlugin.getConfiguration();
    if (configuration != null) {
      Xpp3Dom excludeDevtools = configuration.getChild("excludeDevtools");
      if (excludeDevtools != null) {
        if ("true".equalsIgnoreCase(excludeDevtools.getValue())) {
          exclusions.add("spring-boot-devtools-");
        }
      }
      Xpp3Dom excludes = configuration.getChild("excludes");
      if (excludes != null && excludes.getChildCount() > 0) {
        for (Xpp3Dom child:excludes.getChildren()) {
          exclusions.add(String.format("%s-", child.getChild("artifactId").getValue()));
        }
      }
    }

    // no dependencies or devtools need to be excluded
    return exclusions;
  }

  @VisibleForTesting
  static LayerObject filterOutDependencies(LayerObject layerObject, List<String> exclusions) {
    // skip non-dependencies and non-project_dependencies layers
    if (!Objects.equals(JavaContainerBuilder.LayerType.DEPENDENCIES.getName(), layerObject.getName())
            && !Objects.equals(JavaContainerBuilder.LayerType.PROJECT_DEPENDENCIES.getName(), layerObject.getName())) {
      return layerObject;
    }

    FileEntriesLayer layer = (FileEntriesLayer) layerObject;
    Predicate<FileEntry> notDependencyJar =
            fileEntry -> !isDependencyJar(fileEntry.getSourceFile().toFile(), exclusions);
    List<FileEntry> newEntries =
            layer.getEntries().stream().filter(notDependencyJar).collect(Collectors.toList());
    return layer.toBuilder().setEntries(newEntries).build();
  }
}
