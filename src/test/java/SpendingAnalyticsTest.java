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
import java.lang.Character;
import java.util.HashMap;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SpendingAnalyticsTest {
  private static final double ERROR_THRESHOLD = 0.1;

  // Test Receipt fields.
  private static final String USER_ID = "1";
  private static final long TIMESTAMP = 6292020;
  private static final BlobKey BLOB_KEY = new BlobKey("Test");
  private static final String IMAGE_URL = "img/walmart-receipt.jpg";
  private static final String STORE = "walmart";
  private static final ImmutableSet<String> CATEGORIES =
      ImmutableSet.of("Cappuccino", "Sandwich", "Lunch");
  private static final String RAW_TEXT = "Walmart\nAlways Low Prices At Walmart\n";
  private static final double WALMART_PRICE = 26.12;
  private static final double CONTOSO_PRICE = 14.51;
  private static final double TARGET_PRICE = 29.01;

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
    // Walmart: $26.12, Contoso: $14.51, Target: $29.01

    ImmutableSet<Entity> receipts =
        new ImmutableSet.Builder<Entity>()
            .add(TestUtils.createEntity(USER_ID, TIMESTAMP, BLOB_KEY, IMAGE_URL,
                /* price = */ WALMART_PRICE, /* store = */ "walmart", CATEGORIES, RAW_TEXT))
            .add(TestUtils.createEntity(USER_ID, TIMESTAMP, BLOB_KEY, IMAGE_URL,
                /* price = */ CONTOSO_PRICE, /* store = */ "contoso", CATEGORIES, RAW_TEXT))
            .add(TestUtils.createEntity(USER_ID, TIMESTAMP, BLOB_KEY, IMAGE_URL,
                /* price = */ TARGET_PRICE, /* store = */ "target", CATEGORIES, RAW_TEXT))
            .build();

    SpendingAnalytics analytics = new SpendingAnalytics(receipts);
    HashMap<String, Double> storeAnalytics = analytics.getStoreAnalytics();

    Assert.assertEquals(WALMART_PRICE, storeAnalytics.get("walmart"), ERROR_THRESHOLD);
    Assert.assertEquals(CONTOSO_PRICE, storeAnalytics.get("contoso"), ERROR_THRESHOLD);
    Assert.assertEquals(TARGET_PRICE, storeAnalytics.get("target"), ERROR_THRESHOLD);
  }

  @Test
  public void storeAnalyticsWithDuplicateStoreNames() {
    // Duplicate receipts store added:
    // Walmart: $26.12, Contoso: $14.51, Walmart: $26.12,

    ImmutableSet<Entity> receipts =
        new ImmutableSet.Builder<Entity>()
            .add(TestUtils.createEntity(USER_ID, TIMESTAMP, BLOB_KEY, IMAGE_URL,
                /* price = */ WALMART_PRICE, /* store = */ "walmart", CATEGORIES, RAW_TEXT))
            .add(TestUtils.createEntity(USER_ID, TIMESTAMP, BLOB_KEY, IMAGE_URL,
                /* price = */ CONTOSO_PRICE, /* store = */ "contoso", CATEGORIES, RAW_TEXT))
            .add(TestUtils.createEntity(USER_ID, TIMESTAMP, BLOB_KEY, IMAGE_URL,
                /* price = */ WALMART_PRICE, /* store = */ "walmart", CATEGORIES, RAW_TEXT))
            .build();

    SpendingAnalytics analytics = new SpendingAnalytics(receipts);
    HashMap<String, Double> storeAnalytics = analytics.getStoreAnalytics();

    // Check for no duplicates (2 stores rather than 3).
    Assert.assertEquals(2, storeAnalytics.size());

    // Walmart price should be doubled.
    Assert.assertEquals(WALMART_PRICE * 2, storeAnalytics.get("walmart"), ERROR_THRESHOLD);
    Assert.assertEquals(CONTOSO_PRICE, storeAnalytics.get("contoso"), ERROR_THRESHOLD);
  }

  @Test
  public void nullStoreNameNotIncludedInAnalytics() {
    // null store name should be ignored:
    // Walmart: $26.12, null: $5.13

    ImmutableSet<Entity> receipts =
        new ImmutableSet.Builder<Entity>()
            .add(TestUtils.createEntity(USER_ID, TIMESTAMP, BLOB_KEY, IMAGE_URL,
                /* price = */ 26.12, /* store = */ "walmart", CATEGORIES, RAW_TEXT))
            .add(TestUtils.createEntity(USER_ID, TIMESTAMP, BLOB_KEY, IMAGE_URL,
                /* price = */ 5.13, /* store = */ null, CATEGORIES, RAW_TEXT))
            .build();

    SpendingAnalytics analytics = new SpendingAnalytics(receipts);
    HashMap<String, Double> storeAnalytics = analytics.getStoreAnalytics();

    // Check that only one store is returned and it's the expected one.
    Assert.assertEquals(1, storeAnalytics.size());
    Assert.assertTrue(storeAnalytics.containsKey("walmart"));
  }

  @Test
  public void emptyStoreNameNotIncludedInAnalytics() {
    // empty store name should be ignored:
    // Walmart: $26.12, "": $5.13

    ImmutableSet<Entity> receipts =
        new ImmutableSet.Builder<Entity>()
            .add(TestUtils.createEntity(USER_ID, TIMESTAMP, BLOB_KEY, IMAGE_URL,
                /* price = */ 26.12, /* store = */ "walmart", CATEGORIES, RAW_TEXT))
            .add(TestUtils.createEntity(USER_ID, TIMESTAMP, BLOB_KEY, IMAGE_URL,
                /* price = */ 5.13, /* store = */ "", CATEGORIES, RAW_TEXT))
            .build();

    SpendingAnalytics analytics = new SpendingAnalytics(receipts);
    HashMap<String, Double> storeAnalytics = analytics.getStoreAnalytics();

    // Check that only one store is returned and it's the expected one.
    Assert.assertEquals(1, storeAnalytics.size());
    Assert.assertTrue(storeAnalytics.containsKey("walmart"));
  }

  @Test
  public void storeWithNullPriceNotIncludedInAnalytics() {
    // Stores with null price should be ignored:
    // Contoso: null, Walmart: $26.12

    ImmutableSet<Entity> receipts =
        new ImmutableSet.Builder<Entity>()
            .add(TestUtils.createEntity(USER_ID, TIMESTAMP, BLOB_KEY, IMAGE_URL,
                /* price = */ null, /* store = */ "contoso", CATEGORIES, RAW_TEXT))
            .add(TestUtils.createEntity(USER_ID, TIMESTAMP, BLOB_KEY, IMAGE_URL,
                /* price = */ 26.12, /* store = */ "walmart", CATEGORIES, RAW_TEXT))
            .build();

    SpendingAnalytics analytics = new SpendingAnalytics(receipts);

    // Check that only one store is returned and it's the expected one.
    Assert.assertEquals(1, analytics.getStoreAnalytics().size());
    Assert.assertTrue(analytics.getStoreAnalytics().containsKey("walmart"));
  }

  @Test
  public void categoryAnalyticsWithUniqueCategoryNames() {
    // Receipts categories:
    // candy: $26.12, drink: $26.12, cappuccino: $14.51, food: $14.51
    double storeOneTotal = 26.12;
    double storeTwoTotal = 14.51;

    ImmutableSet<Entity> receipts =
        new ImmutableSet.Builder<Entity>()
            .add(TestUtils.createEntity(USER_ID, TIMESTAMP, BLOB_KEY, IMAGE_URL,
                /* price = */ 26.12, STORE, ImmutableSet.of("candy", "drink"), RAW_TEXT))
            .add(TestUtils.createEntity(USER_ID, TIMESTAMP, BLOB_KEY, IMAGE_URL,
                /* price = */ 14.51, STORE, ImmutableSet.of("cappuccino", "food"), RAW_TEXT))
            .build();

    SpendingAnalytics analytics = new SpendingAnalytics(receipts);
    HashMap<String, Double> categoryAnalytics = analytics.getCategoryAnalytics();

    Assert.assertEquals(storeOneTotal, categoryAnalytics.get("candy"), ERROR_THRESHOLD);
    Assert.assertEquals(storeOneTotal, categoryAnalytics.get("drink"), ERROR_THRESHOLD);
    Assert.assertEquals(storeTwoTotal, categoryAnalytics.get("cappuccino"), ERROR_THRESHOLD);
    Assert.assertEquals(storeTwoTotal, categoryAnalytics.get("food"), ERROR_THRESHOLD);
  }

  @Test
  public void categoryAnalyticsWithDuplicateCategoryNames() {
    // Receipts categories:
    // candy: $26.12, drink: $26.12, candy: $14.51, food: $14.51
    double storeOneTotal = 26.12;
    double storeTwoTotal = 14.51;

    ImmutableSet<Entity> receipts =
        new ImmutableSet.Builder<Entity>()
            .add(TestUtils.createEntity(USER_ID, TIMESTAMP, BLOB_KEY, IMAGE_URL,
                /* price = */ 26.12, STORE, ImmutableSet.of("candy", "drink"), RAW_TEXT))
            .add(TestUtils.createEntity(USER_ID, TIMESTAMP, BLOB_KEY, IMAGE_URL,
                /* price = */ 14.51, STORE, ImmutableSet.of("candy", "food"), RAW_TEXT))
            .build();

    SpendingAnalytics analytics = new SpendingAnalytics(receipts);
    HashMap<String, Double> categoryAnalytics = analytics.getCategoryAnalytics();

    // Check for no duplicates (3 categories rather than 4).
    Assert.assertEquals(3, categoryAnalytics.size());

    // Both "Candy" totals should be combined.
    Assert.assertEquals(
        storeOneTotal + storeTwoTotal, categoryAnalytics.get("candy"), ERROR_THRESHOLD);
    Assert.assertEquals(storeOneTotal, categoryAnalytics.get("drink"), ERROR_THRESHOLD);
    Assert.assertEquals(storeTwoTotal, categoryAnalytics.get("food"), ERROR_THRESHOLD);
  }

  @Test
  public void nullCategoryNotIncludedInAnalytics() {
    // null category should be ignored:
    // candy: $26.12, null: $26.12

    ImmutableSet<Entity> receipts =
        new ImmutableSet.Builder<Entity>()
            .add(TestUtils.createEntity(USER_ID, TIMESTAMP, BLOB_KEY, IMAGE_URL,
                /* price = */ 26.12, STORE, ImmutableSet.of("candy"), RAW_TEXT))
            .add(TestUtils.createEntity(USER_ID, TIMESTAMP, BLOB_KEY, IMAGE_URL,
                /* price = */ 26.12, STORE, /* categories = */ null, RAW_TEXT))
            .build();

    SpendingAnalytics analytics = new SpendingAnalytics(receipts);
    HashMap<String, Double> categoryAnalytics = analytics.getCategoryAnalytics();

    // Check that only one category is returned and it's the expected one.
    Assert.assertEquals(1, categoryAnalytics.size());
    Assert.assertTrue(categoryAnalytics.containsKey("candy"));
  }

  @Test
  public void categoryWithNullPriceNotIncludedInAnalytics() {
    // Category with null price should be ignored:
    // drink: null, candy: $26.12

    ImmutableSet<Entity> receipts =
        new ImmutableSet.Builder<Entity>()
            .add(TestUtils.createEntity(USER_ID, TIMESTAMP, BLOB_KEY, IMAGE_URL,
                /* price = */ null, STORE, ImmutableSet.of("drink"), RAW_TEXT))
            .add(TestUtils.createEntity(USER_ID, TIMESTAMP, BLOB_KEY, IMAGE_URL,
                /* price = */ 26.12, STORE, ImmutableSet.of("candy"), RAW_TEXT))
            .build();

    SpendingAnalytics analytics = new SpendingAnalytics(receipts);
    HashMap<String, Double> categoryAnalytics = analytics.getCategoryAnalytics();

    // Check that only one category is returned and it's the expected one.
    Assert.assertEquals(1, categoryAnalytics.size());
    Assert.assertTrue(categoryAnalytics.containsKey("candy"));
  }
}
