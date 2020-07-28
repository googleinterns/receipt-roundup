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

import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.Entity;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import javax.servlet.http.HttpServletRequest;

/** Class that contains helpful methods used for testing. */
public final class TestUtils {
  /** Private constructor to prevent instantiation. */
  private TestUtils() {
    throw new UnsupportedOperationException();
  }

  /** Adds a test receipt to the mock datastore. */
  public static Entity addTestReceipt(DatastoreService datastore, String userId, long timestamp,
      BlobKey blobkey, String imageUrl, double price, String store, ImmutableSet<String> categories,
      String rawText) {
    Entity receiptEntity =
        createEntity(userId, timestamp, blobkey, imageUrl, price, store, categories, rawText);

    datastore.put(receiptEntity);
    return receiptEntity;
  }

  /** Adds multiple receipts to datastore. */
  public static ImmutableSet<Entity> addTestReceipts(DatastoreService datastore) {
    ImmutableSet<Entity> entities = ImmutableSet.of(
        createEntity(/* userId = */ "123", /* timestamp = */ 1045237591000L, new BlobKey("test"),
            "img/walmart-receipt.jpg", 26.12, "walmart", ImmutableSet.of("candy", "drink"), ""),

        createEntity(/* userId = */ "123", /* timestamp = */ 1560193140000L, new BlobKey("test"),
            "img/contoso-receipt.jpg", 14.51, "contoso", ImmutableSet.of("cappuccino", "food"), ""),

        createEntity(/* userId = */ "123", /* timestamp = */ 1491582960000L, new BlobKey("test"),
            "img/restaurant-receipt.jpeg", 29.01, "main street restaurant", ImmutableSet.of("food"),
            ""));

    entities.stream().forEach(entity -> datastore.put(entity));

    return entities;
  }

  /** Creates and returns a single Receipt entity. */
  public static Entity createEntity(String userId, long timestamp, BlobKey blobkey, String imageUrl,
      Double price, String store, ImmutableSet<String> categories, String rawText) {
    Entity receiptEntity = new Entity("Receipt");
    receiptEntity.setProperty("userId", userId);
    receiptEntity.setProperty("timestamp", timestamp);
    receiptEntity.setProperty("blobkey", blobkey);
    receiptEntity.setProperty("imageUrl", imageUrl);
    receiptEntity.setProperty("price", price);
    receiptEntity.setProperty("store", store);
    if (categories == null) {
      receiptEntity.setProperty("categories", null);
    } else {
      receiptEntity.setProperty("categories", new ArrayList(categories));
    }
    receiptEntity.setProperty("rawText", rawText);

    return receiptEntity;
  }

  /** Set all necessary parameters that SearchServlet will ask for in a doGet. */
  public static void setSearchServletRequestParameters(HttpServletRequest request,
      String timeZoneId, String categories, String dateRange, String store, String minPrice,
      String maxPrice) {
    when(request.getParameter("isNewLoad")).thenReturn("false");
    when(request.getParameter("timeZoneId")).thenReturn(timeZoneId);
    when(request.getParameter("category")).thenReturn(categories);
    when(request.getParameter("dateRange")).thenReturn(dateRange);
    when(request.getParameter("store")).thenReturn(store);
    when(request.getParameter("min")).thenReturn(minPrice);
    when(request.getParameter("max")).thenReturn(maxPrice);
  }

  /** Parses a string containing store analytics into a hashmap representation. */
  public static HashMap<String, Double> parseStoreAnalytics(String str) throws IOException {
    String stores = str.substring(str.indexOf("{"), str.indexOf("}") + 1);
    stores = stores.substring(stores.lastIndexOf("{"), stores.indexOf("}") + 1);
    return new ObjectMapper().readValue(stores, HashMap.class);
  }

  /** Parses a string containing category analytics into a hashmap representation. */
  public static HashMap<String, Double> parseCategoryAnalytics(String str) throws IOException {
    String categories = str.substring(str.lastIndexOf("{"), str.lastIndexOf("}") + 1);
    categories = categories.substring(categories.indexOf("{"), categories.indexOf("}") + 1);
    return new ObjectMapper().readValue(categories, HashMap.class);
  }
}