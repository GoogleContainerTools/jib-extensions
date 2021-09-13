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

package com.google.cloud.tools.jib.gradle.extension.springboot;

import com.google.cloud.tools.jib.api.JavaContainerBuilder;
import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FileEntry;
import com.google.cloud.tools.jib.api.buildplan.LayerObject;
import com.google.cloud.tools.jib.gradle.extension.GradleData;
import com.google.cloud.tools.jib.gradle.extension.JibGradlePluginExtension;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger.LogLevel;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.tasks.TaskProvider;
import org.springframework.boot.gradle.tasks.bundling.BootJar;

public class JibSpringBootExtension implements JibGradlePluginExtension<Void> {

  @Override
  public Optional<Class<Void>> getExtraConfigType() {
    return Optional.empty();
  }

  @Override
  public ContainerBuildPlan extendContainerBuildPlan(
      ContainerBuildPlan buildPlan,
      Map<String, String> properties,
      Optional<Void> config,
      GradleData gradleData,
      ExtensionLogger logger)
      throws JibPluginExtensionException {
    logger.log(LogLevel.LIFECYCLE, "Running Jib Spring Boot extension");

    if (!shouldExcludeDevtools(gradleData.getProject(), properties, logger)) {
      logger.log(LogLevel.INFO, "Keeping spring-boot-devtools (if any)");
      return buildPlan;
    }
    logger.log(LogLevel.INFO, "Removing spring-boot-devtools (if any)");

    List<LayerObject> newLayers =
        buildPlan.getLayers().stream()
            .map(JibSpringBootExtension::filterOutDevtools)
            .collect(Collectors.toList());
    return buildPlan.toBuilder().setLayers(newLayers).build();
  }

  @VisibleForTesting
  static boolean isDevtoolsJar(File file) {
    return file.getName().startsWith("spring-boot-devtools-") && file.getName().endsWith(".jar");
  }

  @VisibleForTesting
  static boolean shouldExcludeDevtools(
      Project project, Map<String, String> extensionProperties, ExtensionLogger logger) {
    try {
      TaskProvider<Task> taskProvider = project.getTasks().named("bootJar");
      if (taskProvider.getOrNull() instanceof BootJar) {
        BootJar bootJar = ((BootJar) taskProvider.get());

        boolean useDeprecatedExcludeDevtools =
            Boolean.valueOf(
                extensionProperties.getOrDefault("useDeprecatedExcludeDevtoolsOption", "false"));
        if (useDeprecatedExcludeDevtools) {
          return bootJar.isExcludeDevtools();
        }

        // Since Spring 2.3.0, "excludeDevtools" is deprecated and has no effect. (Also its default
        // is no longer "true".) The new guide to include devtools is to explicitly add
        // "developmentOnly" to "bootJar.classpath".
        // https://docs.spring.io/spring-boot/docs/current/gradle-plugin/reference/html/#packaging-executable-configuring-including-development-only-dependencies
        boolean noDevtoolsJarOnClasspath =
            bootJar.getClasspath().filter(JibSpringBootExtension::isDevtoolsJar).isEmpty();
        return noDevtoolsJarOnClasspath;
      }
    } catch (UnknownTaskException ignored) { // fall through
    }

    logger.log(LogLevel.WARN, "Jib Spring Boot extension: project doesn't have bootJar task?");
    return true;
  }

  @VisibleForTesting
  static LayerObject filterOutDevtools(LayerObject layerObject) {
    String dependencyLayerName = JavaContainerBuilder.LayerType.DEPENDENCIES.getName();
    if (!dependencyLayerName.equals(layerObject.getName())) {
      return layerObject;
    }

    FileEntriesLayer layer = (FileEntriesLayer) layerObject;
    Predicate<FileEntry> notDevtoolsJar =
        fileEntry -> !isDevtoolsJar(fileEntry.getSourceFile().toFile());
    List<FileEntry> newEntries =
        layer.getEntries().stream().filter(notDevtoolsJar).collect(Collectors.toList());
    return layer.toBuilder().setEntries(newEntries).build();
  }
}
