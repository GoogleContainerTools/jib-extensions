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

package com.google.cloud.tools.jib.maven.extension.classpathmainclass;

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import java.util.Arrays;
import java.util.Optional;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link JibClasspathMainClassExtension}. */
@RunWith(JUnitParamsRunner.class)
public class JibClasspathMainClassExtensionTest {

  private String[] classpaths() {
    return new String[] {"-cp", "-classpath", "--class-path"};
  }

  @Test
  @Parameters(method = "classpaths")
  public void testExtractClasspathMainClass(String classpathFlag)
      throws JibPluginExtensionException {
    ContainerBuildPlan buildPlan =
        ContainerBuildPlan.builder()
            .setEntrypoint(Arrays.asList("java", classpathFlag, "testcp", "main.class"))
            .build();
    ContainerBuildPlan newPlan =
        new JibClasspathMainClassExtension()
            .extendContainerBuildPlan(buildPlan, null, Optional.empty(), null, null);
    assertThat(newPlan.getEnvironment())
        .containsExactly("JIB_JAVA_MAIN_CLASS", "main.class", "JIB_JAVA_CLASSPATH", "testcp");
  }

  @Test
  public void testExtractClasspathMainClass_nullEntrypoint() throws JibPluginExtensionException {
    ContainerBuildPlan buildPlan = ContainerBuildPlan.builder().build();
    ContainerBuildPlan newPlan =
        new JibClasspathMainClassExtension()
            .extendContainerBuildPlan(buildPlan, null, Optional.empty(), null, null);
    assertThat(newPlan.getEnvironment()).isEmpty();
  }

  @Test
  public void testExtractClasspathMainClass_firstCommandNotJava()
      throws JibPluginExtensionException {
    ContainerBuildPlan buildPlan =
        ContainerBuildPlan.builder()
            .setEntrypoint(Arrays.asList("/usr/local/java", "-cp", "testcp", "main.class"))
            .build();
    ContainerBuildPlan newPlan =
        new JibClasspathMainClassExtension()
            .extendContainerBuildPlan(buildPlan, null, Optional.empty(), null, null);
    assertThat(newPlan.getEnvironment()).isEmpty();
  }

  @Test
  public void testExtractClasspathMainClass_lengthLessThan4() throws JibPluginExtensionException {
    ContainerBuildPlan buildPlan =
        ContainerBuildPlan.builder().setEntrypoint(Arrays.asList("java", "-cp", "testcp")).build();
    ContainerBuildPlan newPlan =
        new JibClasspathMainClassExtension()
            .extendContainerBuildPlan(buildPlan, null, Optional.empty(), null, null);
    assertThat(newPlan.getEnvironment()).isEmpty();
  }

  @Test
  public void testExtractClasspathMainClass_noClasspathOption() throws JibPluginExtensionException {
    ContainerBuildPlan buildPlan =
        ContainerBuildPlan.builder()
            .setEntrypoint(Arrays.asList("java", "-foo", "-bar", "main.class"))
            .build();
    ContainerBuildPlan newPlan =
        new JibClasspathMainClassExtension()
            .extendContainerBuildPlan(buildPlan, null, Optional.empty(), null, null);
    assertThat(newPlan.getEnvironment()).isEmpty();
  }
}
