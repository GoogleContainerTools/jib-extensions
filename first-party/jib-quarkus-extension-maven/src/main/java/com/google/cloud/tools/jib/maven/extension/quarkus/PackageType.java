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

package com.google.cloud.tools.jib.maven.extension.quarkus;

import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import java.util.Arrays;
import java.util.Optional;

public enum PackageType {
  LEGACY("legacy-jar"),
  FAST("fast-jar");

  private final String name;

  PackageType(String name) {
    this.name = name;
  }

  /**
   * Retrieves package type by the config property value.
   *
   * @param name name of the package type
   * @return enum of the package type
   * @throws JibPluginExtensionException if there is an unknown package type value given to the
   *     extension config
   */
  public static PackageType getPackageTypeByName(String name) throws JibPluginExtensionException {
    Optional<PackageType> packageTypeEnum =
        Arrays.stream(values()).filter(el -> name.equals(el.name)).findFirst();
    if (packageTypeEnum.isPresent()) {
      return packageTypeEnum.get();
    }
    throw new JibPluginExtensionException(
        JibQuarkusExtension.class, "Unknown packageType, possible values: legacy-jar, fast-jar");
  }
}
