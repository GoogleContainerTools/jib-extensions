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

package com.google.cloud.tools.jib.gradle.extension.quarkus;

import com.google.cloud.tools.jib.api.JavaContainerBuilder;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.gradle.JibExtension;
import com.google.cloud.tools.jib.gradle.extension.GradleData;
import com.google.cloud.tools.jib.gradle.extension.JibGradlePluginExtension;
import com.google.cloud.tools.jib.gradle.extension.quarkus.Configuration.PackageType;
import com.google.cloud.tools.jib.gradle.extension.quarkus.resolvers.JarResolver;
import com.google.cloud.tools.jib.gradle.extension.quarkus.resolvers.JarResolverFactory;
import com.google.cloud.tools.jib.gradle.extension.quarkus.resolvers.impl.LegacyJarResolver;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger.LogLevel;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import com.google.common.base.Strings;
import com.google.common.base.Verify;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.plugins.JavaPlugin;

public class JibQuarkusExtension implements JibGradlePluginExtension<Configuration> {

  private AbsoluteUnixPath appRoot = AbsoluteUnixPath.get("/app");
  private List<String> jvmFlags = Collections.emptyList();

  private JarResolver jarResolver = new LegacyJarResolver();
  private PackageType packageType = PackageType.LEGACY;

  @Override
  public Optional<Class<Configuration>> getExtraConfigType() {
    return Optional.of(Configuration.class);
  }

  @Override
  public ContainerBuildPlan extendContainerBuildPlan(
      ContainerBuildPlan buildPlan,
      Map<String, String> properties,
      Optional<Configuration> config,
      GradleData gradleData,
      ExtensionLogger logger)
      throws JibPluginExtensionException {
    try {
      logger.log(LogLevel.LIFECYCLE, "Running Quarkus Jib extension");
      if (!config.isPresent()) {
        logger.log(
            LogLevel.WARN,
            "Packaging type not configured for Jib Quarkus Gradle Extension. Using default: legacy");
      } else {
        packageType = config.get().getPackageType();
      }

      readJibConfigurations(gradleData.getProject());

      Project project = gradleData.getProject();
      Path jar = jarResolver.getPathToLocalJar(project);

      ContainerBuildPlan.Builder planBuilder = buildPlan.toBuilder();
      planBuilder.setLayers(Collections.emptyList());

      // Dependency layers
      for (Path path : jarResolver.getPathsToDependencies(project)) {
        addDependencyLayers(project, planBuilder, path);
      }

      // Quarkus JAR layer
      AbsoluteUnixPath appRootJar = jarResolver.getPathToJarInContainer(appRoot);

      FileEntriesLayer jarLayer =
          FileEntriesLayer.builder().setName("quarkus jar").addEntry(jar, appRootJar).build();
      planBuilder.addLayer(jarLayer);

      // Preserve extra directories layers
      String extraFilesLayerName = JavaContainerBuilder.LayerType.EXTRA_FILES.getName();
      buildPlan.getLayers().stream()
          .filter(layer -> layer.getName().startsWith(extraFilesLayerName))
          .forEach(planBuilder::addLayer);

      // Set entrypoint
      List<String> entrypoint = new ArrayList<>();
      entrypoint.add("java");
      entrypoint.addAll(jvmFlags);
      entrypoint.add("-jar");
      entrypoint.add(appRootJar.toString());
      planBuilder.setEntrypoint(entrypoint);

      return planBuilder.build();

    } catch (IOException ex) {
      throw new JibPluginExtensionException(getClass(), Verify.verifyNotNull(ex.getMessage()), ex);
    }
  }

  private void addDependencyLayers(
      Project project, ContainerBuildPlan.Builder planBuilder, Path libDirectory)
      throws IOException {
    List<String> projectDependencyFilenames =
        project.getConfigurations().getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
            .getResolvedConfiguration().getResolvedArtifacts().stream()
            .filter(
                artifact ->
                    artifact.getId().getComponentIdentifier() instanceof ProjectComponentIdentifier)
            .map(ResolvedArtifact::getFile)
            .map(File::getName)
            .collect(Collectors.toList());

    Predicate<Path> isProjectDependency =
        path ->
            projectDependencyFilenames.stream()
                // endsWith: Quarkus prepends group ID to the augmented JARs in target/lib.
                .anyMatch(jarFilename -> path.getFileName().toString().endsWith(jarFilename));
    Predicate<Path> isSnapshot =
        path ->
            path.getFileName().toString().contains("SNAPSHOT") && !isProjectDependency.test(path);
    Predicate<Path> isThirdParty = isProjectDependency.negate().and(isSnapshot.negate());

    addDependencyLayer(project, planBuilder, libDirectory, "dependencies", isThirdParty);
    addDependencyLayer(project, planBuilder, libDirectory, "snapshot dependencies", isSnapshot);
    addDependencyLayer(
        project, planBuilder, libDirectory, "project dependencies", isProjectDependency);
  }

  private void addDependencyLayer(
      Project project,
      ContainerBuildPlan.Builder planBuilder,
      Path libDirectory,
      String layerName,
      Predicate<Path> predicate)
      throws IOException {
    FileEntriesLayer.Builder layerBuilder = FileEntriesLayer.builder().setName(layerName);

    String relativePathInLib =
        project.getBuildDir().toURI().relativize(libDirectory.toUri()).toString();

    try (Stream<Path> files = Files.list(libDirectory)) {
      files
          .filter(predicate)
          .forEach(
              path -> {
                layerBuilder.addEntry(
                    path,
                    appRoot.resolve(relativePathInLib).resolve(path.getFileName().toString()));
              });
    }

    FileEntriesLayer layer = layerBuilder.build();
    if (!layer.getEntries().isEmpty()) {
      planBuilder.addLayer(layer);
    }
  }

  private void readJibConfigurations(Project project) {
    JibExtension jibPlugin = project.getExtensions().findByType(JibExtension.class);
    String appRootValue = jibPlugin.getContainer().getAppRoot();
    if (!Strings.isNullOrEmpty(appRootValue)) {
      appRoot = AbsoluteUnixPath.get(appRootValue);
    }
    jvmFlags = jibPlugin.getContainer().getJvmFlags();
    jarResolver = new JarResolverFactory().getJarResolver(packageType);
  }
}
