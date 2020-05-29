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

package com.google.cloud.tools.jib.plugins.extension.maven.ownership;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;

public class Configuration {

  public static class Entry {
    @VisibleForTesting String glob = "";
    @VisibleForTesting String ownership = "";

    String getGlob() {
      return glob;
    }

    String getOwnership() {
      return ownership;
    }
  }

  @VisibleForTesting List<Entry> entries = new ArrayList<>();

  List<Entry> getEntries() {
    return entries;
  }
}
