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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.LayerObject;
import com.google.cloud.tools.jib.maven.extension.MavenData;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger.LogLevel;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link JibSpringBootExtension}. */
@RunWith(MockitoJUnitRunner.class)
public class JibSpringBootExtensionTest {

  @Mock private MavenData mavenData;
  @Mock private ExtensionLogger logger;

  @Mock private MavenProject project;
  @Mock private Plugin bootPlugin;

  private static FileEntriesLayer buildLayer(String layerName, Path... paths) {
    FileEntriesLayer.Builder builder = FileEntriesLayer.builder().setName(layerName);
    for (Path path : paths) {
      builder.addEntry(path, AbsoluteUnixPath.get("/dest/" + path.getFileName()));
    }
    return builder.build();
  }

  private static List<String> layerToExtractionPaths(FileEntriesLayer layer) {
    return layer
        .getEntries()
        .stream()
        .map(layerEntry -> layerEntry.getExtractionPath().toString())
        .collect(Collectors.toList());
  }

  @Before
  public void setUp() {
    when(mavenData.getMavenProject()).thenReturn(project);
  }

  @Test
  public void testIsDevtoolsJar() {
    File file = Paths.get("sub", "folder", "spring-boot-devtools-1.2.3-SNAPSHOT.jar").toFile();
    assertTrue(JibSpringBootExtension.isDevtoolsJar(file));
  }

  @Test
  public void testIsDevtoolsJar_noJarExtension() {
    File file = Paths.get("sub", "folder", "spring-boot-devtools-1.2.3-SNAPSHOT").toFile();
    assertFalse(JibSpringBootExtension.isDevtoolsJar(file));
  }

  @Test
  public void testIsDevtoolsJar_differentJar() {
    File file = Paths.get("sub", "folder", "not-spring-boot-devtools-1.2.3-SNAPSHOT.jar").toFile();
    assertFalse(JibSpringBootExtension.isDevtoolsJar(file));
  }

  @Test
  public void testShouldExcludeDevtools_noSpringBootPlugin() {
    assertTrue(JibSpringBootExtension.shouldExcludeDevtools(project, logger));

    verify(logger)
        .log(
            LogLevel.WARN,
            "Jib Spring Boot extension: project doesn't have spring-boot-maven-plugin?");
  }

  @Test
  public void testShouldExcludeDevtools_trueByDefault() {
    when(project.getPlugin("org.springframework.boot:spring-boot-maven-plugin"))
        .thenReturn(bootPlugin);

    assertTrue(JibSpringBootExtension.shouldExcludeDevtools(project, logger));
  }

  @Test
  public void testShouldExcludeDevtools_false() {
    Xpp3Dom configuration = new Xpp3Dom("configuration");
    Xpp3Dom excludeDevtools = new Xpp3Dom("excludeDevtools");
    excludeDevtools.setValue("FaLsE");
    configuration.addChild(excludeDevtools);

    when(project.getPlugin("org.springframework.boot:spring-boot-maven-plugin"))
        .thenReturn(bootPlugin);
    when(bootPlugin.getConfiguration()).thenReturn(configuration);

    assertFalse(JibSpringBootExtension.shouldExcludeDevtools(project, logger));
  }

  @Test
  public void testShouldExcludeDevtools_true() {
    Xpp3Dom configuration = new Xpp3Dom("configuration");
    Xpp3Dom excludeDevtools = new Xpp3Dom("excludeDevtools");
    excludeDevtools.setValue("tRuE");
    configuration.addChild(excludeDevtools);

    when(project.getPlugin("org.springframework.boot:spring-boot-maven-plugin"))
        .thenReturn(bootPlugin);
    when(bootPlugin.getConfiguration()).thenReturn(configuration);

    assertTrue(JibSpringBootExtension.shouldExcludeDevtools(project, logger));
  }

