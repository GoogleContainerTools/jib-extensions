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

package com.google.cloud.tools.jib.gradle.extension.nativeimage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FileEntry;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import com.google.cloud.tools.jib.gradle.ContainerParameters;
import com.google.cloud.tools.jib.gradle.JibExtension;
import com.google.cloud.tools.jib.gradle.extension.GradleData;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.internal.extensibility.DefaultConvention;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Tests for {@link JibNativeImageExtension}.
 */
@RunWith(MockitoJUnitRunner.class)
public class JibNativeImageExtensionTest {

  @Rule
  public final TemporaryFolder tempFolder = new TemporaryFolder();

  @Mock
  private ExtensionLogger logger;

  @Mock
  private DefaultConvention defaultConvention;

  @Mock
  private Project project;
  private final GradleData gradleData = () -> project;
  @Mock
  private JibExtension jibPlugin;
  @Mock
  private ContainerParameters jibContainer;

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
    when(project.getExtensions()).thenReturn(defaultConvention);
    when(project.getExtensions().findByType(JibExtension.class)).thenReturn(jibPlugin);
    when(jibPlugin.getContainer()).thenReturn(jibContainer);

    when(project.getBuildDir()).thenReturn(tempFolder.getRoot());
  }

  @Test
  public void testGetExecutableName_property() {
    Map<String, String> properties = Collections.singletonMap("imageName", "theExecutable");
    assertEquals(
        Optional.of("theExecutable"),
        JibNativeImageExtension.getExecutableName(jibContainer, properties));
  }

  @Test
  public void testEntrypoint() throws JibPluginExtensionException, IOException {
    Map<String, String> properties = Collections.singletonMap("imageName", "theExecutable");
    tempFolder.newFolder("native/nativeCompile");
    tempFolder.newFile("native/nativeCompile/theExecutable");

    ContainerBuildPlan buildPlan =
        ContainerBuildPlan.builder().setEntrypoint(Collections.singletonList("to be overwritten")).build();
    ContainerBuildPlan newPlan =
        new JibNativeImageExtension()
            .extendContainerBuildPlan(buildPlan, properties, Optional.empty(), gradleData, logger);

    assertEquals(Collections.singletonList("/app/theExecutable"), newPlan.getEntrypoint());
  }

  @Test
  public void testEntrypoint_setByJib() throws JibPluginExtensionException, IOException {
    Map<String, String> properties = Collections.singletonMap("imageName", "theExecutable");
    tempFolder.newFolder("native/nativeCompile/");
    tempFolder.newFile("native/nativeCompile/theExecutable");

    when(jibContainer.getEntrypoint()).thenReturn(Collections.singletonList("non-empty"));


    ContainerBuildPlan buildPlan =
        ContainerBuildPlan.builder().setEntrypoint(Collections.singletonList("set by Jib")).build();
    ContainerBuildPlan newPlan =
        new JibNativeImageExtension()
            .extendContainerBuildPlan(buildPlan, properties, Optional.empty(), gradleData, logger);

    assertEquals(Collections.singletonList("set by Jib"), newPlan.getEntrypoint());
  }

  @Test
  public void testAppRoot() throws JibPluginExtensionException, IOException {
    Map<String, String> properties = Collections.singletonMap("imageName", "theExecutable");
    tempFolder.newFolder("native/nativeCompile/");
    tempFolder.newFile("native/nativeCompile/theExecutable");

    when(jibContainer.getAppRoot()).thenReturn("/new/root");

    ContainerBuildPlan buildPlan = ContainerBuildPlan.builder().build();
    ContainerBuildPlan newPlan =
        new JibNativeImageExtension()
            .extendContainerBuildPlan(buildPlan, properties, Optional.empty(), gradleData, logger);

    assertEquals(Arrays.asList("/new/root/theExecutable"), newPlan.getEntrypoint());

    assertEquals(1, newPlan.getLayers().size());
    FileEntriesLayer layer = (FileEntriesLayer) newPlan.getLayers().get(0);
    assertEquals(Arrays.asList("/new/root/theExecutable"), layerToExtractionPaths(layer));
  }

  @Test
  public void testNoExecutableNameDetected() {
    ContainerBuildPlan buildPlan = ContainerBuildPlan.builder().build();

    try {
      new JibNativeImageExtension()
          .extendContainerBuildPlan(
              buildPlan, Collections.emptyMap(), Optional.empty(), gradleData, logger);
      fail();
    } catch (JibPluginExtensionException ex) {
      assertEquals(
          "cannot auto-detect native-image executable name; consider setting 'imageName' property",
          ex.getMessage());
    }
  }

  @Test
  public void testExecutableNotFound() {
    Map<String, String> properties = Collections.singletonMap("imageName", "theExecutable");
    ContainerBuildPlan buildPlan = ContainerBuildPlan.builder().build();

    try {
      new JibNativeImageExtension()
          .extendContainerBuildPlan(buildPlan, properties, Optional.empty(), gradleData, logger);
      fail();
    } catch (JibPluginExtensionException ex) {
      assertEquals(
          "Native-image executable does not exist or not a file: "
              + tempFolder.getRoot().toPath().resolve("native/nativeCompile/theExecutable")
              + "\nDid you run the 'native-image:native-image' goal?",
          ex.getMessage());
    }
  }

  @Test
  public void testExtendContainerBuildPlan() throws JibPluginExtensionException, IOException {
    Map<String, String> properties = Collections.singletonMap("imageName", "theExecutable");
    tempFolder.newFolder("native/nativeCompile/");
    tempFolder.newFile("native/nativeCompile/theExecutable");

    FileEntriesLayer layer = buildLayer("original layer", Paths.get("foo.txt"));
    ContainerBuildPlan buildPlan = ContainerBuildPlan.builder().addLayer(layer).build();

    ContainerBuildPlan newPlan =
        new JibNativeImageExtension()
            .extendContainerBuildPlan(buildPlan, properties, Optional.empty(), gradleData, logger);

    assertEquals(1, newPlan.getLayers().size());
    FileEntriesLayer newLayer = (FileEntriesLayer) newPlan.getLayers().get(0);
    assertEquals("native image", newLayer.getName());

    assertEquals(1, newLayer.getEntries().size());
    FileEntry fileEntry = newLayer.getEntries().get(0);
    assertEquals(AbsoluteUnixPath.get("/app/theExecutable"), fileEntry.getExtractionPath());
    assertEquals(FilePermissions.fromOctalString("755"), fileEntry.getPermissions());
  }

  @Test
  public void testExtendContainerBuildPlan_preserveExtraDirectories()
      throws JibPluginExtensionException, IOException {
    Map<String, String> properties = Collections.singletonMap("imageName", "theExecutable");
    tempFolder.newFolder("native/nativeCompile");
    tempFolder.newFile("native/nativeCompile/theExecutable");

    FileEntriesLayer layer = buildLayer("original layer", Paths.get("foo.txt"));
    FileEntriesLayer extraLayer1 = buildLayer("extra files", Paths.get("extra file1"));
    FileEntriesLayer extraLayer2 = buildLayer("extra files", Paths.get("extra file2"));
    ContainerBuildPlan buildPlan =
        ContainerBuildPlan.builder()
            .setLayers(Arrays.asList(layer, extraLayer1, extraLayer2))
            .build();

    ContainerBuildPlan newPlan =
        new JibNativeImageExtension()
            .extendContainerBuildPlan(buildPlan, properties, Optional.empty(), gradleData, logger);

    assertEquals(3, newPlan.getLayers().size());
    FileEntriesLayer newLayer1 = (FileEntriesLayer) newPlan.getLayers().get(0);
    FileEntriesLayer newLayer2 = (FileEntriesLayer) newPlan.getLayers().get(1);
    FileEntriesLayer newLayer3 = (FileEntriesLayer) newPlan.getLayers().get(2);

    assertEquals(Arrays.asList("/app/theExecutable"), layerToExtractionPaths(newLayer1));
    assertEquals(Arrays.asList("/dest/extra file1"), layerToExtractionPaths(newLayer2));
    assertEquals(Arrays.asList("/dest/extra file2"), layerToExtractionPaths(newLayer3));
  }
}
