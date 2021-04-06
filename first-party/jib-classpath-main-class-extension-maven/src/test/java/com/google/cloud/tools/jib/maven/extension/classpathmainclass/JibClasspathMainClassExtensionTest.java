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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import java.util.Arrays;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link JibClasspathMainClassExtension}. */
@RunWith(MockitoJUnitRunner.class)
public class JibClasspathMainClassExtensionTest {

  @Mock private ExtensionLogger logger;

  @Test
  public void testExtractClasspathMainClass_happy() throws JibPluginExtensionException {
    ContainerBuildPlan buildPlan =
        ContainerBuildPlan.builder()
            .setEntrypoint(Arrays.asList("java", "-cp", "testcp", "main.class"))
            .build();
    ContainerBuildPlan newPlan =
        new JibClasspathMainClassExtension()
            .extendContainerBuildPlan(buildPlan, null, Optional.empty(), null, logger);
    assertNotSame(buildPlan, newPlan);
    assertEquals("main.class", newPlan.getEnvironment().get("JIB_JAVA_MAIN_CLASS"));
    assertEquals("testcp", newPlan.getEnvironment().get("JIB_JAVA_CLASSPATH"));
  }
}
