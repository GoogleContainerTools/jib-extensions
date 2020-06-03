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

package com.google.cloud.tools.jib.gradle.extension.layerfilter;

import java.util.List;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;

/**
 * Extension-specific Gradle configuration.
 *
 * <p>Example usage in {@code build.gradle}:
 *
 * <pre>{@code
 * configuration {
 *   filters {
 *     filter {
 *       glob = '**&#47;google-*.jar'
 *       toLayer = 'google libraries'
 *     }
 *     filter {
 *       glob = '/app/libs/in-house-*.jar'
 *       toLayer = 'in-house dependencies'
 *     }
 *   }
 * }
 * }</pre>
 */
public class Configuration {

  public static class Filter {
    private String glob = "";
    private String toLayer = "";

    @Input
    public String getGlob() {
      return glob;
    }

    public void setGlob(String glob) {
      this.glob = glob;
    }

    @Input
    @Optional
    public String getToLayer() {
      return toLayer;
    }

    public void setToLayer(String toLayer) {
      this.toLayer = toLayer;
    }
  }

  public static class FiltersSpec {

    private final Project project;
    private final ListProperty<Filter> filters;

    @Inject
    public FiltersSpec(Project project) {
      this.project = project;
      filters = project.getObjects().listProperty(Filter.class).empty();
    }

    private ListProperty<Filter> getFilters() {
      return filters;
    }

    /**
     * Adds a new filter configuration to the filters list.
     *
     * @param action closure representing a filter configuration
     */
    public void filter(Action<? super Filter> action) {
      Filter filter = project.getObjects().newInstance(Filter.class);
      action.execute(filter);
      filters.add(filter);
    }
  }

  private final FiltersSpec filtersSpec;

  /**
   * Constructor used to inject a Gradle project.
   *
   * @param project the injected Gradle project
   */
  @Inject
  public Configuration(Project project) {
    filtersSpec = project.getObjects().newInstance(FiltersSpec.class, project);
  }

  @Nested
  public List<Filter> getFilters() {
    return filtersSpec.getFilters().get();
  }

  public void filters(Action<? super FiltersSpec> action) {
    action.execute(filtersSpec);
  }
}
