/*
 * Copyright 2022 Google LLC.
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

package com.google.cloud.tools.jib.gradle.extension.quarkus;

import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

public class Configuration {

  public enum PackageType {
    LEGACY,
    FAST,
  }

  public static class PackagingWrapper {

    private PackageType name = PackageType.LEGACY;

    @Input
    public PackageType getName() {
      return name;
    }

    public void setName(PackageType name) {
      this.name = name;
    }
  }

  private final Property<PackagingWrapper> packageType;

  private final Project project;

  /**
   * Constructor used to inject a Gradle project.
   *
   * @param project the injected Gradle project
   */
  @Inject
  public Configuration(Project project) {
    this.project = project;
    this.packageType = project.getObjects().property(PackagingWrapper.class);
  }

  /**
   * Creates a package type configuration.
   *
   * @param action closure representing a package type configuration
   */
  public void packageType(Action<? super PackagingWrapper> action) {
    PackagingWrapper pack = project.getObjects().newInstance(PackagingWrapper.class);
    action.execute(pack);
    packageType.set(pack);
  }

  public PackageType getPackageType() {
    return this.packageType.get().name;
  }
}
