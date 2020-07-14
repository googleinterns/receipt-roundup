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

package com.google.sps;

import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Class that contains helpful methods used for testing. */
public final class TestUtils {

  /** Private constructor to prevent instantiation. */
  private TestUtils() {
    throw new UnsupportedOperationException();
  }

    /* Add receipts to database for testing purposes. */
  public static long[] addTestReceipts(DatastoreService datastore) {
    long[] ids = {
    
        addTestReceipt(datastore, 1, 123, 1045237591000L, new BlobKey("test"), "img/walmart-receipt.jpg", 26.12,
            "walmart", new HashSet<>(Arrays.asList("candy", "drink", "personal")), ""),

        addTestReceipt(datastore, 2, 123, 1513103400000L, new BlobKey("test"), "img/canes-receipt.jpg", 32.38,
            "raising cane's chicken fingers", new HashSet<>(Arrays.asList("chicken", "drink", "lunch")),
            ""),

        addTestReceipt(datastore, 3, 123, 1560193140000L, new BlobKey("test"), "img/contoso-receipt.jpg", 14.51,
            "contoso", new HashSet<>(Arrays.asList("cappuccino", "sandwich", "lunch")), ""),

        addTestReceipt(datastore, 4, 123, 1491582960000L, new BlobKey("test"), "img/restaurant-receipt.jpeg",
            29.01, "main street restaurant", new HashSet<>(Arrays.asList("food", "meal", "lunch")), ""),

        addTestReceipt(datastore, 5, 123, 1551461940000L, new BlobKey("test"), "img/target-receipt.jpg", 118.94,
            "target", new HashSet<>(Arrays.asList("disney", "lion", "personal")), ""),

        addTestReceipt(datastore, 5, 123, 1131818640000L, new BlobKey("test"), "img/trader-joes-receipt.jpg", 4.32,
            "trader joe's", new HashSet<>(Arrays.asList("cat", "food", "random")), "")
    };

    return ids;
  }

  /** Adds a test receipt to the mock datastore and returns the id of that entity. */
  public static long addTestReceipt(DatastoreService datastore, long id, long userId, long timestamp, BlobKey blobkey,
      String imageUrl, double price, String store, Set<String> categories, String rawText) {
    Entity receiptEntity = new Entity("Receipt");
    receiptEntity.setProperty("userId", userId);
    receiptEntity.setProperty("timestamp", timestamp);
    receiptEntity.setProperty("blobkey", blobkey);
    receiptEntity.setProperty("imageUrl", imageUrl);
    receiptEntity.setProperty("price", price);
    receiptEntity.setProperty("store", store);
    receiptEntity.setProperty("categories", categories);
    receiptEntity.setProperty("rawText", rawText);

    Key key = datastore.put(receiptEntity);
    return key.getId();
  }
}