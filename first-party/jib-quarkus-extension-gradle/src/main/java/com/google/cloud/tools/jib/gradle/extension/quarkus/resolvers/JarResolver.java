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

package com.google.cloud.tools.jib.gradle.extension.quarkus.resolvers;

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import java.nio.file.Path;
import java.util.List;
import org.gradle.api.Project;

public interface JarResolver {

  /**
   * Retrieves path to Jar in local environment.
   *
   * @param project Gradle project for which this extension is run
   * @return path to jar in build directory
   * @throws JibPluginExtensionException if there is no jar
   */
  Path getPathToLocalJar(Project project) throws JibPluginExtensionException;

  /**
   * Retrieves a list of dependencies that need to be packed together with jar.
   *
   * @param project Gradle project for which this extension is run
   * @return list of paths to dependencies
   */
  List<Path> getPathsToDependencies(Project project);

  /**
   * Retrieves a path where jar will be located in the container.
   *
   * @param appRoot path root where application will reside in container
   * @return path to jar in the container
   */
  AbsoluteUnixPath getPathToJarInContainer(AbsoluteUnixPath appRoot);
}
