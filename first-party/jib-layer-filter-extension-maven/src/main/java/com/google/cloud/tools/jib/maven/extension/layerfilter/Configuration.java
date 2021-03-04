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

import java.util.ArrayList;
import java.util.List;

/**
 * Extension-specific Maven configuration.
 *
 * <p>Example usage in {@code pom.xml}:
 *
 * <pre>{@code
 * <configuration implementation="com.google.cloud.tools.jib.maven.extension.layerfilter.Configuration">
 *   <filters>
 *     <filter>
 *       <glob>**&#47;google-*.jar</glob>
 *       <toLayer>google libraries</toLayer>
 *     </filter>
 *     <filter>
 *       <glob>/app/libs/in-house-*.jar</glob>
 *       <toLayer>in-house dependencies</toLayer>
 *     </filter>
 *   </filters>
 *   <createParentDependencyLayers>true</createParentDependencyLayers>
 * </configuration>
 * }</pre>
 */
public class Configuration {

  public static class Filter {
    private String glob = "";
    private String toLayer = "";

    public String getGlob() {
      return glob;
    }

    public String getToLayer() {
      return toLayer;
    }
  }

  private List<Filter> filters = new ArrayList<>();

  /**
   * Whether to create separate layers for dependencies that stem from the parent POM. The parent
   * layers are created after the filters have been applied. For every original layer and every
   * layer defined by the filters, a parent layer will be created (if not empty).
   */
  private boolean createParentDependencyLayers;

  public List<Filter> getFilters() {
    return filters;
  }

  public boolean isCreateParentDependencyLayers() {
    return createParentDependencyLayers;
  }
}
