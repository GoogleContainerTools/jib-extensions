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

import java.util.List;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

/**
 * Extension-specific Gradle configuration.
 *
 * <p>Example usage in {@code build.gradle}:
 *
 * <pre>{@code
 * configuration {
 *   rules {
 *     // sets UID 300 for all files under /app/classes/
 *     rule {
 *       glob = '/app/classes/**'
 *       ownership = '300'
 *     }
 *     // sets UID 300 and GID 500 for all files under /static/
 *     rule {
 *       glob = '/static/**'
 *       ownership = '300:500'
 *     }
 *   }
 * }
 * }</pre>
 */
public class Configuration {

  public static class Rule {
    private String glob = "";
    private String ownership = "";

    @Input
    public String getGlob() {
      return glob;
    }

    @Input
    @Optional
    public String getOwnership() {
      return ownership;
    }
  }

  public class RuleSpec {

    /**
     * Adds a new rule configuration to the rules list.
     *
     * @param action closure representing a rule configuration
     */
    public void rule(Action<? super Rule> action) {
      Rule filter = project.getObjects().newInstance(Rule.class);
      action.execute(filter);
      rules.add(filter);
    }
  }

  private final Project project;
  private final RuleSpec ruleSpec;
  private final ListProperty<Rule> rules;

  /**
   * Constructor used to inject a Gradle project.
   *
   * @param project the injected Gradle project
   */
  @Inject
  public Configuration(Project project) {
    this.project = project;
    ruleSpec = project.getObjects().newInstance(RuleSpec.class);
    rules = project.getObjects().listProperty(Rule.class).empty();
  }

  public List<Rule> getRules() {
    return rules.get();
  }

  public void filters(Action<? super RuleSpec> action) {
    action.execute(ruleSpec);
  }
}
