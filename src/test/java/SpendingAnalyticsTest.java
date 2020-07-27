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
                /* price = */ 26.12, /* store = */ "walmart", CATEGORIES, RAW_TEXT))
            .add(TestUtils.createEntity(USER_ID, TIMESTAMP, BLOB_KEY, IMAGE_URL,
                /* price = */ 14.51, /* store = */ "contoso", CATEGORIES, RAW_TEXT))
            .add(TestUtils.createEntity(USER_ID, TIMESTAMP, BLOB_KEY, IMAGE_URL,
                /* price = */ 29.01, /* store = */ "target", CATEGORIES, RAW_TEXT))
            .build();

    SpendingAnalytics analytics = new SpendingAnalytics(receipts);

    Assert.assertEquals(26.12, analytics.getStoreAnalytics().get("walmart"), ERROR_THRESHOLD);
    Assert.assertEquals(14.51, analytics.getStoreAnalytics().get("contoso"), ERROR_THRESHOLD);
    Assert.assertEquals(29.01, analytics.getStoreAnalytics().get("target"), ERROR_THRESHOLD);
  }

  @Test
  public void storeAnalyticsWithDuplicateStoreNames() {
    // Duplicate receipts store added:
    // Walmart: $26.12, Contoso: $14.51, Walmart: $26.12,

    ImmutableSet<Entity> receipts =
        new ImmutableSet.Builder<Entity>()
            .add(TestUtils.createEntity(USER_ID, TIMESTAMP, BLOB_KEY, IMAGE_URL,
                /* price = */ 26.12, /* store = */ "walmart", CATEGORIES, RAW_TEXT))
            .add(TestUtils.createEntity(USER_ID, TIMESTAMP, BLOB_KEY, IMAGE_URL,
                /* price = */ 14.51, /* store = */ "contoso", CATEGORIES, RAW_TEXT))
            .add(TestUtils.createEntity(USER_ID, TIMESTAMP, BLOB_KEY, IMAGE_URL,
                /* price = */ 26.12, /* store = */ "walmart", CATEGORIES, RAW_TEXT))
            .build();

    SpendingAnalytics analytics = new SpendingAnalytics(receipts);

    // Check for no duplicates (2 stores rather than 3).
    Assert.assertEquals(2, analytics.getStoreAnalytics().size());

    Assert.assertEquals(52.24, analytics.getStoreAnalytics().get("walmart"), ERROR_THRESHOLD);
    Assert.assertEquals(14.51, analytics.getStoreAnalytics().get("contoso"), ERROR_THRESHOLD);
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

    // Check that only one store is returned and it's the expected one.
    Assert.assertEquals(1, analytics.getStoreAnalytics().size());
    Assert.assertTrue(analytics.getStoreAnalytics().containsKey("walmart"));
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

    // Check that only one store is returned and it's the expected one.
    Assert.assertEquals(1, analytics.getStoreAnalytics().size());
    Assert.assertTrue(analytics.getStoreAnalytics().containsKey("walmart"));
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

    ImmutableSet<Entity> receipts =
        new ImmutableSet.Builder<Entity>()
            .add(TestUtils.createEntity(USER_ID, TIMESTAMP, BLOB_KEY, IMAGE_URL,
                /* price = */ 26.12, STORE, ImmutableSet.of("candy", "drink"), RAW_TEXT))
            .add(TestUtils.createEntity(USER_ID, TIMESTAMP, BLOB_KEY, IMAGE_URL,
                /* price = */ 14.51, STORE, ImmutableSet.of("cappuccino", "food"), RAW_TEXT))
            .build();

    SpendingAnalytics analytics = new SpendingAnalytics(receipts);

    Assert.assertEquals(26.12, analytics.getCategoryAnalytics().get("candy"), ERROR_THRESHOLD);
    Assert.assertEquals(26.12, analytics.getCategoryAnalytics().get("drink"), ERROR_THRESHOLD);
    Assert.assertEquals(14.51, analytics.getCategoryAnalytics().get("cappuccino"), ERROR_THRESHOLD);
    Assert.assertEquals(14.51, analytics.getCategoryAnalytics().get("food"), ERROR_THRESHOLD);
  }

  @Test
  public void categoryAnalyticsWithDuplicateCategoryNames() {
    // Receipts categories:
    // candy: $26.12, drink: $26.12, candy: $14.51, food: $14.51

    ImmutableSet<Entity> receipts =
        new ImmutableSet.Builder<Entity>()
            .add(TestUtils.createEntity(USER_ID, TIMESTAMP, BLOB_KEY, IMAGE_URL,
                /* price = */ 26.12, STORE, ImmutableSet.of("candy", "drink"), RAW_TEXT))
            .add(TestUtils.createEntity(USER_ID, TIMESTAMP, BLOB_KEY, IMAGE_URL,
                /* price = */ 14.51, STORE, ImmutableSet.of("candy", "food"), RAW_TEXT))
            .build();

    SpendingAnalytics analytics = new SpendingAnalytics(receipts);

    // Check for no duplicates (3 categories rather than 4).
    Assert.assertEquals(3, analytics.getCategoryAnalytics().size());

    // Both "Candy" totals should be combined.
    Assert.assertEquals(40.63, analytics.getCategoryAnalytics().get("candy"), ERROR_THRESHOLD);
    Assert.assertEquals(26.12, analytics.getCategoryAnalytics().get("drink"), ERROR_THRESHOLD);
    Assert.assertEquals(14.51, analytics.getCategoryAnalytics().get("food"), ERROR_THRESHOLD);
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
                /* price = */ 26.12, STORE,
                /* categories = */ null, RAW_TEXT))
            .build();

    SpendingAnalytics analytics = new SpendingAnalytics(receipts);

    // Check that only one category is returned and it's the expected one.
    Assert.assertEquals(1, analytics.getCategoryAnalytics().size());
    Assert.assertTrue(analytics.getCategoryAnalytics().containsKey("candy"));
  }

  @Test
  public void categoryWithNullPriceNotIncludedInAnalytics() {
    // Category with null price should be ignored:
    // drink: null, candy: $26.12

    ImmutableSet<Entity> receipts =
        new ImmutableSet.Builder<Entity>()
            .add(TestUtils.createEntity(USER_ID, TIMESTAMP, BLOB_KEY, IMAGE_URL, /* price = */ null,
                STORE, ImmutableSet.of("drink"), RAW_TEXT))
            .add(TestUtils.createEntity(USER_ID, TIMESTAMP, BLOB_KEY, IMAGE_URL,
                /* price = */ 26.12, STORE, ImmutableSet.of("candy"), RAW_TEXT))
            .build();

    SpendingAnalytics analytics = new SpendingAnalytics(receipts);

    // Check that only one category is returned and it's the expected one.
    Assert.assertEquals(1, analytics.getCategoryAnalytics().size());
    Assert.assertTrue(analytics.getCategoryAnalytics().containsKey("candy"));
  }
}
