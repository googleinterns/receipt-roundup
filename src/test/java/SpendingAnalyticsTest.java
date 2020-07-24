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
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.common.collect.ImmutableSet;
import com.google.sps.data.SpendingAnalytics;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SpendingAnalyticsTest {
  private static final double ERROR_THRESHOLD = 0.1;

  // Expected values.
  private static final double WALMART_TOTAL_SPENT = 26.12;
  private static final double CONTOSO_TOTAL_SPENT = 14.51;
  private static final double MAIN_STREET_RESTAURANT_SPENT = 29.01;

  // Test Receipt fields.
  private static final String USER_ID = "1";
  private static final long TIMESTAMP = 6292020;
  private static final BlobKey BLOB_KEY = new BlobKey("Test");
  private static final String IMAGE_URL = "img/walmart-receipt.jpg";
  private static final double PRICE = 26.12;
  private static final String STORE = "Walmart";
  private static final ImmutableSet<String> CATEGORIES =
      ImmutableSet.of("Cappuccino", "Sandwich", "Lunch");
  private static final String RAW_TEXT = "Walmart\nAlways Low Prices At Walmart\n";

  private final LocalServiceTestHelper helper =
      new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig());

  @Before
  public void setUp() {
    helper.setUp();
  }

  @After
  public void tearDown() {
    helper.tearDown();
  }

  @Test
  public void storeAnalyticsWithUniqueStoreNames() {
    // Only one receipt for each store added:
    // Walmart: $26.12, Contoso: $14.51, Main Street Restaurant: $29.01

    ImmutableSet<Entity> receipts = TestUtils.createEntities();

    SpendingAnalytics analytics = new SpendingAnalytics(receipts);

    Assert.assertEquals(
        WALMART_TOTAL_SPENT, analytics.getStoreAnalytics().get("Walmart"), ERROR_THRESHOLD);
    Assert.assertEquals(
        CONTOSO_TOTAL_SPENT, analytics.getStoreAnalytics().get("Contoso"), ERROR_THRESHOLD);
    Assert.assertEquals(MAIN_STREET_RESTAURANT_SPENT,
        analytics.getStoreAnalytics().get("Main Street Restaurant"), ERROR_THRESHOLD);
  }

  @Test
  public void storeAnalyticsWithDuplicateStoreNames() {
    // Duplicate receipts for each store added:
    // Walmart: $26.12, Contoso: $14.51, Main Street Restaurant: $29.01, Walmart: $26.12,
    // Contoso: $14.51, Main Street Restaurant: $29.01

    ImmutableSet<Entity> receipts = new ImmutableSet.Builder<Entity>()
                                        .addAll(TestUtils.createEntities())
                                        .addAll(TestUtils.createEntities())
                                        .build();

    SpendingAnalytics analytics = new SpendingAnalytics(receipts);

    // Check for no duplicates (3 stores rather than 6).
    Assert.assertEquals(3, analytics.getStoreAnalytics().size());

    // Each total should be doubled since two of the same total was added for each store.
    Assert.assertEquals(
        WALMART_TOTAL_SPENT * 2, analytics.getStoreAnalytics().get("Walmart"), ERROR_THRESHOLD);
    Assert.assertEquals(
        CONTOSO_TOTAL_SPENT * 2, analytics.getStoreAnalytics().get("Contoso"), ERROR_THRESHOLD);
    Assert.assertEquals(MAIN_STREET_RESTAURANT_SPENT * 2,
        analytics.getStoreAnalytics().get("Main Street Restaurant"), ERROR_THRESHOLD);
  }

  @Test
  public void nullStoreNameNotIncludedInAnalytics() {
    // null store name should be ignored:
    // null: $26.12, Walmart: $26.12

    ImmutableSet<Entity> receipts = new ImmutableSet.Builder<Entity>()
                                        .add(TestUtils.createEntity(USER_ID, TIMESTAMP, BLOB_KEY,
                                            IMAGE_URL, PRICE, STORE, CATEGORIES, RAW_TEXT))
                                        .add(TestUtils.createEntity(USER_ID, TIMESTAMP, BLOB_KEY,
                                            IMAGE_URL, PRICE, /*store=*/null, CATEGORIES, RAW_TEXT))
                                        .build();

    SpendingAnalytics analytics = new SpendingAnalytics(receipts);

    // Check that only one stores is returned and it's the expected one.
    Assert.assertEquals(1, analytics.getStoreAnalytics().size());
    Assert.assertTrue(analytics.getStoreAnalytics().containsKey("Walmart"));
  }

  @Test
  public void emptyStoreNameNotIncludedInAnalytics() {
    // empty store name should be ignored:
    // "": $26.12, Walmart: $26.12

    ImmutableSet<Entity> receipts = new ImmutableSet.Builder<Entity>()
                                        .add(TestUtils.createEntity(USER_ID, TIMESTAMP, BLOB_KEY,
                                            IMAGE_URL, PRICE, STORE, CATEGORIES, RAW_TEXT))
                                        .add(TestUtils.createEntity(USER_ID, TIMESTAMP, BLOB_KEY,
                                            IMAGE_URL, PRICE, /*store=*/"", CATEGORIES, RAW_TEXT))
                                        .build();

    SpendingAnalytics analytics = new SpendingAnalytics(receipts);

    // Check that only one store is returned and it's the expected one.
    Assert.assertEquals(1, analytics.getStoreAnalytics().size());
    Assert.assertTrue(analytics.getStoreAnalytics().containsKey("Walmart"));
  }

  @Test
  public void storeWithNullPriceNotIncludedInAnalytics() {
    // Stores with null price should be ignored:
    // Contoso: null, Walmart: $26.12

    ImmutableSet<Entity> receipts =
        new ImmutableSet.Builder<Entity>()
            .add(TestUtils.createEntity(USER_ID, TIMESTAMP, BLOB_KEY, IMAGE_URL, /*price=*/null,
                "CONTOSO", CATEGORIES, RAW_TEXT))
            .add(TestUtils.createEntity(
                USER_ID, TIMESTAMP, BLOB_KEY, IMAGE_URL, PRICE, STORE, CATEGORIES, RAW_TEXT))
            .build();

    SpendingAnalytics analytics = new SpendingAnalytics(receipts);

    // Check that only one store is returned and it's the expected one.
    Assert.assertEquals(1, analytics.getStoreAnalytics().size());
    Assert.assertTrue(analytics.getStoreAnalytics().containsKey("Walmart"));
  }
}
