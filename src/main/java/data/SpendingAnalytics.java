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
import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Collectors;

/** Class that computes and stores user's spending analytics. */
public class SpendingAnalytics {
  private final HashMap<String, Double> storeAnalytics;

  public SpendingAnalytics(ImmutableSet<Entity> allReceipts) {
    storeAnalytics = new HashMap<>();

    for (Entity receipt : allReceipts) {
      String store = (String) receipt.getProperty("store");
      Double price = (Double) receipt.getProperty("price");

      // Don't add to hashmap if either store or price are invalid.
      if (!Strings.isNullOrEmpty(store) && price != null) {
        store = capitalizeFirstLetters(store);

        if (storeAnalytics.containsKey(store)) {
          storeAnalytics.put(store, storeAnalytics.get(store) + price);
        } else {
          storeAnalytics.put(store, price);
        }
      }
    }
  }

  /** Capitalizes the first letter of each word in a string. */
  private String capitalizeFirstLetters(String lowercasedString) {
    return Arrays.stream(lowercasedString.split("\\s+"))
        .map(s -> s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase())
        .collect(Collectors.joining(" "));
  }

  public HashMap<String, Double> getStoreAnalytics() {
    return storeAnalytics;
  }
}