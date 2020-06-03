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

package com.google.cloud.tools.jib.plugins.extension.gradle.layerfilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.LayerObject;
import com.google.cloud.tools.jib.gradle.extension.GradleData;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger.LogLevel;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class JibLayerFilterExtensionTest {

  @Mock private Configuration config;
  @Mock private GradleData mavenData;
  @Mock private ExtensionLogger logger;

  private Map<String, String> properties;

  private static FileEntriesLayer buildLayer(String layerName, List<String> filePaths) {
    FileEntriesLayer.Builder builder = FileEntriesLayer.builder().setName(layerName);
    for (String path : filePaths) {
      builder.addEntry(Paths.get("whatever"), AbsoluteUnixPath.get(path));
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

  @Test
  public void testExtendContainerBuildPlan_noConfiguration() throws JibPluginExtensionException {
    ContainerBuildPlan buildPlan = ContainerBuildPlan.builder().build();
    ContainerBuildPlan newPlan =
        new JibLayerFilterExtension()
            .extendContainerBuildPlan(buildPlan, properties, Optional.empty(), mavenData, logger);
    assertSame(buildPlan, newPlan);
    verify(logger).log(LogLevel.WARN, "Nothing configured for Jib Layer Filter Extension");
  }

  @Test
  public void testExtendContainerBuildPlan_noGlobGiven() {
    ContainerBuildPlan buildPlan = ContainerBuildPlan.builder().build();

    Configuration.Filter filter = mock(Configuration.Filter.class);
    when(filter.getGlob()).thenReturn("");
    when(filter.getToLayer()).thenReturn("doesn't matter");
    when(config.getFilters()).thenReturn(Arrays.asList(filter));

    try {
      new JibLayerFilterExtension()
          .extendContainerBuildPlan(buildPlan, properties, Optional.of(config), mavenData, logger);
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

    Configuration.Filter filter = mock(Configuration.Filter.class);
    when(filter.getToLayer()).thenReturn("same layer name");
    when(config.getFilters()).thenReturn(Arrays.asList(filter));

    try {
      new JibLayerFilterExtension()
          .extendContainerBuildPlan(buildPlan, properties, Optional.of(config), mavenData, logger);
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

    Configuration.Filter filter = mock(Configuration.Filter.class);
    when(filter.getGlob()).thenReturn("nothing/matches");
    when(filter.getToLayer()).thenReturn("");
    when(config.getFilters()).thenReturn(Arrays.asList(filter));

    ContainerBuildPlan newPlan =
        new JibLayerFilterExtension()
            .extendContainerBuildPlan(
                buildPlan, properties, Optional.of(config), mavenData, logger);

    FileEntriesLayer newLayer = (FileEntriesLayer) newPlan.getLayers().get(0);
    assertEquals(layer.getEntries(), newLayer.getEntries());
  }

  @Test
  public void testExtendContainerBuildPlan_noMatches() throws JibPluginExtensionException {
    FileEntriesLayer layer1 = buildLayer("", Arrays.asList("/foo"));
    FileEntriesLayer layer2 = buildLayer("", Arrays.asList("/foo", "/bar"));
    ContainerBuildPlan buildPlan =
        ContainerBuildPlan.builder().addLayer(layer1).addLayer(layer2).build();

    Configuration.Filter filter = mock(Configuration.Filter.class);
    when(filter.getGlob()).thenReturn("nothing/matches");
    when(filter.getToLayer()).thenReturn("");
    when(config.getFilters()).thenReturn(Arrays.asList(filter));

    ContainerBuildPlan newPlan =
        new JibLayerFilterExtension()
            .extendContainerBuildPlan(
                buildPlan, properties, Optional.of(config), mavenData, logger);

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

    Configuration.Filter filter = mock(Configuration.Filter.class);
    when(filter.getGlob()).thenReturn("**");
    when(filter.getToLayer()).thenReturn("");
    when(config.getFilters()).thenReturn(Arrays.asList(filter));

    ContainerBuildPlan newPlan =
        new JibLayerFilterExtension()
            .extendContainerBuildPlan(
                buildPlan, properties, Optional.of(config), mavenData, logger);

    assertEquals(0, newPlan.getLayers().size());
  }

  @Test
  public void testExtendContainerBuildPlan_sameLayerNameInMultipleFilters()
      throws JibPluginExtensionException {
    FileEntriesLayer layer =
        buildLayer("", Arrays.asList("/filter1", "/filter2", "/filter3", "/filter4", "/filter5"));
    ContainerBuildPlan buildPlan = ContainerBuildPlan.builder().addLayer(layer).build();

    Configuration.Filter filter1 = mock(Configuration.Filter.class);
    when(filter1.getGlob()).thenReturn("/filter1");
    when(filter1.getToLayer()).thenReturn("foo");
    Configuration.Filter filter2 = mock(Configuration.Filter.class);
    when(filter2.getGlob()).thenReturn("/filter2");
    when(filter2.getToLayer()).thenReturn("same layer name");
    Configuration.Filter filter3 = mock(Configuration.Filter.class);
    when(filter3.getGlob()).thenReturn("/filter3");
    when(filter3.getToLayer()).thenReturn("bar");
    Configuration.Filter filter4 = mock(Configuration.Filter.class);
    when(filter4.getGlob()).thenReturn("/filter4");
    when(filter4.getToLayer()).thenReturn("same layer name");
    Configuration.Filter filter5 = mock(Configuration.Filter.class);
    when(filter5.getGlob()).thenReturn("/filter5");
    when(filter5.getToLayer()).thenReturn("baz");
    when(config.getFilters())
        .thenReturn(Arrays.asList(filter1, filter2, filter3, filter4, filter5));

    JibLayerFilterExtension extension = new JibLayerFilterExtension();
    ContainerBuildPlan newPlan =
        extension.extendContainerBuildPlan(
            buildPlan, properties, Optional.of(config), mavenData, logger);

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

    Configuration.Filter filter1 = mock(Configuration.Filter.class);
    when(filter1.getGlob()).thenReturn("**");
    when(filter1.getToLayer()).thenReturn("looser");
    Configuration.Filter filter2 = mock(Configuration.Filter.class);
    when(filter2.getGlob()).thenReturn("**");
    when(filter2.getToLayer()).thenReturn("winner");
    when(config.getFilters()).thenReturn(Arrays.asList(filter1, filter2));

    ContainerBuildPlan newPlan =
        new JibLayerFilterExtension()
            .extendContainerBuildPlan(
                buildPlan, properties, Optional.of(config), mavenData, logger);

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

    Configuration.Filter filter1 = mock(Configuration.Filter.class);
    when(filter1.getGlob()).thenReturn("/alpha/**");
    when(filter1.getToLayer()).thenReturn("alpha Alice");
    Configuration.Filter filter2 = mock(Configuration.Filter.class);
    when(filter2.getGlob()).thenReturn("/?????/*");
    when(filter2.getToLayer()).thenReturn("alpha gamma");
    Configuration.Filter filter3 = mock(Configuration.Filter.class);
    when(filter3.getGlob()).thenReturn("**/Bob");
    when(filter3.getToLayer()).thenReturn("Bob");
    Configuration.Filter filter4 = mock(Configuration.Filter.class);
    when(filter4.getGlob()).thenReturn("/gamma/C*");
    when(filter4.getToLayer()).thenReturn("gamma Charlie");
    Configuration.Filter filter5 = mock(Configuration.Filter.class);
    when(filter5.getGlob()).thenReturn("**/Alice");
    when(filter5.getToLayer()).thenReturn("alpha Alice");
    Configuration.Filter filter6 = mock(Configuration.Filter.class);
    when(filter6.getGlob()).thenReturn("**/David");
    when(filter6.getToLayer()).thenReturn("");
    when(config.getFilters())
        .thenReturn(Arrays.asList(filter1, filter2, filter3, filter4, filter5, filter6));

    ContainerBuildPlan newPlan =
        new JibLayerFilterExtension()
            .extendContainerBuildPlan(
                buildPlan, properties, Optional.of(config), mavenData, logger);

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
}
