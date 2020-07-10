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

/** Class that contains helpful methods used for testing. */
public final class TestUtils {
  private static final long USER_ID = 1;
  private static final long TIMESTAMP = 6292020;
  private static final BlobKey BLOB_KEY = new BlobKey("Test");
  private static final String IMAGE_URL = "img/walmart-receipt.jpg";
  private static final double PRICE = 26.12;
  private static final String STORE = "Walmart";
  private static final String LABEL = "test";
  private static final HashSet<String> CATEGORIES = new HashSet<>(Arrays.asList("Cappuccino", "Sandwich", "Lunch"));
  private static final String RAW_TEXT = "Walmart\nAlways Low Prices At Walmart\n";

  /** Private constructor to prevent instantiation. */
  private TestUtils() {
    throw new UnsupportedOperationException();
  }

  /** Adds a test receipt to the mock datastore and returns the id of that entity. */
  public static long addReceiptToMockDatastore(DatastoreService datastore) throws IOException {
    // Set entity fields.
    Entity receiptEntity = new Entity("Receipt");
    receiptEntity.setProperty("userId", USER_ID);
    receiptEntity.setProperty("timestamp", TIMESTAMP);
    receiptEntity.setProperty("blobkey", BLOB_KEY);
    receiptEntity.setProperty("imageUrl", IMAGE_URL);
    receiptEntity.setProperty("price", PRICE);
    receiptEntity.setProperty("store", STORE);
    receiptEntity.setProperty("label", LABEL);
    receiptEntity.setProperty("categories", CATEGORIES);
    receiptEntity.setProperty("rawText", RAW_TEXT);

    // Add receipt to datastore and return ID.
    Key key = datastore.put(receiptEntity);
    return key.getId();
  }
}