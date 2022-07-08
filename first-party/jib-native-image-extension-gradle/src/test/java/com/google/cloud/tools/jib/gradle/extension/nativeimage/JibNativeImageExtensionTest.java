/*
 * Copyright 2022 Google LLC.
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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
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

/** Tests for {@link JibNativeImageExtension}. */
@RunWith(MockitoJUnitRunner.class)
public class JibNativeImageExtensionTest {

  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  @Mock private ExtensionLogger logger;
  @Mock private DefaultConvention defaultConvention;
  @Mock private Project project;
  @Mock private JibExtension jibPlugin;
  @Mock private ContainerParameters jibContainer;

  private final GradleData gradleData = () -> project;

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
    assertThat(JibNativeImageExtension.getExecutableName(jibContainer, properties))
        .isEqualTo(Optional.of("theExecutable"));
  }

  @Test
  public void testEntrypoint() throws JibPluginExtensionException, IOException {
    Map<String, String> properties = Collections.singletonMap("imageName", "theExecutable");
    tempFolder.newFolder("native/nativeCompile");
    tempFolder.newFile("native/nativeCompile/theExecutable");

    ContainerBuildPlan buildPlan =
        ContainerBuildPlan.builder()
            .setEntrypoint(Collections.singletonList("to be overwritten"))
            .build();
    ContainerBuildPlan newPlan =
        new JibNativeImageExtension()
            .extendContainerBuildPlan(buildPlan, properties, Optional.empty(), gradleData, logger);

    assertThat(newPlan.getEntrypoint()).isEqualTo(Collections.singletonList("/app/theExecutable"));
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

    assertThat(newPlan.getEntrypoint()).isEqualTo(Collections.singletonList("set by Jib"));
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

    assertThat(newPlan.getEntrypoint())
        .isEqualTo(Collections.singletonList("/new/root/theExecutable"));
    assertThat(newPlan.getLayers().size()).isEqualTo(1);
    assertThat(layerToExtractionPaths((FileEntriesLayer) newPlan.getLayers().get(0)))
        .isEqualTo(Collections.singletonList("/new/root/theExecutable"));
  }

  @Test
  public void testNoExecutableNameDetected() {
    ContainerBuildPlan buildPlan = ContainerBuildPlan.builder().build();

    Exception exception =
        assertThrows(
            JibPluginExtensionException.class,
            () ->
                new JibNativeImageExtension()
                    .extendContainerBuildPlan(
                        buildPlan, Collections.emptyMap(), Optional.empty(), gradleData, logger));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "cannot auto-detect native-image executable name; consider setting 'imageName' property");
  }

  @Test
  public void testExecutableNotFound() {
    Map<String, String> properties = Collections.singletonMap("imageName", "theExecutable");
    ContainerBuildPlan buildPlan = ContainerBuildPlan.builder().build();

    Exception exception =
        assertThrows(
            JibPluginExtensionException.class,
            () ->
                new JibNativeImageExtension()
                    .extendContainerBuildPlan(
                        buildPlan, properties, Optional.empty(), gradleData, logger));
    assertThat(exception)
        .hasMessageThat()
        .startsWith(
            "Native-image executable does not exist or not a file: "
                + tempFolder.getRoot().toPath().resolve("native/nativeCompile/theExecutable"));
  }

  @Test
  public void testCantFindJibPlugin() throws IOException, JibPluginExtensionException {
    when(project.getExtensions().findByType(JibExtension.class)).thenReturn(null);
    Map<String, String> properties = Collections.singletonMap("imageName", "theExecutable");
    tempFolder.newFolder("native/nativeCompile/");
    tempFolder.newFile("native/nativeCompile/theExecutable");

    ContainerBuildPlan buildPlan = ContainerBuildPlan.builder().build();
    Exception exception =
        assertThrows(
            JibPluginExtensionException.class,
            () ->
                new JibNativeImageExtension()
                    .extendContainerBuildPlan(
                        buildPlan, properties, Optional.empty(), gradleData, logger));
    assertThat(exception).hasMessageThat().isEqualTo("Can't find jib plugin!");
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

    assertThat(newPlan.getLayers().size()).isEqualTo(1);
    FileEntriesLayer newLayer = (FileEntriesLayer) newPlan.getLayers().get(0);
    assertThat(newLayer.getName()).isEqualTo("native image");

    assertThat(newLayer.getEntries().size()).isEqualTo(1);
    FileEntry fileEntry = newLayer.getEntries().get(0);
    assertThat(fileEntry.getExtractionPath()).isEqualTo(AbsoluteUnixPath.get("/app/theExecutable"));
    assertThat(fileEntry.getPermissions()).isEqualTo(FilePermissions.fromOctalString("755"));
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

    assertThat(newPlan.getLayers().size()).isEqualTo(3);
    assertThat(layerToExtractionPaths((FileEntriesLayer) newPlan.getLayers().get(0)))
        .isEqualTo(Collections.singletonList("/app/theExecutable"));
    assertThat(layerToExtractionPaths((FileEntriesLayer) newPlan.getLayers().get(1)))
        .isEqualTo(Collections.singletonList("/dest/extra file1"));
    assertThat(layerToExtractionPaths((FileEntriesLayer) newPlan.getLayers().get(2)))
        .isEqualTo(Collections.singletonList("/dest/extra file2"));
  }
}
