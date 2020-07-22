// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.sps.data;

import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import java.util.Set;

/**
 * Object for holding the analysis results served by ReceiptAnalysisServlet.
 */
public class AnalysisResults {
  private final String rawText;
  private final ImmutableSet<String> categories;
  private final Optional<String> store;

  public AnalysisResults(String rawText, Set<String> categories, Optional<String> store) {
    this.rawText = rawText;
    this.categories = ImmutableSet.copyOf(categories);
    this.store = store;
  }

  public String getRawText() {
    return rawText;
  }

  public ImmutableSet<String> getCategories() {
    return categories;
  }

  public Optional<String> getStore() {
    return this.store;
  }

  public static class Builder {
    private final String rawText;
    private ImmutableSet<String> categories;
    private Optional<String> store;

    public Builder(String rawText) {
      this.rawText = rawText;
    }

    public String getRawText() {
      return this.rawText;
    }

    public Builder setCategories(Set<String> categories) {
      this.categories = ImmutableSet.copyOf(categories);
      return this;
    }

    public Builder setStore(String store) {
      this.store = Optional.of(store);
      return this;
    }

    public AnalysisResults build() {
      // No logo was detected.
      if (this.store == null) {
        this.store = Optional.empty();
      }

      return new AnalysisResults(this.rawText, this.categories, this.store);
    }
  }
}
