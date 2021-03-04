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

package com.google.cloud.tools.jib.maven.extension.layerfilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link JibLayerFilterExtension}. */
@RunWith(MockitoJUnitRunner.class)
public class JibLayerFilterExtensionTest {

  @Mock private Configuration config;
  @Mock private ExtensionLogger logger;
  @Mock private MavenData mavenData;
  @Mock private MavenProject mavenProject;
  @Mock private MavenProject mavenParentProject;
  @Mock private MavenSession mavenSession;
  @Mock private ProjectDependenciesResolver projectDependenciesResolver;
  @Mock private DependencyResolutionResult dependencyResolutionResult;

  @Before
  public void setUp() throws DependencyResolutionException {
    when(config.getFilters()).thenReturn(Collections.emptyList());
    when(mavenData.getMavenProject()).thenReturn(mavenProject);
    when(mavenData.getMavenSession()).thenReturn(mavenSession);
    when(mavenProject.getParent()).thenReturn(mavenParentProject);
    when(projectDependenciesResolver.resolve(any(DependencyResolutionRequest.class)))
        .thenReturn(dependencyResolutionResult);
  }

  private static FileEntriesLayer buildLayer(String layerName, List<String> filePaths) {
    FileEntriesLayer.Builder builder = FileEntriesLayer.builder().setName(layerName);
    for (String path : filePaths) {
      builder.addEntry(Paths.get("whatever"), AbsoluteUnixPath.get(path));
    }
    return builder.build();
  }

