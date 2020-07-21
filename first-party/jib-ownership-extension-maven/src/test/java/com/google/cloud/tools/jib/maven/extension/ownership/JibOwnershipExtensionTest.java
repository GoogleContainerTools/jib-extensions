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

package com.google.cloud.tools.jib.maven.extension.ownership;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FileEntry;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger.LogLevel;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link JibOwnershipExtension}. */
@RunWith(MockitoJUnitRunner.class)
public class JibOwnershipExtensionTest {

  @Mock private Configuration config;
  @Mock private ExtensionLogger logger;

  private static <T> List<T> mapLayerEntries(
      FileEntriesLayer layer, Function<FileEntry, T> mapper) {
    return layer.getEntries().stream().map(mapper).collect(Collectors.toList());
  }

  private static Configuration.Rule mockRule(String glob, String ownership) {
    Configuration.Rule filter = mock(Configuration.Rule.class);
    when(filter.getGlob()).thenReturn(glob);
    when(filter.getOwnership()).thenReturn(ownership);
    return filter;
  }

  @Test
  public void testExtendContainerBuildPlan_noConfiguration() throws JibPluginExtensionException {
    ContainerBuildPlan buildPlan = ContainerBuildPlan.builder().build();
    ContainerBuildPlan newPlan =
        new JibOwnershipExtension()
            .extendContainerBuildPlan(buildPlan, null, Optional.empty(), null, logger);
    assertSame(buildPlan, newPlan);
    verify(logger).log(LogLevel.WARN, "Nothing configured for Jib Ownership Extension");
  }

  @Test
  public void testExtendContainerBuildPlan_noGlobGiven() {
    ContainerBuildPlan buildPlan = ContainerBuildPlan.builder().build();

    Configuration.Rule rule = mockRule("", "doesn't matter");
    when(config.getRules()).thenReturn(Arrays.asList(rule));

    try {
      new JibOwnershipExtension()
          .extendContainerBuildPlan(buildPlan, null, Optional.of(config), null, logger);
      fail();
    } catch (JibPluginExtensionException ex) {
      assertEquals(JibOwnershipExtension.class, ex.getExtensionClass());
      assertEquals("glob pattern not given in ownership configuration", ex.getMessage());
    }
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
        ContainerBuildPlan.builder().addLayer(layer1).addLayer(layer2).build();

    Configuration.Rule rule1 = mockRule("/target/**", "10:20");
    Configuration.Rule rule2 = mockRule("**/bar", "999:777");
    when(config.getRules()).thenReturn(Arrays.asList(rule1, rule2));

    ContainerBuildPlan newPlan =
        new JibOwnershipExtension()
            .extendContainerBuildPlan(buildPlan, null, Optional.of(config), null, logger);

    FileEntriesLayer newLayer1 = (FileEntriesLayer) newPlan.getLayers().get(0);
    FileEntriesLayer newLayer2 = (FileEntriesLayer) newPlan.getLayers().get(1);

    assertEquals(
        Arrays.asList(
            AbsoluteUnixPath.get("/target/file"),
            AbsoluteUnixPath.get("/target/another"),
            AbsoluteUnixPath.get("/target/sub/dir/file"),
            AbsoluteUnixPath.get("/target/sub/dir/another"),
            AbsoluteUnixPath.get("/untouched/file"),
            AbsoluteUnixPath.get("/untouched/another")),
        mapLayerEntries(newLayer1, FileEntry::getExtractionPath));
    assertEquals(
        Arrays.asList("10:20", "10:20", "10:20", "10:20", "", ""),
        mapLayerEntries(newLayer1, FileEntry::getOwnership));

    assertEquals(
        Arrays.asList(
            AbsoluteUnixPath.get("/target/foo"),
            AbsoluteUnixPath.get("/target/bar"),
            AbsoluteUnixPath.get("/target/sub/dir/foo"),
            AbsoluteUnixPath.get("/target/sub/dir/bar"),
            AbsoluteUnixPath.get("/untouched/foo"),
            AbsoluteUnixPath.get("/untouched/bar")),
        mapLayerEntries(newLayer2, FileEntry::getExtractionPath));
    assertEquals(
        Arrays.asList("10:20", "999:777", "10:20", "999:777", "", "999:777"),
        mapLayerEntries(newLayer2, FileEntry::getOwnership));
  }

  @Test
  public void testExtendContainerBuildPlan_lastConfigWins() throws JibPluginExtensionException {
    FileEntriesLayer layer =
        FileEntriesLayer.builder()
            .addEntry(Paths.get("whatever"), AbsoluteUnixPath.get("/target/file"))
            .build();
    ContainerBuildPlan buildPlan = ContainerBuildPlan.builder().addLayer(layer).build();

    Configuration.Rule rule1 = mockRule("**", "10:20");
    Configuration.Rule rule2 = mockRule("**", "999:777");
    when(config.getRules()).thenReturn(Arrays.asList(rule1, rule2));

    ContainerBuildPlan newPlan =
        new JibOwnershipExtension()
            .extendContainerBuildPlan(buildPlan, null, Optional.of(config), null, logger);

    FileEntriesLayer newLayer = (FileEntriesLayer) newPlan.getLayers().get(0);
    assertEquals(Arrays.asList("999:777"), mapLayerEntries(newLayer, FileEntry::getOwnership));
  }
}
