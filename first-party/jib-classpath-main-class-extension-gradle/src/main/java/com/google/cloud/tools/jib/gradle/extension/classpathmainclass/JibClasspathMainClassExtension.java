/*
 * Copyright 2021 Google LLC.
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

package com.google.cloud.tools.jib.gradle.extension.classpathmainclass;

import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.gradle.extension.GradleData;
import com.google.cloud.tools.jib.gradle.extension.JibGradlePluginExtension;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import java.util.Map;
import java.util.Optional;

public class JibClasspathMainClassExtension implements JibGradlePluginExtension<Void> {

  @Override
  public Optional<Class<Void>> getExtraConfigType() {
    return Optional.empty();
  }

  @Override
  public ContainerBuildPlan extendContainerBuildPlan(
      ContainerBuildPlan buildPlan,
      Map<String, String> properties,
      Optional<Void> extraConfig,
      GradleData gradleData,
      ExtensionLogger logger)
      throws JibPluginExtensionException {
    Map<String, String> newEnv = buildPlan.getEnvironment();
    newEnv.putAll(
        JibClasspathMainClassExtractor.extractEnvsFromEntrypoint(buildPlan.getEntrypoint()));
    return buildPlan.toBuilder().setEnvironment(newEnv).build();
  }
}
