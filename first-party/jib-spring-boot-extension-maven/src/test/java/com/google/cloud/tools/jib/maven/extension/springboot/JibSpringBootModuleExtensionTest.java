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

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.LayerObject;
import com.google.cloud.tools.jib.maven.extension.MavenData;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger.LogLevel;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests for {@link JibSpringBootModuleExtension}. */
@RunWith(MockitoJUnitRunner.class)
public class JibSpringBootModuleExtensionTest {

  @Mock private MavenData mavenData;
  @Mock private ExtensionLogger logger;

  @Mock private MavenProject project;
  @Mock private Plugin bootPlugin;

  private final JibSpringBootModuleExtension extension = new JibSpringBootModuleExtension() {
    @Override
    public String getModuleName() {
      return "spring-boot-module";
    }

    @Override
    public String getExcludePropertyName() {
      return "excludeModule";
    }
  };

  private static FileEntriesLayer buildLayer(String layerName, Path... paths) {
    FileEntriesLayer.Builder builder = FileEntriesLayer.builder().setName(layerName);
    for (Path path : paths) {
      builder.addEntry(path, AbsoluteUnixPath.get("/dest/" + path.getFileName()));
    }
    return builder.build();
  }

  private static List<String> layerToExtractionPaths(FileEntriesLayer layer) {
    return layer.getEntries().stream()
        .map(layerEntry -> layerEntry.getExtractionPath().toString())
        .collect(Collectors.toList());
  }

  @Before
  public void setUp() {
    when(mavenData.getMavenProject()).thenReturn(project);
  }

  @Test
  public void testIsModuleJar() {
    File file = Paths.get("sub", "folder", "spring-boot-module-1.2.3-SNAPSHOT.jar").toFile();
    assertTrue(extension.isModuleJar(file));
  }

  @Test
  public void testIsModuleJar_noJarExtension() {
    File file = Paths.get("sub", "folder", "spring-boot-module-1.2.3-SNAPSHOT").toFile();
    assertFalse(extension.isModuleJar(file));
  }

  @Test
  public void testIsModuleJar_differentJar() {
    File file = Paths.get("sub", "folder", "not-spring-boot-module-1.2.3-SNAPSHOT.jar").toFile();
    assertFalse(extension.isModuleJar(file));
  }

  @Test
  public void testShouldExcludeModule_noSpringBootPlugin() {
    assertTrue(extension.shouldExcludeModule(project, logger));

    verify(logger)
        .log(
            LogLevel.WARN,
            "Jib Spring Boot extension: project doesn't have spring-boot-maven-plugin?");
  }

  @Test
  public void testShouldExcludeModule_trueByDefault() {
    when(project.getPlugin("org.springframework.boot:spring-boot-maven-plugin"))
        .thenReturn(bootPlugin);

    assertTrue(extension.shouldExcludeModule(project, logger));
  }

  @Test
  public void testShouldExcludeMOdule_false() {
    Xpp3Dom configuration = new Xpp3Dom("configuration");
    Xpp3Dom excludeModule = new Xpp3Dom("excludeModule");
    excludeModule.setValue("FaLsE");
    configuration.addChild(excludeModule);

    when(project.getPlugin("org.springframework.boot:spring-boot-maven-plugin"))
        .thenReturn(bootPlugin);
    when(bootPlugin.getConfiguration()).thenReturn(configuration);

    assertFalse(extension.shouldExcludeModule(project, logger));
  }

  @Test
  public void testShouldExcludeModule_true() {
    Xpp3Dom configuration = new Xpp3Dom("configuration");
    Xpp3Dom excludeModule = new Xpp3Dom("excludeModule");
    excludeModule.setValue("tRuE");
    configuration.addChild(excludeModule);

    when(project.getPlugin("org.springframework.boot:spring-boot-maven-plugin"))
        .thenReturn(bootPlugin);
    when(bootPlugin.getConfiguration()).thenReturn(configuration);

    assertTrue(extension.shouldExcludeModule(project, logger));
  }

