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

package com.google.cloud.tools.jib.gradle.extension.quarkus.resolvers.impl;

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.gradle.extension.quarkus.JibQuarkusExtension;
import com.google.cloud.tools.jib.gradle.extension.quarkus.resolvers.JarResolver;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.jvm.tasks.Jar;

public class LegacyJarResolver implements JarResolver {

  @Override
  public Path getPathToLocalJar(Project project) throws JibPluginExtensionException {
    Path buildDir = project.getBuildDir().toPath();
    Jar jarTask = (Jar) project.getTasks().findByName("jar");
    String jarName = jarTask.getArchiveFile().get().getAsFile().getName();
    Path jar = buildDir.resolve(jarName.replaceAll("\\.jar$", "-runner.jar"));

    if (!Files.isRegularFile(jar)) {
      throw new JibPluginExtensionException(
          JibQuarkusExtension.class,
          jar + " doesn't exist; did you run the Quarkus Gradle plugin ('quarkusBuild' task)?");
    }

    return jar;
  }

  @Override
  public List<Path> getPathsToDependencies(Project project) {
    return Collections.singletonList(project.getBuildDir().toPath().resolve("lib"));
  }

  @Override
  public AbsoluteUnixPath getPathToJarInContainer(AbsoluteUnixPath appRoot) {
    return appRoot.resolve("app.jar");
  }
}
