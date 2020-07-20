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

import com.google.appengine.api.blobstore.BlobKey;
import com.google.common.collect.ImmutableSet;

/** Class to represent a receipt and its properties. */
public class Receipt {
  private final long id;
  private final String userId;
  private final long timestamp;
  private final BlobKey blobKey;
  private final String imageUrl;
  private final double price;
  private final String store;
  private final ImmutableSet<String> categories;
  private final String rawText;

  public Receipt(long id, String userId, long timestamp, BlobKey blobKey, String imageUrl,
      double price, String store, ImmutableSet<String> categories, String rawText) {
    this.id = id;
    this.userId = userId;
    this.timestamp = timestamp;
    this.blobKey = blobKey;
    this.imageUrl = imageUrl;
    this.price = price;
    this.store = store;
    this.categories = ImmutableSet.copyOf(categories); // creates a deep copy
    this.rawText = rawText;
  }

  public long getId() {
    return id;
  }

  public double getPrice() {
    return price;
  }
}