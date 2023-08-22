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
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public interface JibSpringBootModuleExtension extends JibMavenPluginExtension<Void> {

  @Override
  default Optional<Class<Void>> getExtraConfigType() {
    return Optional.empty();
  }

  String getModuleName();
  String getExcludePropertyName();

  @Override
  default ContainerBuildPlan extendContainerBuildPlan(
      ContainerBuildPlan buildPlan,
      Map<String, String> properties,
      Optional<Void> config,
      MavenData mavenData,
      ExtensionLogger logger) {
    logger.log(LogLevel.LIFECYCLE, "Running Jib Spring Boot extension for module " + getModuleName());

    if (!shouldExcludeModule(mavenData.getMavenProject(), logger)) {
      logger.log(LogLevel.INFO, "Keeping " + getModuleName() + " (if any)");
      return buildPlan;
    }
    logger.log(LogLevel.INFO, "Removing " + getModuleName() + " (if any)");

    List<LayerObject> newLayers =
        buildPlan.getLayers().stream()
            .map(this::filterOutModule)
            .collect(Collectors.toList());
    return buildPlan.toBuilder().setLayers(newLayers).build();
  }

  default boolean shouldExcludeModule(MavenProject project, ExtensionLogger logger) {
    Plugin bootPlugin = project.getPlugin("org.springframework.boot:spring-boot-maven-plugin");
    if (bootPlugin == null) {
      logger.log(
              LogLevel.WARN,
              "Jib Spring Boot extension: project doesn't have spring-boot-maven-plugin?");
      return true;
    }

    Xpp3Dom configuration = (Xpp3Dom) bootPlugin.getConfiguration();
    if (configuration != null) {
      Xpp3Dom excludeDevtools = configuration.getChild(getExcludePropertyName());
      if (excludeDevtools != null) {
        return "true".equalsIgnoreCase(excludeDevtools.getValue());
      }
    }
    return true; // Spring Boot's exclude property default is true.
  }

  default LayerObject filterOutModule(LayerObject layerObject) {
    String dependencyLayerName = JavaContainerBuilder.LayerType.DEPENDENCIES.getName();
    if (!dependencyLayerName.equals(layerObject.getName())) {
      return layerObject;
    }

    FileEntriesLayer layer = (FileEntriesLayer) layerObject;
    Predicate<FileEntry> notDevtoolsJar =
        fileEntry -> !isModuleJar(fileEntry.getSourceFile().toFile());
    List<FileEntry> newEntries =
        layer.getEntries().stream().filter(notDevtoolsJar).collect(Collectors.toList());
    return layer.toBuilder().setEntries(newEntries).build();
  }

  default boolean isModuleJar(File file) {
    return file.getName().startsWith(getModuleName() + "-") && file.getName().endsWith(".jar");
  }
}
