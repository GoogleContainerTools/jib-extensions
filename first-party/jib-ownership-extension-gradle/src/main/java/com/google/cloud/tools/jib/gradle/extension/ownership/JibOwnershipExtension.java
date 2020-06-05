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

package com.google.cloud.tools.jib.gradle.extension.ownership;

import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FileEntry;
import com.google.cloud.tools.jib.gradle.extension.GradleData;
import com.google.cloud.tools.jib.gradle.extension.JibGradlePluginExtension;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger.LogLevel;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;

public class JibOwnershipExtension implements JibGradlePluginExtension<Configuration> {

  private Map<PathMatcher, String> pathMatchers = new LinkedHashMap<>();

  @Override
  public Optional<Class<Configuration>> getExtraConfigType() {
    return Optional.of(Configuration.class);
  }

  @Override
  public ContainerBuildPlan extendContainerBuildPlan(
      ContainerBuildPlan buildPlan,
      Map<String, String> properties,
      Optional<Configuration> config,
      GradleData gradleData,
      ExtensionLogger logger)
      throws JibPluginExtensionException {
    logger.log(LogLevel.LIFECYCLE, "Running Jib Ownership Extension");
    if (!config.isPresent()) {
      logger.log(LogLevel.WARN, "Nothing configured for Jib Ownership Extension");
      return buildPlan;
    }

    for (Configuration.Rule rule : config.get().getRules()) {
      if (rule.getGlob().isEmpty()) {
        throw new JibPluginExtensionException(
            getClass(), "glob pattern not given in ownership configuration");
      }
      pathMatchers.put(
          FileSystems.getDefault().getPathMatcher("glob:" + rule.getGlob()), rule.getOwnership());
    }

    @SuppressWarnings("unchecked")
    List<FileEntriesLayer> layers = (List<FileEntriesLayer>) buildPlan.getLayers();
    List<FileEntriesLayer> newLayers =
        layers.stream().map(this::applyRulesToLayer).collect(Collectors.toList());
    return buildPlan.toBuilder().setLayers(newLayers).build();
  }

  private FileEntriesLayer applyRulesToLayer(FileEntriesLayer layer) {
    List<FileEntry> entries =
        layer.getEntries().stream().map(this::applyRulesToFileEntry).collect(Collectors.toList());
    return layer.toBuilder().setEntries(entries).build();
  }

  private FileEntry applyRulesToFileEntry(FileEntry entry) {
    String newOwnership = null;

    for (Entry<PathMatcher, String> mapEntry : pathMatchers.entrySet()) {
      PathMatcher matcher = mapEntry.getKey();
      Path pathInContainer = Paths.get(entry.getExtractionPath().toString());
      if (matcher.matches(pathInContainer)) {
        newOwnership = mapEntry.getValue();
      }
    }
    return newOwnership == null
        ? entry
        : new FileEntry(
            entry.getSourceFile(),
            entry.getExtractionPath(),
            entry.getPermissions(),
            entry.getModificationTime(),
            newOwnership);
  }
}
