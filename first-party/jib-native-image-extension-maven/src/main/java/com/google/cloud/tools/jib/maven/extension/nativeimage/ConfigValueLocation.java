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

package com.google.cloud.tools.jib.maven.extension.nativeimage;

/**
 * Represents a location of a plugin configuration value in POM. For example, for the {@code
 * location-in-question} value in the following POM,
 *
 * <pre>{@code
 * <groupId>org.apache.maven.plugins</groupId>
 * <artifactId>maven-shade-plugin</artifactId>
 * <configuration>
 *   <archive>
 *     <manifest>
 *       <mainClass>location-in-question</mainClass>
 *     </manifest>
 *   </archive>
 * </configuration>
 *
 * <ul>
 *   <li>"plugin ID" is {@code org.apache.maven.plugins:maven-shade-plugin}.
 *   <li>"value container" is {@code <configuration>} (as opposed to {@code <executions>}).
 *   <li>"dom path" is {@code archive/manifest/mainclass}.
 * </ul>
 * }</pre>
 */
class ConfigValueLocation {

  static enum ValueContainer {
    CONFIGURATION,
    EXECUTIONS
  }

  final String pluginId;
  final ValueContainer valueContainer;
  final String[] domPath;

  ConfigValueLocation(String pluginId, ValueContainer valueContainer, String[] domPath) {
    this.pluginId = pluginId;
    this.valueContainer = valueContainer;
    this.domPath = domPath;
  }
}
