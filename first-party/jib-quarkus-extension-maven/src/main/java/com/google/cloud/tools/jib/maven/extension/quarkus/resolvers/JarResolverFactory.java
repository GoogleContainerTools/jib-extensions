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

package com.google.cloud.tools.jib.maven.extension.quarkus.resolvers;

import com.google.cloud.tools.jib.maven.extension.quarkus.PackageType;
import com.google.cloud.tools.jib.maven.extension.quarkus.resolvers.impl.FastJarResolver;
import com.google.cloud.tools.jib.maven.extension.quarkus.resolvers.impl.LegacyJarResolver;

public class JarResolverFactory {

  /**
   * Factory that returns a correct jar resolver depending on given package type.
   *
   * @param packageType package type of Quarkus application
   * @return jar resolver for given package type
   */
  public JarResolver getJarResolver(PackageType packageType) {
    switch (packageType) {
      case LEGACY:
        return new LegacyJarResolver();
      case FAST:
        return new FastJarResolver();
      default:
        throw new IllegalArgumentException(
            "Quarkus packaging is set wrong! It has to be either legacy or fast.");
    }
  }
}
