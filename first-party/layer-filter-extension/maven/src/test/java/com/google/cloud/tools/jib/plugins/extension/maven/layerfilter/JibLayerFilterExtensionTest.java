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
import com.google.cloud.tools.jib.maven.extension.MavenData;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger.LogLevel;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import java.nio.file.Paths;
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

  private static FileEntriesLayer buildTestLayer(String layerName, List<String> filePaths) {
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
  public void testExtendContainerBuildPlan_movingToExistingLayer() {
    FileEntriesLayer layer = buildTestLayer("same layer name", Arrays.asList("/foo"));
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
    FileEntriesLayer layer = buildTestLayer("" /* deliberately empty */, Arrays.asList("/foo"));
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
    FileEntriesLayer layer1 = buildTestLayer("", Arrays.asList("/foo"));
    FileEntriesLayer layer2 = buildTestLayer("", Arrays.asList("/foo", "/bar"));
    ContainerBuildPlan buildPlan =
        ContainerBuildPlan.builder().setLayers(Arrays.asList(layer1, layer2)).build();

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
    FileEntriesLayer layer1 = buildTestLayer("", Arrays.asList("/foo"));
    FileEntriesLayer layer2 = buildTestLayer("", Arrays.asList("/foo", "/bar", "/foo/baz"));
    ContainerBuildPlan buildPlan =
        ContainerBuildPlan.builder().setLayers(Arrays.asList(layer1, layer2)).build();

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
  public void testExtendContainerBuildPlan() throws JibPluginExtensionException {
    FileEntriesLayer layer1 =
        FileEntriesLayer.builder()
            .addEntry(Paths.get("whatever"), AbsoluteUnixPath.get("/target/file"))
            .addEntry(Paths.get("whatever"), AbsoluteUnixPath.get("/target/another"))
            .addEntry(Paths.get("whatever"), AbsoluteUnixPath.get("/target/sub/dir/file"))
            .addEntry(Paths.get("whatever"), AbsoluteUnixPath.get("/target/sub/dir/another"))
            .addEntry(Paths.get("whatever"), AbsoluteUnixPath.get("/untouched/file"))
            .addEntry(Paths.get("whatever"), AbsoluteUnixPath.get("/untouched/another"))
            .build();
    FileEntriesLayer layer2 =
        FileEntriesLayer.builder()
            .addEntry(Paths.get("whatever"), AbsoluteUnixPath.get("/target/foo"))
            .addEntry(Paths.get("whatever"), AbsoluteUnixPath.get("/target/bar"))
            .addEntry(Paths.get("whatever"), AbsoluteUnixPath.get("/target/sub/dir/foo"))
            .addEntry(Paths.get("whatever"), AbsoluteUnixPath.get("/target/sub/dir/bar"))
            .addEntry(Paths.get("whatever"), AbsoluteUnixPath.get("/untouched/foo"))
            .addEntry(Paths.get("whatever"), AbsoluteUnixPath.get("/untouched/bar"))
            .build();
    ContainerBuildPlan buildPlan =
        ContainerBuildPlan.builder().setLayers(Arrays.asList(layer1, layer2)).build();

    Configuration.Filter filter1 = new Configuration.Filter();
    filter1.glob = "/target/**";
    filter1.moveIntoLayerName = "10:20";
    Configuration.Filter filter2 = new Configuration.Filter();
    filter2.glob = "**/bar";
    filter2.moveIntoLayerName = "999:777";
    Configuration config = new Configuration();
    config.filters = Arrays.asList(filter1, filter2);

    ContainerBuildPlan newPlan =
        new JibLayerFilterExtension()
            .extendContainerBuildPlan(
                buildPlan, properties, Optional.of(config), mavenData, logger);

    FileEntriesLayer newLayer1 = (FileEntriesLayer) newPlan.getLayers().get(0);
    FileEntriesLayer newLayer2 = (FileEntriesLayer) newPlan.getLayers().get(1);
    /*
      assertEquals(
          Arrays.asList(
              AbsoluteUnixPath.get("/target/file"),
              AbsoluteUnixPath.get("/target/another"),
              AbsoluteUnixPath.get("/target/sub/dir/file"),
              AbsoluteUnixPath.get("/target/sub/dir/another"),
              AbsoluteUnixPath.get("/untouched/file"),
              AbsoluteUnixPath.get("/untouched/another")),
          getExtractionPaths(newLayer1, FileEntry::getExtractionPath));
      assertEquals(
          Arrays.asList("10:20", "10:20", "10:20", "10:20", "", ""),
          getExtractionPaths(newLayer1, FileEntry::getOwnership));

      assertEquals(
          Arrays.asList(
              AbsoluteUnixPath.get("/target/foo"),
              AbsoluteUnixPath.get("/target/bar"),
              AbsoluteUnixPath.get("/target/sub/dir/foo"),
              AbsoluteUnixPath.get("/target/sub/dir/bar"),
              AbsoluteUnixPath.get("/untouched/foo"),
              AbsoluteUnixPath.get("/untouched/bar")),
          getExtractionPaths(newLayer2, FileEntry::getExtractionPath));
      assertEquals(
          Arrays.asList("10:20", "999:777", "10:20", "999:777", "", "999:777"),
          getExtractionPaths(newLayer2, FileEntry::getOwnership));
    */
  }
}
