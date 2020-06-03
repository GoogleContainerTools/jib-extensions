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

package com.google.cloud.tools.jib.maven.extension.ownership;

import java.util.ArrayList;
import java.util.List;

/**
 * Extension-specific Maven configuration.
 *
 * <p>Example usage in {@code pom.xml}:
 *
 * <pre>{@code
 * <configuration implementation="com.google.cloud.tools.jib.plugins.extension.maven.ownership.Configuration">
 *   <rules>
 *     <!-- sets UID 300 for all files under /app/classes/ -->
 *     <rule>
 *       <glob>/app/classes/**</glob>
 *       <ownership>300</ownership>
 *     </rule>
 *     <!-- sets UID 300 and GID 500 for all files under /static/ -->
 *     <rule>
 *       <glob>/static/**</glob>
 *       <ownership>300:500</ownership>
 *     </rule>
 *   </rules>
 * </configuration>
 * }</pre>
 */
public class Configuration {

  public static class Rule {
    private String glob = "";
    private String ownership = "";

    public String getGlob() {
      return glob;
    }

    public String getOwnership() {
      return ownership;
    }
  }

  private List<Rule> rules = new ArrayList<>();

  public List<Rule> getRules() {
    return rules;
  }
}
