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

package com.google.sps.servlets;

import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;


/** Class that contains helpful methods used for testing. */
public final class TestUtils {
  // Private constructor to prevent instantiation.
  private TestUtils() {
    throw new UnsupportedOperationException();
  }

  public static long addReceiptToMockDatastore(DatastoreService datastore) throws IOException {
    // Set entity fields.
    Entity receiptEntity = new Entity("Receipt");
    receiptEntity.setProperty("userId", 1);
    receiptEntity.setProperty("timestamp", 6292020);
    receiptEntity.setProperty("blobkey", new BlobKey("Test"));
    receiptEntity.setProperty("imageUrl", "img/walmart-receipt.jpg");
    receiptEntity.setProperty("price", 26.12);
    receiptEntity.setProperty("store", "Walmart");
    receiptEntity.setProperty("label", "test");
    receiptEntity.setProperty("categories", new HashSet<>(Arrays.asList("Cappuccino", "Sandwich", "Lunch")));
    receiptEntity.setProperty("rawText", "");

    // Add receipt to datastore and return ID.
    Key receiptKey = datastore.put(receiptEntity);
    return receiptKey.getId();
  }
}