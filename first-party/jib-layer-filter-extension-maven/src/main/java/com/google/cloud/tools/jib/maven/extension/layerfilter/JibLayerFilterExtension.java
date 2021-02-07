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
import com.google.common.base.Verify;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.util.filter.ScopeDependencyFilter;

public class JibLayerFilterExtension implements JibMavenPluginExtension<Configuration> {

  private Map<PathMatcher, String> pathMatchers = new LinkedHashMap<>();

  // (layer name, layer builder) map for new layers of configured <toLayer>
  @VisibleForTesting Map<String, FileEntriesLayer.Builder> newToLayers = new LinkedHashMap<>();

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

    ContainerBuildPlan.Builder newPlanBuilder = buildPlan.toBuilder();
    newPlanBuilder.setLayers(Collections.emptyList());

    @SuppressWarnings("unchecked")
    List<FileEntriesLayer> originalLayers = (List<FileEntriesLayer>) buildPlan.getLayers();
    // Start filtering original layers.
    for (FileEntriesLayer layer : originalLayers) {
      List<FileEntry> filesToKeep = new ArrayList<>();

      for (FileEntry entry : layer.getEntries()) {
        Optional<String> finalLayerName = determineFinalLayerName(entry, layer.getName());
        // Either keep, move, or delete this FileEntry.
        if (finalLayerName.isPresent()) {
          if (finalLayerName.get().equals(layer.getName())) {
            filesToKeep.add(entry);
          } else {
            FileEntriesLayer.Builder targetLayerBuilder =
                Verify.verifyNotNull(newToLayers.get(finalLayerName.get()));
            targetLayerBuilder.addEntry(entry);
          }
        }
      }

      if (!filesToKeep.isEmpty()) {
        newPlanBuilder.addLayer(layer.toBuilder().setEntries(filesToKeep).build());
      }
    }

    // Add newly created non-empty to-layers (if any).
    newToLayers
        .values()
        .stream()
        .map(FileEntriesLayer.Builder::build)
        .filter(layer -> !layer.getEntries().isEmpty())
        .forEach(newPlanBuilder::addLayer);
    
   
    
    ContainerBuildPlan newPlan = newPlanBuilder.build();
    
    newPlan = moveParentDepsToNewLayers(newPlan, mavenData, logger);
    
    return newPlan;
  }

  private ContainerBuildPlan moveParentDepsToNewLayers(ContainerBuildPlan buildPlan, MavenData mavenData,
      ExtensionLogger logger) {

    if (mavenData.getMavenProject().getParent() == null) {
      // Keep plan unchanged
      return buildPlan;
    }
    logger.log(LogLevel.LIFECYCLE, "Moving parent dependencies to new layers.");


    // the key is the expected path for the parent dependency
    Map<String, Artifact> parentDependencies = getParentDependencies(mavenData).stream()
        .map(d -> d.getArtifact())
        //TODO: configurable?
        .collect(Collectors.toMap(a -> "/app/libs/"+a.getArtifactId()+"-"+a.getVersion()+".jar", a -> a));
    
    // parent dependencies that have not been found in any layer (due to different version or filtering)
    Map<String, Artifact> parentDependenciesNotFound = new HashMap<>(parentDependencies);
    
    List<FileEntriesLayer.Builder> newLayers = new ArrayList<>();
    

    @SuppressWarnings("unchecked")
    List<FileEntriesLayer> originalLayers = ((List<FileEntriesLayer>) buildPlan.getLayers());
    originalLayers.forEach(l -> {
      // for each layer, create a parent layer
      FileEntriesLayer.Builder toParentLayerBuilder = FileEntriesLayer.builder().setName(l.getName()+" parent");
      newLayers.add(toParentLayerBuilder);
      // ... and the normal layer
      FileEntriesLayer.Builder toLayerBuilder = FileEntriesLayer.builder().setName(l.getName());
      newLayers.add(toLayerBuilder);
     
      
      l.getEntries().forEach(fe -> {
        String path = fe.getExtractionPath().toString();
        if(parentDependencies.containsKey(path)) {
          // move to parent layer
          toParentLayerBuilder.addEntry(fe);
          // mark parent dep as found
          parentDependenciesNotFound.remove(path);
        } else {
          //keep in original layer
          toLayerBuilder.addEntry(fe);
        }
        
      });

    });
    
    parentDependenciesNotFound.forEach((path, artifact) -> {     
      logger.log(LogLevel.ERROR, "Dependency from parent not found: "+artifact );
    });

    return buildPlanWithNewLayers(buildPlan, newLayers);
  }

  private ContainerBuildPlan buildPlanWithNewLayers(ContainerBuildPlan buildPlan, List<FileEntriesLayer.Builder> newLayers) {
    ContainerBuildPlan.Builder newPlanBuilder = buildPlan.toBuilder();
    newPlanBuilder.setLayers(Collections.emptyList());
    
    // Add newly created non-empty to-layers (if any).
    newLayers
        .stream()
        .map(FileEntriesLayer.Builder::build)
        .filter(layer -> !layer.getEntries().isEmpty())
        .forEach(newPlanBuilder::addLayer);
    
    return newPlanBuilder.build();
  }

  private List<Dependency> getParentDependencies(MavenData mavenData) {
    try {
    ProjectDependenciesResolver resolver = (ProjectDependenciesResolver) mavenData.getMavenSession()
        .lookup(org.apache.maven.project.ProjectDependenciesResolver.class.getName());
    
    DefaultDependencyResolutionRequest request = new DefaultDependencyResolutionRequest(mavenData.getMavenProject().getParent(), mavenData.getMavenSession().getRepositorySession());
    request.setResolutionFilter(new ScopeDependencyFilter("test"));
    DependencyResolutionResult resolutionResult = resolver.resolve(request); 
 
    //TODO: Probably should use resolved dependencies, where snapshot versions are expanded!
    return resolutionResult.getDependencies();
    } catch (ComponentLookupException | DependencyResolutionException e) {
      throw new RuntimeException("Error when getting parent dependencies: ", e);
    }
  }


  private void preparePathMatchersAndLayerBuilders(
      ContainerBuildPlan buildPlan, Configuration config) throws JibPluginExtensionException {
    List<String> originalLayerNames =
        buildPlan.getLayers().stream().map(LayerObject::getName).collect(Collectors.toList());

    for (Configuration.Filter filter : config.getFilters()) {
      String toLayerName = filter.getToLayer();
      if (!toLayerName.isEmpty() && originalLayerNames.contains(toLayerName)) {
        throw new JibPluginExtensionException(
            getClass(),
            "moving files into built-in layer '"
                + toLayerName
                + "' is not supported; specify a new layer name in '<toLayer>'.");
      }
      if (filter.getGlob().isEmpty()) {
        throw new JibPluginExtensionException(
            getClass(), "glob pattern not given in filter configuration");
      }

      pathMatchers.put(
          FileSystems.getDefault().getPathMatcher("glob:" + filter.getGlob()), filter.getToLayer());

      if (!newToLayers.containsKey(toLayerName)) {
        newToLayers.put(toLayerName, FileEntriesLayer.builder().setName(toLayerName));
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
        String toLayerName = mapEntry.getValue();
        if (toLayerName.isEmpty()) {
          finalLayerName = Optional.empty(); // Mark deletion.
        } else {
          finalLayerName = Optional.of(toLayerName);
        }
      }
    }
    return finalLayerName;
  }
}
