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

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.gradle.JibExtension;
import com.google.cloud.tools.jib.gradle.extension.GradleData;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.jvm.tasks.Jar;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link JibQuarkusExtension}. */
@RunWith(MockitoJUnitRunner.class)
public class JibQuarkusExtensionTest {

  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  @Mock private ExtensionLogger logger;

  @Mock private TaskContainer taskContainer;
  @Mock private ExtensionContainer extensionContainer;
  @Mock private ResolvedArtifact thirdPartyArtifact1;
  @Mock private ResolvedArtifact thirdPartyArtifact2;
  @Mock private ResolvedArtifact subModuleArtifact;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private Project project;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private Jar jarTask;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private JibExtension jibPlugin;

  private GradleData gradleData = () -> project;

  private static FileEntriesLayer buildLayer(String layerName, List<String> filePaths) {
    FileEntriesLayer.Builder builder = FileEntriesLayer.builder().setName(layerName);
    for (String path : filePaths) {
      builder.addEntry(Paths.get("whatever"), AbsoluteUnixPath.get(path));
    }
    return builder.build();
  }

  private static List<String> layerToExtractionPaths(FileEntriesLayer layer) {
    return layer.getEntries().stream()
        .map(layerEntry -> layerEntry.getExtractionPath().toString())
        .collect(Collectors.toList());
  }

  @Before
  public void setUp() throws IOException {
    Path buildDir = tempFolder.newFolder("build").toPath();
    Path quarkusLibDir = Files.createDirectory(buildDir.resolve("lib"));
    Files.createFile(buildDir.resolve("my-app-runner.jar"));
    Files.createFile(quarkusLibDir.resolve("com.example.sub-module-artifact.jar"));
    Files.createFile(quarkusLibDir.resolve("com.example.third-party-artifact.jar"));
    Files.createFile(quarkusLibDir.resolve("com.example.third-party-SNAPSHOT-artifact.jar"));

    when(project.getBuildDir()).thenReturn(buildDir.toFile());
    when(project.getTasks()).thenReturn(taskContainer);
    when(project.getExtensions()).thenReturn(extensionContainer);

    when(taskContainer.findByName("jar")).thenReturn(jarTask);
    when(jarTask.getArchiveFile().get().getAsFile().getName()).thenReturn("my-app.jar");

    when(extensionContainer.findByType(JibExtension.class)).thenReturn(jibPlugin);
    when(jibPlugin.getContainer().getAppRoot()).thenReturn("/new/appRoot");
    when(jibPlugin.getContainer().getJvmFlags())
        .thenReturn(Arrays.asList("-verbose:gc", "-Dmy.property=value"));

    when(project
            .getConfigurations()
            .getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
            .getResolvedConfiguration()
            .getResolvedArtifacts())
        .thenReturn(Sets.newHashSet(thirdPartyArtifact1, subModuleArtifact, thirdPartyArtifact2));

    when(thirdPartyArtifact1.getId()).thenReturn(new MockComponentArtifactIdentifier(null));
    when(thirdPartyArtifact2.getId()).thenReturn(new MockComponentArtifactIdentifier(null));
    when(subModuleArtifact.getId())
        .thenReturn(new MockComponentArtifactIdentifier(new MockProjectComponentIdentifier()));
    when(subModuleArtifact.getFile()).thenReturn(tempFolder.newFile("sub-module-artifact.jar"));
  }

  @Test
  public void testExtendContainerBuildPlan_entrypoint() throws JibPluginExtensionException {
    ContainerBuildPlan buildPlan = ContainerBuildPlan.builder().build();
    ContainerBuildPlan newPlan =
        new JibQuarkusExtension()
            .extendContainerBuildPlan(buildPlan, null, Optional.empty(), gradleData, logger);

    assertEquals(
        Arrays.asList("java", "-verbose:gc", "-Dmy.property=value", "-jar", "/new/appRoot/app.jar"),
        newPlan.getEntrypoint());
  }

