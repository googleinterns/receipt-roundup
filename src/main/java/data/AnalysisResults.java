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
  private final Optional<String> rawText;
  private final ImmutableSet<String> categories;
  private final Optional<String> store;
  private final Optional<Long> timestamp;

  private AnalysisResults(
      Optional<String> rawText, Set<String> categories, Optional<String> store, Optional<Long> timestamp) {
    this.rawText = rawText;
    this.categories = ImmutableSet.copyOf(categories);
    this.store = store;
    this.timestamp = timestamp;
  }

  public Optional<String> getRawText() {
    return rawText;
  }

  public ImmutableSet<String> getCategories() {
    return categories;
  }

  public Optional<String> getStore() {
    return store;
  }

  public Optional<Long> getTimestamp() {
    return timestamp;
  }

  public static class Builder {
    private Optional<String> rawText = Optional.empty();
    private ImmutableSet<String> categories = ImmutableSet.of();
    private Optional<String> store = Optional.empty();
    private Optional<Long> timestamp = Optional.empty();

    public Optional<String> getRawText() {
      return rawText;
    }

    public Builder setRawText(String rawText) {
      this.rawText = Optional.of(rawText);
      return this;
    }

    public Builder setCategories(Set<String> categories) {
      this.categories = ImmutableSet.copyOf(categories);
      return this;
    }

    public Builder setStore(String store) {
      this.store = Optional.of(store);
      return this;
    }

    public Builder setTimestamp(long timestamp) {
      this.timestamp = Optional.of(Long.valueOf(timestamp));
      return this;
    }

    public AnalysisResults build() {
      return new AnalysisResults(rawText, categories, store, timestamp);
    }
  }
}
