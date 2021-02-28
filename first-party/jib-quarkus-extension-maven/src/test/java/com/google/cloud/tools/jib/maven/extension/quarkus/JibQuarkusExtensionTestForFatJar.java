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
import static org.junit.Assert.fail;
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
import java.util.List;
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
public class JibQuarkusExtensionTestForFatJar {

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
    Path outputDir = tempFolder.newFolder("target").toPath();
    Path quarkusLibDir = Files.createDirectory(outputDir.resolve("lib"));
    Files.createFile(outputDir.resolve("my-app-runner.jar"));
    Files.createFile(quarkusLibDir.resolve("com.example.sub-module-artifact.jar"));
    Files.createFile(quarkusLibDir.resolve("com.example.third-party-artifact.jar"));
    Files.createFile(quarkusLibDir.resolve("com.example.third-party-SNAPSHOT-artifact.jar"));

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
    when(mavenBuild.getDirectory()).thenReturn(outputDir.toString());

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
  public void testExtendContainerBuildPlan_entrypoint() throws JibPluginExtensionException {
    ContainerBuildPlan buildPlan = ContainerBuildPlan.builder().build();
    ContainerBuildPlan newPlan =
        new JibQuarkusExtension()
            .extendContainerBuildPlan(buildPlan, null, Optional.empty(), mavenData, logger);

    assertEquals(
        Arrays.asList("java", "-verbose:gc", "-Dmy.property=value", "-jar", "/new/appRoot/app.jar"),
        newPlan.getEntrypoint());
  }

  @Test
  public void testExtendContainerBuildPlan_noQuarkusRunnerJar() throws IOException {
    Files.delete(tempFolder.getRoot().toPath().resolve("target").resolve("my-app-runner.jar"));
    ContainerBuildPlan buildPlan = ContainerBuildPlan.builder().build();

    try {
      new JibQuarkusExtension()
          .extendContainerBuildPlan(buildPlan, null, Optional.empty(), mavenData, logger);
      fail();
    } catch (JibPluginExtensionException ex) {
      assertEquals(JibQuarkusExtension.class, ex.getExtensionClass());
      assertThat(
          ex.getMessage(),
          endsWith(
              "/my-app-runner.jar doesn't exist; did you run the Qaurkus Maven plugin "
                  + "('compile' and 'quarkus:build' Maven goals)?"));
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
            .extendContainerBuildPlan(buildPlan, null, Optional.empty(), mavenData, logger);

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