  @Test
  public void testFilterOutDevtools() {
    FileEntriesLayer layer =
        buildLayer(
            "dependencies",
            Paths.get("static").resolve("foo.txt"),
            Paths.get("lib").resolve("spring-boot-devtools-1.2.3.jar"),
            Paths.get("archive").resolve("bar.zip"));
    FileEntriesLayer filtered = (FileEntriesLayer) JibSpringBootExtension.filterOutDevtools(layer);

    assertEquals(Arrays.asList("/dest/foo.txt", "/dest/bar.zip"), layerToExtractionPaths(filtered));
  }

  @Test
  public void testFilterOutDevtools_differentDependencyLayerName() {
    FileEntriesLayer layer =
        buildLayer(
            "NOT dependencies",
            Paths.get("lib").resolve("spring-boot-devtools-1.2.3.jar"),
            Paths.get("archive").resolve("bar.zip"));
    LayerObject newLayer = JibSpringBootExtension.filterOutDevtools(layer);
    assertSame(layer, newLayer);
    assertEquals(layer.getEntries(), ((FileEntriesLayer) newLayer).getEntries());
  }

  @Test
  public void testExtendContainerBuildPlan_devtoolsFiltered() throws JibPluginExtensionException {
    when(project.getPlugin("org.springframework.boot:spring-boot-maven-plugin"))
        .thenReturn(bootPlugin);

    FileEntriesLayer layer1 =
        buildLayer(
            "dependencies",
            Paths.get("spring-boot-devtools-1.2.3.jar"),
            Paths.get("archive").resolve("bar.zip"));
    FileEntriesLayer layer2 =
        buildLayer("NOT dependencies", Paths.get("spring-boot-devtools-1.2.3.jar"));
    ContainerBuildPlan buildPlan =
        ContainerBuildPlan.builder().addLayer(layer1).addLayer(layer2).build();

    ContainerBuildPlan newPlan =
        new JibSpringBootExtension()
            .extendContainerBuildPlan(buildPlan, null, Optional.empty(), mavenData, logger);

    assertEquals(2, newPlan.getLayers().size());
    FileEntriesLayer newLayer1 = (FileEntriesLayer) newPlan.getLayers().get(0);
    FileEntriesLayer newLayer2 = (FileEntriesLayer) newPlan.getLayers().get(1);

    assertEquals(Arrays.asList("/dest/bar.zip"), layerToExtractionPaths(newLayer1));
    assertEquals(
        Arrays.asList("/dest/spring-boot-devtools-1.2.3.jar"), layerToExtractionPaths(newLayer2));

    verify(logger).log(LogLevel.INFO, "Removing spring-boot-devtools (if any)");
  }

  @Test
  public void testExtendContainerBuildPlan_noFiltering() throws JibPluginExtensionException {
    // set up <excludeDevtools>false (no filtering required)
    Xpp3Dom configuration = new Xpp3Dom("configuration");
    Xpp3Dom excludeDevtools = new Xpp3Dom("excludeDevtools");
    excludeDevtools.setValue("false");
    configuration.addChild(excludeDevtools);

    when(project.getPlugin("org.springframework.boot:spring-boot-maven-plugin"))
        .thenReturn(bootPlugin);
    when(bootPlugin.getConfiguration()).thenReturn(configuration);

    FileEntriesLayer layer1 =
        buildLayer(
            "dependencies",
            Paths.get("spring-boot-devtools-1.2.3.jar"),
            Paths.get("archive").resolve("bar.zip"));
    FileEntriesLayer layer2 =
        buildLayer("NOT dependencies", Paths.get("spring-boot-devtools-1.2.3.jar"));
    ContainerBuildPlan buildPlan =
        ContainerBuildPlan.builder().addLayer(layer1).addLayer(layer2).build();

    ContainerBuildPlan newPlan =
        new JibSpringBootExtension()
            .extendContainerBuildPlan(buildPlan, null, Optional.empty(), mavenData, logger);
    assertSame(buildPlan, newPlan);

    verify(logger).log(LogLevel.INFO, "Keeping spring-boot-devtools (if any)");
  }
}
