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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

public class JibClasspathMainClassExtractor {

  private JibClasspathMainClassExtractor() {}

  private static final List<String> classpathOptions =
      Arrays.asList("-cp", "-classpath", "--class-path");

  static Map<String, String> extractEnvsFromEntrypoint(@Nullable List<String> entrypoint) {
    if (entrypoint == null || entrypoint.size() <= 3 || !"java".equals(entrypoint.get(0))) {
      return new HashMap<>();
    }

    String classpathString = null;
    for (int i = 1; i < entrypoint.size() - 2; i++) {
      if (classpathOptions.contains(entrypoint.get(i))) {
        classpathString = entrypoint.get(i + 1);
        break;
      }
    }
    if (classpathString == null) {
      return new HashMap<>();
    }

    String mainClass = entrypoint.get(entrypoint.size() - 1);
    Map<String, String> envs = new HashMap<>();
    envs.put("JIB_JAVA_CLASSPATH", classpathString);
    envs.put("JIB_JAVA_MAIN_CLASS", mainClass);
    return envs;
  }
}
