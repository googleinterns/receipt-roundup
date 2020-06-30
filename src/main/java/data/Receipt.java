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
import java.util.Set;

/* Class to represent a receipt and its properties.*/
public class Receipt {
  private long id;
  private long userId;
  private long timestamp;
  private BlobKey blobkey;
  private String imageURL;
  private double price;
  private String store;
  private String label;
  private Set<String> categories;
  private String rawText;

  public Receipt(long id, long userId, long timestamp, BlobKey blobkey, String imageURL,
      double price, String store, String label, Set<String> categories, String rawText) {
    this.id = id;
    this.userId = userId;
    this.timestamp = timestamp;
    this.blobkey = blobkey;
    this.imageURL = imageURL;
    this.price = price;
    this.store = store;
    this.label = label;
    this.categories = categories;
    this.rawText = rawText;
  }
}