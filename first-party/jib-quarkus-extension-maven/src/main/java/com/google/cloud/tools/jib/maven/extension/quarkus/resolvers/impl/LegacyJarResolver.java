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
import java.util.Collections;
import java.util.List;
import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;

public class LegacyJarResolver implements JarResolver {

  @Override
  public Path getPathToLocalJar(MavenProject project) throws JibPluginExtensionException {
    Build build = project.getBuild();
    Path outputDirectory = Paths.get(build.getDirectory());
    Path jar = outputDirectory.resolve(build.getFinalName() + "-runner.jar");

    if (!Files.isRegularFile(jar)) {
      throw new JibPluginExtensionException(
          JibQuarkusExtension.class,
          jar
              + " doesn't exist; did you run the Quarkus Maven plugin "
              + "('compile' and 'quarkus:build' Maven goals)?");
    }
    return jar;
  }

  @Override
  public List<Path> getPathsToDependencies(MavenProject project) {
    return Collections.singletonList(Paths.get(project.getBuild().getDirectory()).resolve("lib"));
  }

  @Override
  public AbsoluteUnixPath getPathToJarInContainer(AbsoluteUnixPath appRoot) {
    return appRoot.resolve("app.jar");
  }
}
