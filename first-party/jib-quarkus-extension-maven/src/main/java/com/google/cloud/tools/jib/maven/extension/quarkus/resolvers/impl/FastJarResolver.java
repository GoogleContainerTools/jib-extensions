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

package com.google.cloud.tools.jib.maven.extension.quarkus.resolvers.impl;

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.maven.extension.quarkus.JibQuarkusExtension;
import com.google.cloud.tools.jib.maven.extension.quarkus.resolvers.JarResolver;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.apache.maven.project.MavenProject;

public class FastJarResolver implements JarResolver {

  private static final String FAST_JAR_LOCATION = "quarkus-app/quarkus-run.jar";

  @Override
  public Path getPathToLocalJar(MavenProject project) throws JibPluginExtensionException {

    Path buildDir = Paths.get(project.getBuild().getDirectory());
    Path jar = buildDir.resolve(FAST_JAR_LOCATION);

    if (!Files.isRegularFile(jar)) {
      throw new JibPluginExtensionException(
          JibQuarkusExtension.class,
          "quarkus-app/quarkus-run.jar doesn't exist; did you run the Quarkus Maven plugin "
              + "('compile' and 'quarkus:build' Maven goals)?");
    }

    return jar;
  }

  @Override
  public List<Path> getPathsToDependencies(MavenProject project) {
    Path outputPath = Paths.get(project.getBuild().getDirectory());
    return Arrays.asList(
        outputPath.resolve("quarkus-app/lib/main"),
        outputPath.resolve("quarkus-app/lib/boot"),
        outputPath.resolve("quarkus-app/app"),
        outputPath.resolve("quarkus-app/quarkus"));
  }

  @Override
  public AbsoluteUnixPath getPathToJarInContainer(AbsoluteUnixPath appRoot) {
    return appRoot.resolve(FAST_JAR_LOCATION);
  }
}
