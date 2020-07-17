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

package com.google.cloud.tools.jib.maven.extension.nativeimage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FileEntry;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import com.google.cloud.tools.jib.maven.extension.MavenData;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class JibNativeImageExtensionTest {

  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  @Mock private MavenData mavenData;
  @Mock private ExtensionLogger logger;

  @Mock private MavenProject project;
  @Mock private Plugin plugin;

  private final Map<String, String> properites = new HashMap<>();

  private static FileEntriesLayer buildLayer(String layerName, Path... paths) {
    FileEntriesLayer.Builder builder = FileEntriesLayer.builder().setName(layerName);
    for (Path path : paths) {
      builder.addEntry(path, AbsoluteUnixPath.get("/dest/" + path.getFileName()));
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

  private static Xpp3Dom newDom(String name, String value) {
    Xpp3Dom node = new Xpp3Dom(name);
    node.setValue(value);
    return node;
  }

  private static Xpp3Dom addDomChild(Xpp3Dom parent, String name, String value) {
    Xpp3Dom node = new Xpp3Dom(name);
    node.setValue(value);
    parent.addChild(node);
    return node;
  }

  private static Xpp3Dom buildDom(List<String> domPath, String value) {
    Xpp3Dom root = new Xpp3Dom(domPath.get(0));

    Xpp3Dom node = root;
    for (String nodeName : domPath.subList(1, domPath.size())) {
      Xpp3Dom child = new Xpp3Dom(nodeName);
      node.addChild(child);
      node = child;
    }
    node.setValue(value);
    return root;
  }

  @Before
  public void setUp() {
    Build build = mock(Build.class);
    when(build.getDirectory()).thenReturn(tempFolder.getRoot().toString());
    when(project.getBuild()).thenReturn(build);
    when(mavenData.getMavenProject()).thenReturn(project);
  }

  @Test
  public void testGetDomValue_null() {
    assertFalse(JibNativeImageExtension.getDomValue(null).isPresent());
    assertFalse(JibNativeImageExtension.getDomValue(null, "foo", "bar").isPresent());
  }

  @Test
  public void testGetDomValue_noPathGiven() {
    Xpp3Dom root = buildDom(Arrays.asList("root"), "value");

    assertEquals(Optional.of("value"), JibNativeImageExtension.getDomValue(root));
  }

  @Test
  public void testGetDomValue_noChild() {
    Xpp3Dom root = newDom("root", "value");

    assertFalse(JibNativeImageExtension.getDomValue(root, "foo").isPresent());
    assertFalse(JibNativeImageExtension.getDomValue(root, "foo", "bar").isPresent());
  }

  @Test
  public void testGetDomValue_childPathMatched() {
    Xpp3Dom root = newDom("root", "value");
    Xpp3Dom foo = addDomChild(root, "foo", "fooValue");
    addDomChild(foo, "bar", "barValue");

    assertEquals(Optional.of("fooValue"), JibNativeImageExtension.getDomValue(root, "foo"));
    assertEquals(Optional.of("barValue"), JibNativeImageExtension.getDomValue(root, "foo", "bar"));
    assertEquals(Optional.of("barValue"), JibNativeImageExtension.getDomValue(foo, "bar"));
  }

  @Test
  public void testGetDomValue_notFullyMatched() {
    Xpp3Dom root = newDom("root", "value");
    Xpp3Dom foo = addDomChild(root, "foo", "fooValue");
    addDomChild(foo, "bar", "barValue");

    assertFalse(JibNativeImageExtension.getDomValue(root, "baz").isPresent());
    assertFalse(JibNativeImageExtension.getDomValue(root, "foo", "baz").isPresent());
  }

  @Test
  public void testGetDomValue_nullValue() {
    Xpp3Dom root = buildDom(Arrays.asList("root", "foo"), null);

    assertFalse(JibNativeImageExtension.getDomValue(root).isPresent());
    assertFalse(JibNativeImageExtension.getDomValue(root, "foo").isPresent());
  }

  @Test
  public void testGetExecutableName_nothingDefined() {
    assertFalse(
        JibNativeImageExtension.getExecutableName(project, Collections.emptyMap()).isPresent());
  }

  @Test
  public void testGetExecutableName_property() {
    Map<String, String> properties = Collections.singletonMap("imageName", "theExecutable");
    assertEquals(
        Optional.of("theExecutable"),
        JibNativeImageExtension.getExecutableName(project, properties));
  }

  @Test
  public void testGetExecutableName_imageName() {
    when(project.getPlugin("org.graalvm.nativeimage:native-image-maven-plugin")).thenReturn(plugin);
    Xpp3Dom configuration = buildDom(Arrays.asList("configuration", "imageName"), "theExecutable");
    when(plugin.getConfiguration()).thenReturn(configuration);

    assertEquals(
        Optional.of("theExecutable"),
        JibNativeImageExtension.getExecutableName(project, properites));
  }

  @Test
  public void testGetExecutableName_mainClass() {
    when(project.getPlugin("org.graalvm.nativeimage:native-image-maven-plugin")).thenReturn(plugin);
    Xpp3Dom configuration = buildDom(Arrays.asList("configuration", "mainClass"), "MyMain");
    when(plugin.getConfiguration()).thenReturn(configuration);

    assertEquals(
        Optional.of("mymain"), JibNativeImageExtension.getExecutableName(project, properites));
  }

  @Test
  public void testGetExecutableName_fromJarPlugin() {
    when(project.getPlugin("org.apache.maven.plugins:maven-jar-plugin")).thenReturn(plugin);
    Xpp3Dom configuration =
        buildDom(Arrays.asList("configuration", "archive", "manifest", "mainClass"), "MyMain");
    when(plugin.getConfiguration()).thenReturn(configuration);

    assertEquals(
        Optional.of("mymain"), JibNativeImageExtension.getExecutableName(project, properites));
  }

  @Test
  public void testGetExecutableName_fromAssemblyPlugin() {
    when(project.getPlugin("org.apache.maven.plugins:maven-assembly-plugin")).thenReturn(plugin);
    Xpp3Dom configuration =
        buildDom(Arrays.asList("configuration", "archive", "manifest", "mainClass"), "MyMain");
    when(plugin.getConfiguration()).thenReturn(configuration);

    assertEquals(
        Optional.of("mymain"), JibNativeImageExtension.getExecutableName(project, properites));
  }

  @Test
  public void testGetExecutableName_fromShadePlugin() {
    when(project.getPlugin("org.apache.maven.plugins:maven-shade-plugin")).thenReturn(plugin);
    Xpp3Dom configuration =
        buildDom(
            Arrays.asList("configuration", "transformers", "transformer", "mainClass"), "MyMain");
    PluginExecution execution = mock(PluginExecution.class);
    when(execution.getConfiguration()).thenReturn(configuration);
    when(plugin.getExecutions()).thenReturn(Arrays.asList(execution));

    assertEquals(
        Optional.of("mymain"), JibNativeImageExtension.getExecutableName(project, properites));
  }

  @Test
  public void testEntrypoint() throws JibPluginExtensionException, IOException {
    Map<String, String> properties = Collections.singletonMap("imageName", "theExecutable");
    tempFolder.newFile("theExecutable");

    ContainerBuildPlan buildPlan =
        ContainerBuildPlan.builder().setEntrypoint(Arrays.asList("to be overwritten")).build();
    ContainerBuildPlan newPlan =
        new JibNativeImageExtension()
            .extendContainerBuildPlan(buildPlan, properties, Optional.empty(), mavenData, logger);

    assertEquals(Arrays.asList("/app/theExecutable"), newPlan.getEntrypoint());
  }

  @Test
  public void testEntrypoint_setByJib() throws JibPluginExtensionException, IOException {
    Map<String, String> properties = Collections.singletonMap("imageName", "theExecutable");
    tempFolder.newFile("theExecutable");

    when(project.getPlugin("com.google.cloud.tools:jib-maven-plugin")).thenReturn(plugin);
    Xpp3Dom configuration =
        buildDom(Arrays.asList("configuration", "container", "entrypoint"), "non-empty");
    when(plugin.getConfiguration()).thenReturn(configuration);

    ContainerBuildPlan buildPlan =
        ContainerBuildPlan.builder().setEntrypoint(Arrays.asList("set by Jib")).build();
    ContainerBuildPlan newPlan =
        new JibNativeImageExtension()
            .extendContainerBuildPlan(buildPlan, properties, Optional.empty(), mavenData, logger);

    assertEquals(Arrays.asList("set by Jib"), newPlan.getEntrypoint());
  }

  @Test
  public void testAppRoot() throws JibPluginExtensionException, IOException {
    Map<String, String> properties = Collections.singletonMap("imageName", "theExecutable");
    tempFolder.newFile("theExecutable");

    when(project.getPlugin("com.google.cloud.tools:jib-maven-plugin")).thenReturn(plugin);
    Xpp3Dom configuration =
        buildDom(Arrays.asList("configuration", "container", "appRoot"), "/new/root");
    when(plugin.getConfiguration()).thenReturn(configuration);

    ContainerBuildPlan buildPlan = ContainerBuildPlan.builder().build();
    ContainerBuildPlan newPlan =
        new JibNativeImageExtension()
            .extendContainerBuildPlan(buildPlan, properties, Optional.empty(), mavenData, logger);

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
              buildPlan, Collections.emptyMap(), Optional.empty(), mavenData, logger);
      fail();
    } catch (JibPluginExtensionException ex) {
      assertEquals(
          "cannot auto-detect native-image executable name; consider setting 'executableName'",
          ex.getMessage());
    }
  }

  @Test
  public void testExecutableNotFound() {
    Map<String, String> properties = Collections.singletonMap("imageName", "theExecutable");
    ContainerBuildPlan buildPlan = ContainerBuildPlan.builder().build();

    try {
      new JibNativeImageExtension()
          .extendContainerBuildPlan(buildPlan, properties, Optional.empty(), mavenData, logger);
      fail();
    } catch (JibPluginExtensionException ex) {
      assertEquals(
          "Native-image executable does not exist or not a file: "
              + tempFolder.getRoot().toPath().resolve("theExecutable")
              + "\nDid you run the 'native-image:native-image' goal?",
          ex.getMessage());
    }
  }

  @Test
  public void testExtendContainerBuildPlan() throws JibPluginExtensionException, IOException {
    Map<String, String> properties = Collections.singletonMap("imageName", "theExecutable");
    tempFolder.newFile("theExecutable");

    FileEntriesLayer layer = buildLayer("original layer", Paths.get("foo.txt"));
    ContainerBuildPlan buildPlan = ContainerBuildPlan.builder().addLayer(layer).build();

    ContainerBuildPlan newPlan =
        new JibNativeImageExtension()
            .extendContainerBuildPlan(buildPlan, properties, Optional.empty(), mavenData, logger);

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
    tempFolder.newFile("theExecutable");

    FileEntriesLayer layer = buildLayer("original layer", Paths.get("foo.txt"));
    FileEntriesLayer extraLayer1 = buildLayer("extra files", Paths.get("extra file1"));
    FileEntriesLayer extraLayer2 = buildLayer("extra files", Paths.get("extra file2"));
    ContainerBuildPlan buildPlan =
        ContainerBuildPlan.builder()
            .setLayers(Arrays.asList(layer, extraLayer1, extraLayer2))
            .build();

    ContainerBuildPlan newPlan =
        new JibNativeImageExtension()
            .extendContainerBuildPlan(buildPlan, properties, Optional.empty(), mavenData, logger);

    assertEquals(3, newPlan.getLayers().size());
    FileEntriesLayer newLayer1 = (FileEntriesLayer) newPlan.getLayers().get(0);
    FileEntriesLayer newLayer2 = (FileEntriesLayer) newPlan.getLayers().get(1);
    FileEntriesLayer newLayer3 = (FileEntriesLayer) newPlan.getLayers().get(2);

    assertEquals(Arrays.asList("/app/theExecutable"), layerToExtractionPaths(newLayer1));
    assertEquals(Arrays.asList("/dest/extra file1"), layerToExtractionPaths(newLayer2));
    assertEquals(Arrays.asList("/dest/extra file2"), layerToExtractionPaths(newLayer3));
  }
}
