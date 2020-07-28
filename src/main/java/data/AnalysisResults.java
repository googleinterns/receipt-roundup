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
  private final Optional<Long> timestamp;
  private final Optional<Double> price;

  public AnalysisResults(String rawText, Set<String> categories, Optional<String> store,
      Optional<Long> timestamp, Optional<Double> price) {
    this.rawText = rawText;
    this.categories = ImmutableSet.copyOf(categories);
    this.store = store;
    this.timestamp = timestamp;
    this.price = price;
  }

  public String getRawText() {
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

  public Optional<Double> getPrice() {
    return price;
  }

  public static class Builder {
    private final String rawText;
    private ImmutableSet<String> categories;
    private Optional<String> store;
    private Optional<Long> timestamp = Optional.empty();
    private Optional<Double> price = Optional.empty();

    public Builder(String rawText) {
      this.rawText = rawText;
    }

    public String getRawText() {
      return rawText;
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

    public Builder setPrice(double price) {
      this.price = Optional.of(Double.valueOf(price));
      return this;
    }

    public AnalysisResults build() {
      // No logo was detected.
      if (store == null) {
        store = Optional.empty();
      }

      return new AnalysisResults(rawText, categories, store, timestamp, price);
    }
  }
}
