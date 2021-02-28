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

package com.google.cloud.tools.jib.maven.extension.quarkus.resolvers;

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.maven.extension.quarkus.JibQuarkusExtension;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FastJarDependencyResolver implements JarDependencyResolver {
  @Override
  public Path verifyJarPresent(Path outputDirectory, String finalName)
      throws JibPluginExtensionException {
    Path jar = outputDirectory.resolve("quarkus-app/quarkus-run.jar");
    if (!Files.isRegularFile(jar)) {
      throw new JibPluginExtensionException(
          JibQuarkusExtension.class,
          "quarkus-app/quarkus-run.jar"
              + " doesn't exist; did you run the Qaurkus Maven plugin "
              + "('compile' and 'quarkus:build' Maven goals)?");
    }
    return jar;
  }

  @Override
  public AbsoluteUnixPath getAppRootJar(AbsoluteUnixPath appRoot) {
    return appRoot.resolve("quarkus-app/quarkus-run.jar");
  }

  @Override
  public List<DependencyDto> getDependencyLayers(Path outputDirectory) {
    List<DependencyDto> returnList = new ArrayList<>();
    // main deps should be at the bottom because the upper 3 layer will change more
    // often
    returnList.add(
        new DependencyDto(outputDirectory.resolve("quarkus-app/lib/main"), Optional.of("main-")));
    returnList.add(
        new DependencyDto(outputDirectory.resolve("quarkus-app/lib/boot"), Optional.of("boot-")));
    returnList.add(
        new DependencyDto(outputDirectory.resolve("quarkus-app/app"), Optional.of("application-")));
    returnList.add(
        new DependencyDto(
            outputDirectory.resolve("quarkus-app/quarkus"), Optional.of("bytecode-")));
    return returnList;
  }
}
