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

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;

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
    @VisibleForTesting String glob = "";
    @VisibleForTesting String toLayer = "";

    String getGlob() {
      return glob;
    }

    String getToLayer() {
      return toLayer;
    }
  }

  @VisibleForTesting List<Filter> filters = new ArrayList<>();

  List<Filter> getFilters() {
    return filters;
  }
}
