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

import com.google.appengine.api.datastore.Entity;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Collectors;

/** Class that computes and stores user's spending analytics. */
public class SpendingAnalytics {
  private final HashMap<String, Double> storeAnalytics;
  private final HashMap<String, Double> categoryAnalytics;

  public SpendingAnalytics(ImmutableSet<Entity> allReceipts) {
    storeAnalytics = new HashMap<>();
    categoryAnalytics = new HashMap<>();

    for (Entity receipt : allReceipts) {
      updateStoreAnalytics(receipt, storeAnalytics);

      updateCategoryAnalytics(receipt, categoryAnalytics);
    }
  }

  /** Updates storeAnalytics hashmap with store info from the passed in receipt. */
  private void updateStoreAnalytics(Entity receipt, HashMap<String, Double> storeAnalytics) {
    String store = (String) receipt.getProperty("store");
    Double price = (Double) receipt.getProperty("price");

    // Don't add to hashmap if either store or price are invalid.
    if (Strings.isNullOrEmpty(store) || price == null) {
      return;
    }

    // Either update the existing entry or add a new one if it doesn't exist.
    if (storeAnalytics.containsKey(store)) {
      storeAnalytics.put(store, storeAnalytics.get(store) + price);
    } else {
      storeAnalytics.put(store, price);
    }
  }

  /** Updates categoryAnalytics hashmap with category info from the passed in receipt. */
  private void updateCategoryAnalytics(Entity receipt, HashMap<String, Double> categoryAnalytics) {
    Double price = (Double) receipt.getProperty("price");

    // Don't add any categories to hashmap if price is invalid.
    if (price == null) {
      return;
    }

    ImmutableSet<String> categories;

    // If categories is null for a particular receipt, just skip it and continue.
    try {
      categories = ImmutableSet.copyOf((ArrayList) receipt.getProperty("categories"));
    } catch (NullPointerException exception) {
      return;
    }

    for (String category : categories) {
      // Either update the existing entry or add a new one if it doesn't exist.
      if (categoryAnalytics.containsKey(category)) {
        categoryAnalytics.put(category, categoryAnalytics.get(category) + price);
      } else {
        categoryAnalytics.put(category, price);
      }
    }
  }

  public HashMap<String, Double> getStoreAnalytics() {
    return storeAnalytics;
  }

  public HashMap<String, Double> getCategoryAnalytics() {
    return categoryAnalytics;
  }
}