  private static FileEntriesLayer buildLayer(
      String layerName, List<String> sourcePaths, List<String> inContainerPaths) {
    FileEntriesLayer.Builder builder = FileEntriesLayer.builder().setName(layerName);
    for (int i = 0; i < sourcePaths.size(); i++) {
      builder.addEntry(
          Paths.get(sourcePaths.get(i)), AbsoluteUnixPath.get(inContainerPaths.get(i)));
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

  private static Configuration.Filter mockFilter(String glob, String toLayer) {
    Configuration.Filter filter = mock(Configuration.Filter.class);
    when(filter.getGlob()).thenReturn(glob);
    when(filter.getToLayer()).thenReturn(toLayer);
    return filter;
  }

  private static Dependency mockDependency(String sourcePath, String artifactId) {
    Artifact artifact = mock(Artifact.class);
    when(artifact.getArtifactId()).thenReturn(artifactId);
    when(artifact.getFile()).thenReturn(Paths.get(sourcePath).toFile());
    return new Dependency(artifact, null);
  }

  @Test
  public void testExtendContainerBuildPlan_noConfiguration() throws JibPluginExtensionException {
    ContainerBuildPlan buildPlan = ContainerBuildPlan.builder().build();
    ContainerBuildPlan newPlan =
        new JibLayerFilterExtension()
            .extendContainerBuildPlan(buildPlan, null, Optional.empty(), null, logger);
    assertSame(buildPlan, newPlan);
    verify(logger).log(LogLevel.WARN, "Nothing configured for Jib Layer Filter Extension");
  }

  @Test
  public void testExtendContainerBuildPlan_noGlobGiven() {
    ContainerBuildPlan buildPlan = ContainerBuildPlan.builder().build();

    Configuration.Filter filter = mockFilter("", "doesn't matter");
    when(config.getFilters()).thenReturn(Arrays.asList(filter));

    try {
      new JibLayerFilterExtension()
          .extendContainerBuildPlan(buildPlan, null, Optional.of(config), null, logger);
      fail();
    } catch (JibPluginExtensionException ex) {
      assertEquals(JibLayerFilterExtension.class, ex.getExtensionClass());
      assertEquals("glob pattern not given in filter configuration", ex.getMessage());
    }
  }

  @Test
  public void testExtendContainerBuildPlan_movingToExistingLayerNotAllowed() {
    FileEntriesLayer layer = buildLayer("same layer name", Arrays.asList("/foo"));
    ContainerBuildPlan buildPlan = ContainerBuildPlan.builder().addLayer(layer).build();

    Configuration.Filter filter = mockFilter("", "same layer name");
    when(config.getFilters()).thenReturn(Arrays.asList(filter));

    try {
      new JibLayerFilterExtension()
          .extendContainerBuildPlan(buildPlan, null, Optional.of(config), null, logger);
      fail();
    } catch (JibPluginExtensionException ex) {
      assertEquals(JibLayerFilterExtension.class, ex.getExtensionClass());
      assertEquals(
          "moving files into built-in layer 'same layer name' is not supported; specify a new "
              + "layer name in '<toLayer>'.",
          ex.getMessage());
    }
  }

  @Test
  public void testExtendContainerBuildPlan_emptyOriginalLayerNameDoesNotClash()
      throws JibPluginExtensionException {
    FileEntriesLayer layer = buildLayer("" /* deliberately empty */, Arrays.asList("/foo"));
    ContainerBuildPlan buildPlan = ContainerBuildPlan.builder().addLayer(layer).build();

    Configuration.Filter filter = mockFilter("nothing/matches", "");
    when(config.getFilters()).thenReturn(Arrays.asList(filter));

    ContainerBuildPlan newPlan =
        new JibLayerFilterExtension()
            .extendContainerBuildPlan(buildPlan, null, Optional.of(config), null, logger);

    FileEntriesLayer newLayer = (FileEntriesLayer) newPlan.getLayers().get(0);
    assertEquals(layer.getEntries(), newLayer.getEntries());
  }

  @Test
  public void testExtendContainerBuildPlan_noMatches() throws JibPluginExtensionException {
    FileEntriesLayer layer1 = buildLayer("", Arrays.asList("/foo"));
    FileEntriesLayer layer2 = buildLayer("", Arrays.asList("/foo", "/bar"));
    ContainerBuildPlan buildPlan =
        ContainerBuildPlan.builder().addLayer(layer1).addLayer(layer2).build();

    Configuration.Filter filter = mockFilter("nothing/matches", "");
    when(config.getFilters()).thenReturn(Arrays.asList(filter));

    ContainerBuildPlan newPlan =
        new JibLayerFilterExtension()
            .extendContainerBuildPlan(buildPlan, null, Optional.of(config), null, logger);

    assertEquals(2, newPlan.getLayers().size());
    FileEntriesLayer newLayer1 = (FileEntriesLayer) newPlan.getLayers().get(0);
    FileEntriesLayer newLayer2 = (FileEntriesLayer) newPlan.getLayers().get(1);

    assertEquals("", newLayer1.getName());
    assertEquals("", newLayer2.getName());
    assertEquals(layer1.getEntries(), newLayer1.getEntries());
    assertEquals(layer2.getEntries(), newLayer2.getEntries());
  }

  @Test
  public void testExtendContainerBuildPlan_deletion() throws JibPluginExtensionException {
    FileEntriesLayer layer1 = buildLayer("", Arrays.asList("/foo"));
    FileEntriesLayer layer2 = buildLayer("", Arrays.asList("/foo", "/bar", "/foo/baz"));
    ContainerBuildPlan buildPlan =
        ContainerBuildPlan.builder().addLayer(layer1).addLayer(layer2).build();

    Configuration.Filter filter = mockFilter("**", "");
    when(config.getFilters()).thenReturn(Arrays.asList(filter));

    ContainerBuildPlan newPlan =
        new JibLayerFilterExtension()
            .extendContainerBuildPlan(buildPlan, null, Optional.of(config), null, logger);

    assertEquals(0, newPlan.getLayers().size());
  }

  @Test
  public void testExtendContainerBuildPlan_sameLayerNameInMultipleFilters()
      throws JibPluginExtensionException {
    FileEntriesLayer layer =
        buildLayer("", Arrays.asList("/filter1", "/filter2", "/filter3", "/filter4", "/filter5"));
    ContainerBuildPlan buildPlan = ContainerBuildPlan.builder().addLayer(layer).build();

    Configuration.Filter filter1 = mockFilter("/filter1", "foo");
    Configuration.Filter filter2 = mockFilter("/filter2", "same layer name");
    Configuration.Filter filter3 = mockFilter("/filter3", "bar");
    Configuration.Filter filter4 = mockFilter("/filter4", "same layer name");
    Configuration.Filter filter5 = mockFilter("/filter5", "baz");
    when(config.getFilters())
        .thenReturn(Arrays.asList(filter1, filter2, filter3, filter4, filter5));

    JibLayerFilterExtension extension = new JibLayerFilterExtension();
    ContainerBuildPlan newPlan =
        extension.extendContainerBuildPlan(buildPlan, null, Optional.of(config), null, logger);

    ArrayList<String> layerNames = new ArrayList<>(extension.newToLayers.keySet());
    assertEquals(Arrays.asList("foo", "same layer name", "bar", "baz"), layerNames);

    assertEquals(4, newPlan.getLayers().size());
    FileEntriesLayer newLayer1 = (FileEntriesLayer) newPlan.getLayers().get(0);
    FileEntriesLayer newLayer2 = (FileEntriesLayer) newPlan.getLayers().get(1);
    FileEntriesLayer newLayer3 = (FileEntriesLayer) newPlan.getLayers().get(2);
    FileEntriesLayer newLayer4 = (FileEntriesLayer) newPlan.getLayers().get(3);

    assertEquals("foo", newLayer1.getName());
    assertEquals("same layer name", newLayer2.getName());
    assertEquals("bar", newLayer3.getName());
    assertEquals("baz", newLayer4.getName());

    assertEquals(Arrays.asList("/filter1"), layerToExtractionPaths(newLayer1));
    assertEquals(Arrays.asList("/filter2", "/filter4"), layerToExtractionPaths(newLayer2));
    assertEquals(Arrays.asList("/filter3"), layerToExtractionPaths(newLayer3));
    assertEquals(Arrays.asList("/filter5"), layerToExtractionPaths(newLayer4));
  }

  @Test
  public void testExtendContainerBuildPlan_lastConfigWins() throws JibPluginExtensionException {
    FileEntriesLayer layer = buildLayer("extra files", Arrays.asList("/foo"));
    ContainerBuildPlan buildPlan = ContainerBuildPlan.builder().addLayer(layer).build();

    Configuration.Filter filter1 = mockFilter("**", "looser");
    Configuration.Filter filter2 = mockFilter("**", "winner");
    when(config.getFilters()).thenReturn(Arrays.asList(filter1, filter2));

    ContainerBuildPlan newPlan =
        new JibLayerFilterExtension()
            .extendContainerBuildPlan(buildPlan, null, Optional.of(config), null, logger);

    assertEquals(1, newPlan.getLayers().size());
    FileEntriesLayer newLayer = (FileEntriesLayer) newPlan.getLayers().get(0);

    assertEquals("winner", newLayer.getName());
    assertEquals(layer.getEntries(), newLayer.getEntries());
  }

  @Test
  public void testExtendContainerBuildPlan_complex() throws JibPluginExtensionException {
    FileEntriesLayer layer1 =
        buildLayer("foo", Arrays.asList("/alpha/Alice", "/alpha/Bob", "/beta/Alice", "/beta/Bob"));
    FileEntriesLayer layer2 =
        buildLayer(
            "app", Arrays.asList("/alpha/Charlie", "/alpha/David", "/beta/Charlie", "/beta/David"));
    FileEntriesLayer layer3 = buildLayer("app", Arrays.asList("/unmatched/foo", "/unmatched/bar"));
    FileEntriesLayer layer4 =
        buildLayer(
            "app", Arrays.asList("/gamma/Alice", "/gamma/Bob", "/gamma/Charlie", "/gamme/David"));
    ContainerBuildPlan buildPlan =
        ContainerBuildPlan.builder()
            .setLayers(Arrays.asList(layer1, layer2, layer3, layer4))
            .build();

    Configuration.Filter filter1 = mockFilter("/alpha/**", "alpha Alice");
    Configuration.Filter filter2 = mockFilter("/?????/*", "alpha gamma");
    Configuration.Filter filter3 = mockFilter("**/Bob", "Bob");
    Configuration.Filter filter4 = mockFilter("/gamma/C*", "gamma Charlie");
    Configuration.Filter filter5 = mockFilter("**/Alice", "alpha Alice");
    Configuration.Filter filter6 = mockFilter("**/David", "");
    when(config.getFilters())
        .thenReturn(Arrays.asList(filter1, filter2, filter3, filter4, filter5, filter6));

    ContainerBuildPlan newPlan =
        new JibLayerFilterExtension()
            .extendContainerBuildPlan(buildPlan, null, Optional.of(config), null, logger);

    assertEquals(6, newPlan.getLayers().size());

    List<String> layerNames =
        newPlan.getLayers().stream().map(LayerObject::getName).collect(Collectors.toList());
    assertEquals(
        Arrays.asList("app", "app", "alpha Alice", "alpha gamma", "Bob", "gamma Charlie"),
        layerNames);

    FileEntriesLayer newLayer1 = (FileEntriesLayer) newPlan.getLayers().get(0);
    FileEntriesLayer newLayer2 = (FileEntriesLayer) newPlan.getLayers().get(1);
    FileEntriesLayer newLayer3 = (FileEntriesLayer) newPlan.getLayers().get(2);
    FileEntriesLayer newLayer4 = (FileEntriesLayer) newPlan.getLayers().get(3);
    FileEntriesLayer newLayer5 = (FileEntriesLayer) newPlan.getLayers().get(4);
    FileEntriesLayer newLayer6 = (FileEntriesLayer) newPlan.getLayers().get(5);

    assertEquals(Arrays.asList("/beta/Charlie"), layerToExtractionPaths(newLayer1));
    assertEquals(
        Arrays.asList("/unmatched/foo", "/unmatched/bar"), layerToExtractionPaths(newLayer2));
    assertEquals(
        Arrays.asList("/alpha/Alice", "/beta/Alice", "/gamma/Alice"),
        layerToExtractionPaths(newLayer3));
    assertEquals(Arrays.asList("/alpha/Charlie"), layerToExtractionPaths(newLayer4));
    assertEquals(
        Arrays.asList("/alpha/Bob", "/beta/Bob", "/gamma/Bob"), layerToExtractionPaths(newLayer5));
    assertEquals(Arrays.asList("/gamma/Charlie"), layerToExtractionPaths(newLayer6));
  }

  @Test
  public void testExtendContainerBuildPlan_createParentLayers_noProjectDependenciesResolver() {
    ContainerBuildPlan buildPlan = ContainerBuildPlan.builder().build();

    when(config.isCreateParentDependencyLayers()).thenReturn(true);
    try {
      new JibLayerFilterExtension()
          .extendContainerBuildPlan(buildPlan, null, Optional.of(config), mavenData, logger);
      fail();
    } catch (JibPluginExtensionException ex) {
      assertEquals(JibLayerFilterExtension.class, ex.getExtensionClass());
      assertEquals(
          "Try to get parent dependencies, but ProjectDependenciesResolver is null. Please use a more recent jib plugin version to fix this.",
          ex.getMessage());
    }
  }

  @Test
  public void testExtendContainerBuildPlan_createParentLayers_noParent() {
    ContainerBuildPlan buildPlan = ContainerBuildPlan.builder().build();

    when(config.isCreateParentDependencyLayers()).thenReturn(true);
    when(mavenProject.getParent()).thenReturn(null);

    try {
      JibLayerFilterExtension extension = new JibLayerFilterExtension();
      extension.extendContainerBuildPlan(buildPlan, null, Optional.of(config), mavenData, logger);
      fail();
    } catch (JibPluginExtensionException ex) {
      assertEquals(JibLayerFilterExtension.class, ex.getExtensionClass());
      assertEquals("Try to get parent dependencies, but project has no parent.", ex.getMessage());
    }
  }

  @Test
  public void testExtendContainerBuildPlan_createParentLayers_noParentDependencies()
      throws JibPluginExtensionException {
    FileEntriesLayer layer1 = buildLayer("", Arrays.asList("/app/libs/parent-lib-1.0.0.jar"));
    ContainerBuildPlan buildPlan = ContainerBuildPlan.builder().addLayer(layer1).build();

    when(config.isCreateParentDependencyLayers()).thenReturn(true);
    when(dependencyResolutionResult.getDependencies()).thenReturn(Collections.emptyList());

    JibLayerFilterExtension extension = new JibLayerFilterExtension();
    extension.dependencyResolver = projectDependenciesResolver;

    ContainerBuildPlan newPlan =
        extension.extendContainerBuildPlan(buildPlan, null, Optional.of(config), mavenData, logger);

    assertEquals(1, newPlan.getLayers().size());
    FileEntriesLayer newLayer1 = (FileEntriesLayer) newPlan.getLayers().get(0);
    assertEquals("", newLayer1.getName());
    assertEquals(layer1.getEntries(), newLayer1.getEntries());
  }

  @Test
  public void testExtendContainerBuildPlan_createParentLayers_withParentDependencies()
      throws JibPluginExtensionException {
    FileEntriesLayer layer1 =
        buildLayer(
            "layer1",
            Arrays.asList(
                "parentlib1path",
                "directlibpath",
                // The extension makes no assumptions about the filenames' structure.
                // Only if a dependency could not be found, it needs the suffix .jar
                // and the artifact id within the filename to find and log potential matches
                "parent-lib-different-version-2.0.0-whatever.jar"),
            Arrays.asList(
                "/whatever/parent-lib1-1.0.0.jar",
                "/whatever/direct-lib-2.0.0.jar",
                "/whatever/parent-lib-different-version-2.0.0.jar"));
    FileEntriesLayer layer2 =
        buildLayer(
            "layer2",
            Arrays.asList("parentlib2path"),
            Arrays.asList("/whatever/parent-lib2-3.0.0.jar"));

    ContainerBuildPlan buildPlan =
        ContainerBuildPlan.builder().addLayer(layer1).addLayer(layer2).build();

    when(config.isCreateParentDependencyLayers()).thenReturn(true);

    Dependency parentDependency1 = mockDependency("parentlib1path", "parent-lib1");
    Dependency parentDependency2 = mockDependency("parentlib2path", "parent-lib2");
    // If the resolved file path for the dependency does not match, the dependency must not be moved
    // to parent layer
    Dependency nonMatchingParentDependency =
        mockDependency(
            "parent-lib-different-version-1.0.0-whatever.jar", "parent-lib-different-version");
    // A parent dependency that has been filtered before and so does not exist in any layer
    Dependency filteredParentDependency =
        mockDependency("parentlibfilteredpath", "parent-lib-filtered");

    when(dependencyResolutionResult.getDependencies())
        .thenReturn(
            Arrays.asList(
                parentDependency1,
                parentDependency2,
                nonMatchingParentDependency,
                filteredParentDependency));

    JibLayerFilterExtension extension = new JibLayerFilterExtension();
    extension.dependencyResolver = projectDependenciesResolver;

    ContainerBuildPlan newPlan =
        extension.extendContainerBuildPlan(buildPlan, null, Optional.of(config), mavenData, logger);

    assertEquals(3, newPlan.getLayers().size());

    List<FileEntriesLayer> expectedNewLayers =
        Arrays.asList(
            buildLayer(
                "layer1-parent",
                Arrays.asList("parentlib1path"),
                Arrays.asList("/whatever/parent-lib1-1.0.0.jar")),
            buildLayer(
                "layer1",
                Arrays.asList("directlibpath", "parent-lib-different-version-2.0.0-whatever.jar"),
                Arrays.asList(
                    "/whatever/direct-lib-2.0.0.jar",
                    "/whatever/parent-lib-different-version-2.0.0.jar")),
            buildLayer(
                "layer2-parent",
                Arrays.asList("parentlib2path"),
                Arrays.asList("/whatever/parent-lib2-3.0.0.jar")));

    for (int i = 0; i < expectedNewLayers.size(); i++) {
      assertEquals(expectedNewLayers.get(i).getName(), newPlan.getLayers().get(i).getName());
      assertEquals(
          expectedNewLayers.get(i).getName(),
          expectedNewLayers.get(i).getEntries(),
          ((FileEntriesLayer) newPlan.getLayers().get(i)).getEntries());
    }

    verify(logger)
        .log(
            LogLevel.INFO,
            "Dependency from parent not found: parent-lib-different-version-1.0.0-whatever.jar");
    verify(logger)
        .log(LogLevel.INFO, "Potential matches: parent-lib-different-version-2.0.0-whatever.jar");
    verify(logger).log(LogLevel.INFO, "Dependency from parent not found: parentlibfilteredpath");
  }
}