  @Test
  public void testFilterOutModule() {
    FileEntriesLayer layer =
        buildLayer(
            "dependencies",
            Paths.get("static").resolve("foo.txt"),
            Paths.get("lib").resolve("spring-boot-module-1.2.3.jar"),
            Paths.get("archive").resolve("bar.zip"));
    FileEntriesLayer filtered = (FileEntriesLayer) extension.filterOutModule(layer);

    assertEquals(Arrays.asList("/dest/foo.txt", "/dest/bar.zip"), layerToExtractionPaths(filtered));
  }

  @Test
  public void testFilterOutModule_differentDependencyLayerName() {
    FileEntriesLayer layer =
        buildLayer(
            "NOT dependencies",
            Paths.get("lib").resolve("spring-boot-module-1.2.3.jar"),
            Paths.get("archive").resolve("bar.zip"));
    LayerObject newLayer = extension.filterOutModule(layer);
    assertSame(layer, newLayer);
    assertEquals(layer.getEntries(), ((FileEntriesLayer) newLayer).getEntries());
  }

  @Test
  public void testExtendContainerBuildPlan_moduleFiltered() {
    when(project.getPlugin("org.springframework.boot:spring-boot-maven-plugin"))
        .thenReturn(bootPlugin);

    FileEntriesLayer layer1 =
        buildLayer(
            "dependencies",
            Paths.get("spring-boot-module-1.2.3.jar"),
            Paths.get("archive").resolve("bar.zip"));
    FileEntriesLayer layer2 =
        buildLayer("NOT dependencies", Paths.get("spring-boot-module-1.2.3.jar"));
    ContainerBuildPlan buildPlan =
        ContainerBuildPlan.builder().addLayer(layer1).addLayer(layer2).build();

    ContainerBuildPlan newPlan =
        extension.extendContainerBuildPlan(buildPlan, null, Optional.empty(), mavenData, logger);

    assertEquals(2, newPlan.getLayers().size());
    FileEntriesLayer newLayer1 = (FileEntriesLayer) newPlan.getLayers().get(0);
    FileEntriesLayer newLayer2 = (FileEntriesLayer) newPlan.getLayers().get(1);

    assertEquals(Arrays.asList("/dest/bar.zip"), layerToExtractionPaths(newLayer1));
    assertEquals(
        Arrays.asList("/dest/spring-boot-module-1.2.3.jar"), layerToExtractionPaths(newLayer2));

    verify(logger).log(LogLevel.INFO, "Removing spring-boot-module (if any)");
  }

  @Test
  public void testExtendContainerBuildPlan_noFiltering() {
    // set up <excludeModule>false (no filtering required)
    Xpp3Dom configuration = new Xpp3Dom("configuration");
    Xpp3Dom excludeModule = new Xpp3Dom("excludeModule");
    excludeModule.setValue("false");
    configuration.addChild(excludeModule);

    when(project.getPlugin("org.springframework.boot:spring-boot-maven-plugin"))
        .thenReturn(bootPlugin);
    when(bootPlugin.getConfiguration()).thenReturn(configuration);

    FileEntriesLayer layer1 =
        buildLayer(
            "dependencies",
            Paths.get("spring-boot-module-1.2.3.jar"),
            Paths.get("archive").resolve("bar.zip"));
    FileEntriesLayer layer2 =
        buildLayer("NOT dependencies", Paths.get("spring-boot-module-1.2.3.jar"));
    ContainerBuildPlan buildPlan =
        ContainerBuildPlan.builder().addLayer(layer1).addLayer(layer2).build();

    ContainerBuildPlan newPlan =
        extension.extendContainerBuildPlan(buildPlan, null, Optional.empty(), mavenData, logger);
    assertSame(buildPlan, newPlan);

    verify(logger).log(LogLevel.INFO, "Keeping spring-boot-module (if any)");
  }
}
