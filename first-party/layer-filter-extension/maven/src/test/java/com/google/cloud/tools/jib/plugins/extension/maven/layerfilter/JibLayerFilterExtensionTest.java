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

package com.google.cloud.tools.jib.plugins.extension.maven.layerfilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.verify;

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer.Builder;
import com.google.cloud.tools.jib.api.buildplan.LayerObject;
import com.google.cloud.tools.jib.maven.extension.MavenData;
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

  @Mock private MavenData mavenData;
  @Mock private ExtensionLogger logger;

  private Map<String, String> properties;

  private static FileEntriesLayer buildLayer(String layerName, List<String> filePaths) {
    Builder builder = FileEntriesLayer.builder().setName(layerName);
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
    Configuration config = new Configuration();
    config.filters = Arrays.asList(new Configuration.Filter());
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

    Configuration.Filter filter = new Configuration.Filter();
    filter.moveIntoLayerName = "same layer name";
    Configuration config = new Configuration();
    config.filters = Arrays.asList(filter);

    try {
      new JibLayerFilterExtension()
          .extendContainerBuildPlan(buildPlan, properties, Optional.of(config), mavenData, logger);
      fail();
    } catch (JibPluginExtensionException ex) {
      assertEquals(JibLayerFilterExtension.class, ex.getExtensionClass());
      assertEquals(
          "moving files into built-in layer 'same layer name' is not supported; specify a new "
              + "layer name in '<moveIntoLayerName>'.",
          ex.getMessage());
    }
  }

  @Test
  public void testExtendContainerBuildPlan_emptyOriginalLayerNameDoesNotClash()
      throws JibPluginExtensionException {
    FileEntriesLayer layer = buildLayer("" /* deliberately empty */, Arrays.asList("/foo"));
    ContainerBuildPlan buildPlan = ContainerBuildPlan.builder().addLayer(layer).build();

    Configuration.Filter filter = new Configuration.Filter();
    filter.glob = "nothing/matches";
    filter.moveIntoLayerName = "";
    Configuration config = new Configuration();
    config.filters = Arrays.asList(filter);

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

    Configuration.Filter filter = new Configuration.Filter();
    filter.glob = "nothing/matches";
    Configuration config = new Configuration();
    config.filters = Arrays.asList(filter);

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

    Configuration.Filter filter = new Configuration.Filter();
    filter.glob = "**";
    Configuration config = new Configuration();
    config.filters = Arrays.asList(filter);

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

    Configuration.Filter filter1 = new Configuration.Filter();
    filter1.glob = "/filter1";
    filter1.moveIntoLayerName = "foo";
    Configuration.Filter filter2 = new Configuration.Filter();
    filter2.glob = "/filter2";
    filter2.moveIntoLayerName = "same layer name";
    Configuration.Filter filter3 = new Configuration.Filter();
    filter3.glob = "/filter3";
    filter3.moveIntoLayerName = "bar";
    Configuration.Filter filter4 = new Configuration.Filter();
    filter4.glob = "/filter4";
    filter4.moveIntoLayerName = "same layer name";
    Configuration.Filter filter5 = new Configuration.Filter();
    filter5.glob = "/filter5";
    filter5.moveIntoLayerName = "baz";
    Configuration config = new Configuration();
    config.filters = Arrays.asList(filter1, filter2, filter3, filter4, filter5);

    JibLayerFilterExtension extension = new JibLayerFilterExtension();
    ContainerBuildPlan newPlan =
        extension.extendContainerBuildPlan(
            buildPlan, properties, Optional.of(config), mavenData, logger);

    ArrayList<String> layerNames = new ArrayList<>(extension.newMoveIntoLayers.keySet());
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

    Configuration.Filter filter1 = new Configuration.Filter();
    filter1.glob = "**";
    filter1.moveIntoLayerName = "looser";
    Configuration.Filter filter2 = new Configuration.Filter();
    filter2.glob = "**";
    filter2.moveIntoLayerName = "winner";
    Configuration config = new Configuration();
    config.filters = Arrays.asList(filter1, filter2);

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

    Configuration.Filter filter1 = new Configuration.Filter();
    filter1.glob = "/alpha/**";
    filter1.moveIntoLayerName = "alpha Alice";
    Configuration.Filter filter2 = new Configuration.Filter();
    filter2.glob = "/?????/*";
    filter2.moveIntoLayerName = "alpha gamma";
    Configuration.Filter filter3 = new Configuration.Filter();
    filter3.glob = "**/Bob";
    filter3.moveIntoLayerName = "Bob";
    Configuration.Filter filter4 = new Configuration.Filter();
    filter4.glob = "/gamma/C*";
    filter4.moveIntoLayerName = "gamma Charlie";
    Configuration.Filter filter5 = new Configuration.Filter();
    filter5.glob = "**/Alice";
    filter5.moveIntoLayerName = "alpha Alice";
    Configuration.Filter filter6 = new Configuration.Filter();
    filter6.glob = "**/David";
    Configuration config = new Configuration();
    config.filters = Arrays.asList(filter1, filter2, filter3, filter4, filter5, filter6);

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
