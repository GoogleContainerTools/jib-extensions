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

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.maven.extension.MavenData;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
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

  @Mock private MavenData mavenData;
  @Mock private ExtensionLogger logger;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private MavenSession mavenSession;

  @Mock private MavenProject pomProject;
  @Mock private MavenProject subModule;
  @Mock private MavenProject jibModule;
  @Mock private Artifact nonFileArtifact;
  @Mock private Artifact subModuleArtifact;
  @Mock private Artifact jibModuleArtifact;
  @Mock private Build mavenBuild;
  @Mock private Plugin jibPlugin;

  private final Map<String, String> properties = new HashMap<>();

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
    when(mavenBuild.getDirectory()).thenReturn("");

    when(mavenData.getMavenProject()).thenReturn(jibModule);
    when(mavenData.getMavenSession()).thenReturn(mavenSession);

    when(mavenSession.getProjects()).thenReturn(Arrays.asList(pomProject, subModule, jibModule));
    when(pomProject.getArtifact()).thenReturn(nonFileArtifact);
    when(subModule.getArtifact()).thenReturn(subModuleArtifact);
    when(jibModule.getArtifact()).thenReturn(jibModuleArtifact);

    when(subModuleArtifact.getFile()).thenReturn(new File("sub-module-artifact.jar"));
    when(jibModuleArtifact.getFile()).thenReturn(new File("jib-module-artifact.jar"));

    when(jibModule.getBuild()).thenReturn(mavenBuild);
    when(mavenBuild.getFinalName()).thenReturn("my-app");

    Xpp3Dom jibConfigurationDom = new Xpp3Dom("configuration");
    Xpp3Dom containerDom = new Xpp3Dom("container");
    Xpp3Dom appRootDom = new Xpp3Dom("appRoot");
    Xpp3Dom jvmFlagsDom = new Xpp3Dom("jvmFlags");
    Xpp3Dom jvmFlag1Dom = new Xpp3Dom("flag");
    Xpp3Dom jvmFlag2Dom = new Xpp3Dom("flag");

    appRootDom.setValue("/new/appRoot");
    jvmFlag1Dom.setValue("-verbose:gc");
    jvmFlag2Dom.setValue("-Dmy.property=value");

    jibConfigurationDom.addChild(containerDom);
    containerDom.addChild(appRootDom);
    containerDom.addChild(jvmFlagsDom);
    jvmFlagsDom.addChild(jvmFlag1Dom);
    jvmFlagsDom.addChild(jvmFlag2Dom);

    when(jibModule.getPlugin("com.google.cloud.tools:jib-maven-plugin")).thenReturn(jibPlugin);
    when(jibPlugin.getConfiguration()).thenReturn(jibConfigurationDom);
  }

  @Test
  public void testExtendContainerBuildPlan_entrypoint()
      throws JibPluginExtensionException, IOException {
    createLegacyJar();
    ContainerBuildPlan buildPlan = ContainerBuildPlan.builder().build();
    ContainerBuildPlan newPlan =
        new JibQuarkusExtension()
            .extendContainerBuildPlan(buildPlan, properties, Optional.empty(), mavenData, logger);

    assertEquals(
        Arrays.asList("java", "-verbose:gc", "-Dmy.property=value", "-jar", "/new/appRoot/app.jar"),
        newPlan.getEntrypoint());
  }

  @Test
  public void testExtendContainerBuildPlan_noQuarkusRunnerJar() throws IOException {
    createLegacyJar();
    Files.delete(tempFolder.getRoot().toPath().resolve("target").resolve("my-app-runner.jar"));
    ContainerBuildPlan buildPlan = ContainerBuildPlan.builder().build();

    JibPluginExtensionException ex =
        assertThrows(
            JibPluginExtensionException.class,
            () ->
                new JibQuarkusExtension()
                    .extendContainerBuildPlan(
                        buildPlan, properties, Optional.empty(), mavenData, logger));

    assertEquals(JibQuarkusExtension.class, ex.getExtensionClass());
    assertThat(
        ex.getMessage(),
        endsWith(
            "my-app-runner.jar doesn't exist; did you run the Quarkus Maven plugin "
                + "('compile' and 'quarkus:build' Maven goals)?"));
  }

  @Test
  public void testExtendContainerBuildPlan_packageTypeConfig()
      throws IOException, JibPluginExtensionException {
    createFastJar();
    properties.put("packageType", "fast-jar");
    ContainerBuildPlan buildPlan = ContainerBuildPlan.builder().build();

    ContainerBuildPlan newPlan =
        new JibQuarkusExtension()
            .extendContainerBuildPlan(buildPlan, properties, Optional.empty(), mavenData, logger);
    assertEquals(7, newPlan.getLayers().size());
    FileEntriesLayer lastLayer = (FileEntriesLayer) newPlan.getLayers().get(6);
    assertEquals(
        Collections.singletonList("/new/appRoot/quarkus-app/quarkus-run.jar"),
        layerToExtractionPaths(lastLayer));
  }

  @Test
  public void testExtendContainerBuildPlan_noQuarkusFastJar() throws IOException {
    ContainerBuildPlan buildPlan = ContainerBuildPlan.builder().build();
    properties.put("packageType", "fast-jar");

    JibPluginExtensionException ex =
        assertThrows(
            JibPluginExtensionException.class,
            () ->
                new JibQuarkusExtension()
                    .extendContainerBuildPlan(
                        buildPlan, properties, Optional.empty(), mavenData, logger));

    assertEquals(JibQuarkusExtension.class, ex.getExtensionClass());
    assertThat(
        ex.getMessage(),
        endsWith(
            "quarkus-app/quarkus-run.jar doesn't exist; did you run the Quarkus Maven plugin "
                + "('compile' and 'quarkus:build' Maven goals)?"));
  }

  @Test
  public void testExtendContainerBuildPlan_LegacyJarLayers()
      throws JibPluginExtensionException, IOException {
    createLegacyJar();
    FileEntriesLayer originalLayer = buildLayer("to be reset", Arrays.asList("/ignored"));
    FileEntriesLayer extraLayer1 = buildLayer("extra files", Arrays.asList("/extra/files/1"));
    FileEntriesLayer extraLayer2 = buildLayer("extra files", Arrays.asList("/extra/files/2"));
    ContainerBuildPlan buildPlan =
        ContainerBuildPlan.builder()
            .setLayers(Arrays.asList(originalLayer, extraLayer1, extraLayer2))
            .build();

    ContainerBuildPlan newPlan =
        new JibQuarkusExtension()
            .extendContainerBuildPlan(buildPlan, properties, Optional.empty(), mavenData, logger);

    assertEquals(6, newPlan.getLayers().size());
    FileEntriesLayer dependencies = (FileEntriesLayer) newPlan.getLayers().get(0);
    FileEntriesLayer snapshotDependencies = (FileEntriesLayer) newPlan.getLayers().get(1);
    FileEntriesLayer projectDependencies = (FileEntriesLayer) newPlan.getLayers().get(2);
    FileEntriesLayer quarkusJar = (FileEntriesLayer) newPlan.getLayers().get(3);
    FileEntriesLayer extraFiles = (FileEntriesLayer) newPlan.getLayers().get(4);
    FileEntriesLayer extraFiles2 = (FileEntriesLayer) newPlan.getLayers().get(5);

    assertEquals("dependencies", dependencies.getName());
    assertEquals("snapshot dependencies", snapshotDependencies.getName());
    assertEquals("project dependencies", projectDependencies.getName());
    assertEquals("quarkus jar", quarkusJar.getName());
    assertEquals("extra files", extraFiles.getName());
    assertEquals("extra files", extraFiles2.getName());

    assertEquals(
        Arrays.asList("/new/appRoot/lib/com.example.third-party-artifact.jar"),
        layerToExtractionPaths(dependencies));
    assertEquals(
        Arrays.asList("/new/appRoot/lib/com.example.third-party-SNAPSHOT-artifact.jar"),
        layerToExtractionPaths(snapshotDependencies));
    assertEquals(
        Arrays.asList("/new/appRoot/lib/com.example.sub-module-artifact.jar"),
        layerToExtractionPaths(projectDependencies));
    assertEquals(Arrays.asList("/new/appRoot/app.jar"), layerToExtractionPaths(quarkusJar));
    assertEquals(extraLayer1.getEntries(), extraFiles.getEntries());
    assertEquals(extraLayer2.getEntries(), extraFiles2.getEntries());
  }

  @Test
  public void testExtendContainerBuildPlan_FastJarLayers()
      throws JibPluginExtensionException, IOException {
    createFastJar();
    FileEntriesLayer originalLayer = buildLayer("to be reset", Arrays.asList("/ignored"));
    FileEntriesLayer extraLayer1 = buildLayer("extra files", Arrays.asList("/extra/files/1"));
    FileEntriesLayer extraLayer2 = buildLayer("extra files", Arrays.asList("/extra/files/2"));
    ContainerBuildPlan buildPlan =
        ContainerBuildPlan.builder()
            .setLayers(Arrays.asList(originalLayer, extraLayer1, extraLayer2))
            .build();
    properties.put("packageType", "fast-jar");

    ContainerBuildPlan newPlan =
        new JibQuarkusExtension()
            .extendContainerBuildPlan(buildPlan, properties, Optional.empty(), mavenData, logger);

    assertEquals(9, newPlan.getLayers().size());
    FileEntriesLayer dependencies = (FileEntriesLayer) newPlan.getLayers().get(0);
    FileEntriesLayer snapshotDependencies = (FileEntriesLayer) newPlan.getLayers().get(1);
    FileEntriesLayer projectDependencies = (FileEntriesLayer) newPlan.getLayers().get(2);
    FileEntriesLayer dependencies2 = (FileEntriesLayer) newPlan.getLayers().get(3);
    FileEntriesLayer snapshotDependencies2 = (FileEntriesLayer) newPlan.getLayers().get(4);
    FileEntriesLayer dependencies3 = (FileEntriesLayer) newPlan.getLayers().get(5);
    FileEntriesLayer quarkusJar = (FileEntriesLayer) newPlan.getLayers().get(6);
    FileEntriesLayer extraFiles = (FileEntriesLayer) newPlan.getLayers().get(7);
    FileEntriesLayer extraFiles2 = (FileEntriesLayer) newPlan.getLayers().get(8);

    assertEquals("dependencies", dependencies.getName());
    assertEquals("snapshot dependencies", snapshotDependencies.getName());
    assertEquals("project dependencies", projectDependencies.getName());
    assertEquals("dependencies", dependencies2.getName());
    assertEquals("snapshot dependencies", snapshotDependencies2.getName());
    assertEquals("dependencies", dependencies3.getName());
    assertEquals("quarkus jar", quarkusJar.getName());
    assertEquals("extra files", extraFiles.getName());
    assertEquals("extra files", extraFiles2.getName());

    assertEquals(
        Collections.singletonList(
            "/new/appRoot/quarkus-app/lib/main/com.example.third-party-artifact.jar"),
        layerToExtractionPaths(dependencies));
    assertEquals(
        Collections.singletonList(
            "/new/appRoot/quarkus-app/lib/main/com.example.third-party-SNAPSHOT-artifact.jar"),
        layerToExtractionPaths(snapshotDependencies));
    assertEquals(
        Collections.singletonList(
            "/new/appRoot/quarkus-app/lib/main/com.example.sub-module-artifact.jar"),
        layerToExtractionPaths(projectDependencies));
    assertEquals(
        Collections.singletonList(
            "/new/appRoot/quarkus-app/lib/boot/io.quarkus.quarkus-bootstrap-runner.jar"),
        layerToExtractionPaths(dependencies2));
    assertEquals(
        Collections.singletonList("/new/appRoot/quarkus-app/app/my-app-runner-SNAPSHOT.jar"),
        layerToExtractionPaths(snapshotDependencies2));
    assertEquals(
        Collections.singletonList("/new/appRoot/quarkus-app/quarkus/generated-bytecode.jar"),
        layerToExtractionPaths(dependencies3));
    assertEquals(
        Collections.singletonList("/new/appRoot/quarkus-app/quarkus-run.jar"),
        layerToExtractionPaths(quarkusJar));
    assertEquals(extraLayer1.getEntries(), extraFiles.getEntries());
    assertEquals(extraLayer2.getEntries(), extraFiles2.getEntries());
  }

  private void createLegacyJar() throws IOException {
    Path buildDir = tempFolder.newFolder("target").toPath();
    Path quarkusLibDir = Files.createDirectory(buildDir.resolve("lib"));
    Files.createFile(buildDir.resolve("my-app-runner.jar"));
    Files.createFile(quarkusLibDir.resolve("com.example.sub-module-artifact.jar"));
    Files.createFile(quarkusLibDir.resolve("com.example.third-party-artifact.jar"));
    Files.createFile(quarkusLibDir.resolve("com.example.third-party-SNAPSHOT-artifact.jar"));

    when(mavenData.getMavenProject().getBuild().getDirectory()).thenReturn(buildDir.toString());
    when(mavenBuild.getDirectory()).thenReturn(buildDir.toString());
  }

  private void createFastJar() throws IOException {
    Path buildDir = tempFolder.newFolder("target").toPath();
    Path quarkusAppDir = Files.createDirectory(buildDir.resolve("quarkus-app"));
    Path quarkusLibDir = Files.createDirectory(quarkusAppDir.resolve("lib"));
    Path quarkusLibBootDir = Files.createDirectory(quarkusLibDir.resolve("boot"));
    Path quarkusLibMainDir = Files.createDirectory(quarkusLibDir.resolve("main"));
    Path quarkusAppLibDir = Files.createDirectory(quarkusAppDir.resolve("app"));
    Path quarkusQuarkusDir = Files.createDirectory(quarkusAppDir.resolve("quarkus"));

    Files.createFile(quarkusAppDir.resolve("quarkus-run.jar"));
    Files.createFile(quarkusLibBootDir.resolve("io.quarkus.quarkus-bootstrap-runner.jar"));
    Files.createFile(quarkusLibMainDir.resolve("com.example.sub-module-artifact.jar"));
    Files.createFile(quarkusLibMainDir.resolve("com.example.third-party-artifact.jar"));
    Files.createFile(quarkusLibMainDir.resolve("com.example.third-party-SNAPSHOT-artifact.jar"));
    Files.createFile(quarkusAppLibDir.resolve("my-app-runner-SNAPSHOT.jar"));
    Files.createFile(quarkusQuarkusDir.resolve("generated-bytecode.jar"));

    when(mavenData.getMavenProject().getBuild().getDirectory()).thenReturn(buildDir.toString());
    when(mavenBuild.getDirectory()).thenReturn(buildDir.toString());
  }
}
