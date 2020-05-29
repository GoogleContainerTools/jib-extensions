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

import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FileEntry;
import com.google.cloud.tools.jib.api.buildplan.LayerObject;
import com.google.cloud.tools.jib.maven.extension.JibMavenPluginExtension;
import com.google.cloud.tools.jib.maven.extension.MavenData;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger.LogLevel;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import com.google.common.annotations.VisibleForTesting;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class JibLayerFilterExtension implements JibMavenPluginExtension<Configuration> {

  private Map<PathMatcher, String> pathMatchers = new LinkedHashMap<>();

  // (layer name, layer builder) map for new layers of configured <moveIntoLayerName>
  @VisibleForTesting
  Map<String, FileEntriesLayer.Builder> newMoveIntoLayers = new LinkedHashMap<>();

  @Override
  public Optional<Class<Configuration>> getExtraConfigType() {
    return Optional.of(Configuration.class);
  }

  @Override
  public ContainerBuildPlan extendContainerBuildPlan(
      ContainerBuildPlan buildPlan,
      Map<String, String> properties,
      Optional<Configuration> config,
      MavenData mavenData,
      ExtensionLogger logger)
      throws JibPluginExtensionException {
    logger.log(LogLevel.LIFECYCLE, "Running Jib Layer Filter Extension");
    if (!config.isPresent()) {
      logger.log(LogLevel.WARN, "Nothing configured for Jib Layer Filter Extension");
      return buildPlan;
    }

    preparePathMatchersAndLayerBuilders(buildPlan, config.get());

    @SuppressWarnings("unchecked")
    List<FileEntriesLayer> originalLayers = (List<FileEntriesLayer>) buildPlan.getLayers();
    List<FileEntriesLayer> filteredOriginalLayers = new ArrayList<>();

    // Start filtering original layers.
    for (FileEntriesLayer layer : originalLayers) {
      List<FileEntry> filesToKeep = new ArrayList<>();

      for (FileEntry entry : layer.getEntries()) {
        Optional<String> finalLayerName = determineFinalLayerName(entry, layer.getName());
        // Either keep, move, or delete this fileEntry.
        if (finalLayerName.isPresent()) {
          if (finalLayerName.get().equals(layer.getName())) {
            filesToKeep.add(entry);
          } else {
            newMoveIntoLayers.get(finalLayerName.get()).addEntry(entry);
          }
        }
      }
      filteredOriginalLayers.add(layer.toBuilder().setEntries(filesToKeep).build());
    }

    // Add filtered original layers first, then newly created non-empty layers (if any).
    ContainerBuildPlan.Builder planBuilder = buildPlan.toBuilder();
    planBuilder.setLayers(filteredOriginalLayers);
    for (FileEntriesLayer.Builder layerBuilder : newMoveIntoLayers.values()) {
      FileEntriesLayer newLayer = layerBuilder.build();
      if (!newLayer.getEntries().isEmpty()) {
        planBuilder.addLayer(layerBuilder.build());
      }
    }
    return planBuilder.build();
  }

  private void preparePathMatchersAndLayerBuilders(
      ContainerBuildPlan buildPlan, Configuration config) throws JibPluginExtensionException {
    List<String> originalLayerNames =
        buildPlan.getLayers().stream().map(LayerObject::getName).collect(Collectors.toList());

    for (Configuration.Filter filter : config.getFilters()) {
      String targetLayerName = filter.getMoveIntoLayerName();
      if (!targetLayerName.isEmpty() && originalLayerNames.contains(targetLayerName)) {
        throw new JibPluginExtensionException(
            getClass(),
            "moving files into built-in layer '"
                + targetLayerName
                + "' is not supported; specify a new layer name in '<moveIntoLayerName>'.");
      }
      pathMatchers.put(
          FileSystems.getDefault().getPathMatcher("glob:" + filter.getGlob()),
          filter.getMoveIntoLayerName());

      if (!newMoveIntoLayers.containsKey(targetLayerName)) {
        newMoveIntoLayers.put(targetLayerName, FileEntriesLayer.builder().setName(targetLayerName));
      }
    }
  }

  /**
   * Determines where this {@code fileEntry} finally belongs after filtering. The last matching
   * filter in the configuration order wins.
   *
   * @param fileEntry file entry in question
   * @param originalLayerName name of the original layer where {@code fileEntry} exists
   * @return final layer name into which {@code fileEntry} should move. May be same as {@code
   *     originalLayerName}. {@link Optional#empty()} indicates deletion.
   */
  private Optional<String> determineFinalLayerName(FileEntry fileEntry, String originalLayerName) {
    Optional<String> finalLayerName = Optional.of(originalLayerName);

    for (Map.Entry<PathMatcher, String> mapEntry : pathMatchers.entrySet()) {
      PathMatcher matcher = mapEntry.getKey();
      Path pathInContainer = Paths.get(fileEntry.getExtractionPath().toString());
      if (matcher.matches(pathInContainer)) {
        String moveIntoLayerName = mapEntry.getValue();
        if (moveIntoLayerName.isEmpty()) {
          finalLayerName = Optional.empty(); // Mark deletion.
        } else {
          finalLayerName = Optional.of(moveIntoLayerName);
        }
      }
    }
    return finalLayerName;
  }
}
