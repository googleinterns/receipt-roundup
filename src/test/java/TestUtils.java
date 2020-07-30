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

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.Entity;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import javax.servlet.http.HttpServletRequest;
import org.json.JSONObject;

/** Class that contains helpful methods used for testing. */
public final class TestUtils {
  /** Private constructor to prevent instantiation. */
  private TestUtils() {
    throw new UnsupportedOperationException();
  }

  /** Adds a test receipt to the mock datastore. */
  public static Entity addTestReceipt(DatastoreService datastore, String userId, long timestamp,
      String imageUrl, double price, String store, ImmutableSet<String> categories,
      String rawText) {
    Entity receiptEntity =
        createEntity(userId, timestamp, imageUrl, price, store, categories, rawText);

    datastore.put(receiptEntity);
    return receiptEntity;
  }

  /** Adds multiple receipts to datastore. */
  public static ImmutableSet<Entity> addTestReceipts(DatastoreService datastore) {
    ImmutableSet<Entity> entities =
        ImmutableSet.of(createEntity(/* userId = */ "testID", /* timestamp = */ 1045237591000L,
                            "img/walmart-receipt.jpg", 26.12, "walmart",
                            ImmutableSet.of("candy", "drink"), ""),

            createEntity(/* userId = */ "testID", /* timestamp = */ 1560193140000L,
                "img/contoso-receipt.jpg", 14.51, "contoso",
                ImmutableSet.of("cappuccino", "food"), ""),

            createEntity(/* userId = */ "testID", /* timestamp = */ 1491582960000L,
                "img/restaurant-receipt.jpeg", 29.01, "main street restaurant",
                ImmutableSet.of("food"), ""));
    
    entities.stream().forEach(entity -> datastore.put(entity));

    return entities;
  }

  /** Creates and returns a single Receipt entity. */
  public static Entity createEntity(String userId, long timestamp, String imageUrl, Double price,
      String store, ImmutableSet<String> categories, String rawText) {
    Entity receiptEntity = new Entity("Receipt");
    receiptEntity.setProperty("userId", userId);
    receiptEntity.setProperty("timestamp", timestamp);
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

  /**
   * Parses a json string containing analytics into a hashmap.
   * @param analyticsType The analytics type we want to parse, either store or categories.
   */
  public static HashMap<String, Double> parseAnalytics(String json, String analyticsType)
      throws IOException {
    JSONObject analyticsObject = new JSONObject(json).getJSONObject(analyticsType);

    HashMap<String, Double> analytics = new HashMap<>();

    // Key will either be a store or category, depending on analyticsType.
    for (String key : analyticsObject.keySet()) {
      analytics.put(key, analyticsObject.getDouble(key));
    }

    return analytics;
}
  /* * Removes the unique id property from a receipt entity JSON string, leaving only the receipt
   * properties.
   */
  public static String extractProperties(String json) {
    return json.substring(json.indexOf("propertyMap"));
  }
}