  @Test
  public void testExtendContainerBuildPlan_emptyAppRoot() throws JibPluginExtensionException {
    when(jibPlugin.getContainer().getAppRoot()).thenReturn("");

    ContainerBuildPlan buildPlan = ContainerBuildPlan.builder().build();
    ContainerBuildPlan newPlan =
        new JibQuarkusExtension()
            .extendContainerBuildPlan(buildPlan, null, Optional.empty(), gradleData, logger);

    assertEquals(
        Arrays.asList("java", "-verbose:gc", "-Dmy.property=value", "-jar", "/app/app.jar"),
        newPlan.getEntrypoint());
  }

  @Test
  public void testExtendContainerBuildPlan_noQuarkusRunnerJar() throws IOException {
    Files.delete(tempFolder.getRoot().toPath().resolve("build").resolve("my-app-runner.jar"));
    ContainerBuildPlan buildPlan = ContainerBuildPlan.builder().build();

    try {
      new JibQuarkusExtension()
          .extendContainerBuildPlan(buildPlan, null, Optional.empty(), gradleData, logger);
      fail();
    } catch (JibPluginExtensionException ex) {
      assertEquals(JibQuarkusExtension.class, ex.getExtensionClass());
      assertThat(
          ex.getMessage(),
          endsWith(
              File.separator
                  + "my-app-runner.jar doesn't exist; did you run the Quarkus Gradle plugin "
                  + "('quarkusBuild' task)?"));
    }
  }

  @Test
  public void testExtendContainerBuildPlan_layers() throws JibPluginExtensionException {
    FileEntriesLayer originalLayer = buildLayer("to be reset", Arrays.asList("/ignored"));
    FileEntriesLayer extraLayer1 = buildLayer("extra files", Arrays.asList("/extra/files/1"));
    FileEntriesLayer extraLayer2 = buildLayer("extra files", Arrays.asList("/extra/files/2"));
    ContainerBuildPlan buildPlan =
        ContainerBuildPlan.builder()
            .setLayers(Arrays.asList(originalLayer, extraLayer1, extraLayer2))
            .build();

    ContainerBuildPlan newPlan =
        new JibQuarkusExtension()
            .extendContainerBuildPlan(buildPlan, null, Optional.empty(), gradleData, logger);

    assertEquals(6, newPlan.getLayers().size());
    FileEntriesLayer layer1 = (FileEntriesLayer) newPlan.getLayers().get(0);
    FileEntriesLayer layer2 = (FileEntriesLayer) newPlan.getLayers().get(1);
    FileEntriesLayer layer3 = (FileEntriesLayer) newPlan.getLayers().get(2);
    FileEntriesLayer layer4 = (FileEntriesLayer) newPlan.getLayers().get(3);
    FileEntriesLayer layer5 = (FileEntriesLayer) newPlan.getLayers().get(4);
    FileEntriesLayer layer6 = (FileEntriesLayer) newPlan.getLayers().get(5);

    assertEquals("dependencies", layer1.getName());
    assertEquals("snapshot dependencies", layer2.getName());
    assertEquals("project dependencies", layer3.getName());
    assertEquals("quarkus jar", layer4.getName());
    assertEquals("extra files", layer5.getName());
    assertEquals("extra files", layer6.getName());

    assertEquals(
        Arrays.asList("/new/appRoot/lib/com.example.third-party-artifact.jar"),
        layerToExtractionPaths(layer1));
    assertEquals(
        Arrays.asList("/new/appRoot/lib/com.example.third-party-SNAPSHOT-artifact.jar"),
        layerToExtractionPaths(layer2));
    assertEquals(
        Arrays.asList("/new/appRoot/lib/com.example.sub-module-artifact.jar"),
        layerToExtractionPaths(layer3));
    assertEquals(Arrays.asList("/new/appRoot/app.jar"), layerToExtractionPaths(layer4));
    assertEquals(extraLayer1.getEntries(), layer5.getEntries());
    assertEquals(extraLayer2.getEntries(), layer6.getEntries());
  }
}
