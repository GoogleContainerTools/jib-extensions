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

package com.google.cloud.tools.jib.maven.extension.quarkus;

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.maven.extension.JibMavenPluginExtension;
import com.google.cloud.tools.jib.maven.extension.MavenData;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger.LogLevel;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Verify;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

public class JibQuarkusExtension implements JibMavenPluginExtension<Void> {

  private AbsoluteUnixPath appRoot = AbsoluteUnixPath.get("/app");
  private List<String> jvmFlags = Collections.emptyList();

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
    try {
      logger.log(LogLevel.LIFECYCLE, "Running Quarkus Jib extension");
      readJibConfigurations(mavenData.getMavenProject());

      Build build = mavenData.getMavenProject().getBuild();
      Path outputDirectory = Paths.get(build.getDirectory());
      Path jar = outputDirectory.resolve(build.getFinalName() + "-runner.jar");

      if (!Files.isRegularFile(jar)) {
        throw new JibPluginExtensionException(
            getClass(),
            jar
                + " doesn't exist; did you run the Qaurkus Maven plugin "
                + "('compile' and 'quarkus:build' Maven goals)?");
      }

      ContainerBuildPlan.Builder planBuilder = buildPlan.toBuilder();
      planBuilder.setLayers(Collections.emptyList());

      // dependency layers
      addDependencyLayers(mavenData.getMavenSession(), planBuilder, outputDirectory.resolve("lib"));

      // Quarkus runner JAR layer
      AbsoluteUnixPath appRootJar = appRoot.resolve("app.jar");
      FileEntriesLayer jarLayer =
          FileEntriesLayer.builder().setName("quarkus jar").addEntry(jar, appRootJar).build();
      planBuilder.addLayer(jarLayer);

      // Preserve extra directories layers.
      buildPlan
          .getLayers()
          .stream()
          .filter(layer -> layer.getName().startsWith("extra files"))
          .forEach(planBuilder::addLayer);

      // set entrypoint
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

  @VisibleForTesting
  void addDependencyLayers(
      MavenSession session, ContainerBuildPlan.Builder planBuilder, Path libDirectory)
      throws IOException {
    // Collect all artifact files involved in this Maven session.
    Set<String> projectArtifactFilenames =
        session
            .getProjects()
            .stream()
            .map(MavenProject::getArtifact)
            .map(Artifact::getFile)
            .filter(Objects::nonNull) // excludes root POM project
            .map(File::getName)
            .collect(Collectors.toSet());

    Predicate<Path> isProjectDependency =
        path ->
            projectArtifactFilenames
                .stream()
                // endsWith: Quarkus prepends group ID to the augmented JARs in target/lib.
                .anyMatch(jarFilename -> path.getFileName().toString().endsWith(jarFilename));
    Predicate<Path> isSnapshot =
        path ->
            path.getFileName().toString().contains("SNAPSHOT") && !isProjectDependency.test(path);
    Predicate<Path> isThirdParty = isProjectDependency.negate().and(isSnapshot.negate());

    addDependencyLayer(planBuilder, libDirectory, "dependencies", isThirdParty);
    addDependencyLayer(planBuilder, libDirectory, "snapshot dependencies", isSnapshot);
    addDependencyLayer(planBuilder, libDirectory, "project dependencies", isProjectDependency);
  }

  private void addDependencyLayer(
      ContainerBuildPlan.Builder planBuilder,
      Path libDirectory,
      String layerName,
      Predicate<Path> predicate)
      throws IOException {
    FileEntriesLayer.Builder layerBuilder = FileEntriesLayer.builder().setName(layerName);
    try (Stream<Path> files = Files.list(libDirectory)) {
      files
          .filter(predicate)
          .forEach(
              path -> {
                layerBuilder.addEntry(
                    path, appRoot.resolve("libs").resolve(path.getFileName().toString()));
              });
    }

    FileEntriesLayer layer = layerBuilder.build();
    if (!layer.getEntries().isEmpty()) {
      planBuilder.addLayer(layer);
    }
  }

  // TODO: also check system and Maven properties (e.g., -Djib.container.appRoot).
  private void readJibConfigurations(MavenProject project) {
    Plugin jibPlugin = project.getPlugin("com.google.cloud.tools:jib-maven-plugin");
    if (jibPlugin != null) {
      Xpp3Dom configurationDom = (Xpp3Dom) jibPlugin.getConfiguration();
      if (configurationDom != null) {
        Xpp3Dom containerDom = configurationDom.getChild("container");
        if (containerDom != null) {
          Xpp3Dom appRootDom = containerDom.getChild("appRoot");
          if (appRootDom != null) {
            appRoot = AbsoluteUnixPath.get(appRootDom.getValue());
          }
          Xpp3Dom jvmFlagsDom = containerDom.getChild("jvmFlags");
          if (jvmFlagsDom != null) {
            jvmFlags =
                Arrays.asList(jvmFlagsDom.getChildren())
                    .stream()
                    .map(Xpp3Dom::getValue)
                    .collect(Collectors.toList());
          }
        }
      }
    }
  }
}
