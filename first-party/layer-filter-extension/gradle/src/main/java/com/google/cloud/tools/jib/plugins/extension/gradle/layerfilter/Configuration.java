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

package com.google.cloud.tools.jib.plugins.extension.gradle.layerfilter;

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
 *       glob = '**&#47;google-*'
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

    @Input
    @Optional
    public String getToLayer() {
      return toLayer;
    }
  }

  public class FilterSpec {

    public void filter(Action<? super Filter> action) {
      Filter filter = project.getObjects().newInstance(Filter.class);
      action.execute(filter);
      filters.add(filter);
    }
  }

  private final Project project;
  private final FilterSpec filterSpec;
  private final ListProperty<Filter> filters;

  @Inject
  public Configuration(Project project) {
    this.project = project;
    filterSpec = project.getObjects().newInstance(FilterSpec.class);
    filters = project.getObjects().listProperty(Filter.class).empty();
  }

  @Nested
  public List<Filter> getFilters() {
    return filters.get();
  }

  public void filters(Action<? super FilterSpec> action) {
    action.execute(filterSpec);
  }
}
