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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.appengine.tools.development.testing.LocalUserServiceTestConfig;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.sps.servlets.SpendingAnalyticsServlet;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public final class SpendingAnalyticsServletTest {
  private static final String AUTHENTICATION_ERROR_MESSAGE =
      "No Authentication: User must be logged in to search receipts.";

  private static final ImmutableMap<String, Double> EXPECTED_STORE_ANALYTICS =
      ImmutableMap.of("walmart", 26.12, "contoso", 14.51, "target", 29.01);
  private static final ImmutableMap<String, Double> EXPECTED_CATEGORY_ANALYTICS =
      ImmutableMap.of("candy", 26.12, "drink", 26.12, "cappuccino", 14.51, "food", 43.52);

  // Test Receipt fields.
  private static final long TIMESTAMP = 6292020;
  private static final String IMAGE_URL = "img/walmart-receipt.jpg";
  private static final String RAW_TEXT = "Walmart\nAlways Low Prices At Walmart\n";

  private static final String DOMAIN_NAME = "gmail.com";
  private static final String USER_EMAIL = "test@gmail.com";
  private static final String USER_ID = "testID";

  // Local Datastore
  private final LocalServiceTestHelper helper =
      new LocalServiceTestHelper(
          new LocalDatastoreServiceTestConfig(), new LocalUserServiceTestConfig())
          .setEnvIsLoggedIn(true)
          .setEnvEmail(USER_EMAIL)
          .setEnvAuthDomain(DOMAIN_NAME)
          .setEnvAttributes(new HashMap(
              ImmutableMap.of("com.google.appengine.api.users.UserService.user_id_key", USER_ID)));

  @Mock private SpendingAnalyticsServlet servlet;
  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;

  private DatastoreService datastore;
  private StringWriter stringWriter;
  private PrintWriter writer;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    helper.setUp();
    datastore = DatastoreServiceFactory.getDatastoreService();

    servlet = new SpendingAnalyticsServlet(datastore);

    stringWriter = new StringWriter();
    writer = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(writer);
  }

  @After
  public void tearDown() {
    helper.tearDown();
  }

  @Test
  public void doGetWithReceiptsInDatastore() throws IOException {
    // Receipts in datastore:
    // Walmart: $26.12, Contoso: $14.51, Target: $29.01

    TestUtils.addTestReceipt(datastore, USER_ID, TIMESTAMP, IMAGE_URL,
        /* price = */ 26.12, /* store = */ "walmart",
        /* categories = */ ImmutableSet.of("candy", "drink"), RAW_TEXT);
    TestUtils.addTestReceipt(datastore, USER_ID, TIMESTAMP, IMAGE_URL,
        /* price = */ 14.51, /* store = */ "contoso",
        /* categories = */ ImmutableSet.of("cappuccino", "food"), RAW_TEXT);
    TestUtils.addTestReceipt(datastore, USER_ID, TIMESTAMP, IMAGE_URL,
        /* price = */ 29.01, /* store = */ "target", /* categories = */ ImmutableSet.of("food"),
        RAW_TEXT);

    servlet.doGet(request, response);
    writer.flush();

    HashMap<String, Double> storeAnalytics =
        TestUtils.parseAnalytics(stringWriter.toString(), "storeAnalytics");
    HashMap<String, Double> categoryAnalytics =
        TestUtils.parseAnalytics(stringWriter.toString(), "categoryAnalytics");

    Assert.assertEquals(EXPECTED_STORE_ANALYTICS, storeAnalytics);
    Assert.assertEquals(EXPECTED_CATEGORY_ANALYTICS, categoryAnalytics);
  }

  @Test
  public void doGetWithNoReceiptsInDatastore() throws IOException {
    servlet.doGet(request, response);
    writer.flush();

    // Make sure empty HashMaps are returned.
    HashMap<String, Double> storeAnalytics =
        TestUtils.parseAnalytics(stringWriter.toString(), "storeAnalytics");
    HashMap<String, Double> categoryAnalytics =
        TestUtils.parseAnalytics(stringWriter.toString(), "categoryAnalytics");

    Assert.assertTrue(storeAnalytics.isEmpty());
    Assert.assertTrue(categoryAnalytics.isEmpty());
  }

  @Test
  public void getAnalyticsWithDifferentUserId() throws IOException {
    // Receipt under the userId: "testId".
    // Walmart: $26.12

    TestUtils.addTestReceipt(datastore, USER_ID, TIMESTAMP, IMAGE_URL,
        /* price = */ 26.12, /* store = */ "walmart",
        /* categories = */ ImmutableSet.of("candy", "drink"), RAW_TEXT);

    // Set userId to one that doesn't have any receipts under it.
    helper
        .setEnvAttributes(new HashMap(ImmutableMap.of(
            "com.google.appengine.api.users.UserService.user_id_key", "wrongUserId")))
        .setUp();

    servlet.doGet(request, response);
    writer.flush();

    // No analytics should be under this userId. Make sure empty HashMaps are returned.
    HashMap<String, Double> storeAnalytics =
        TestUtils.parseAnalytics(stringWriter.toString(), "storeAnalytics");
    HashMap<String, Double> categoryAnalytics =
        TestUtils.parseAnalytics(stringWriter.toString(), "categoryAnalytics");

    Assert.assertTrue(storeAnalytics.isEmpty());
    Assert.assertTrue(categoryAnalytics.isEmpty());
  }

  @Test
  public void checkAuthenticationErrorIsReturned() throws IOException {
    // Will respond with status code 403 since the user is not logged in.

    helper.setEnvIsLoggedIn(false);

    servlet.doGet(request, response);
    writer.flush();

    Assert.assertTrue(stringWriter.toString().contains(AUTHENTICATION_ERROR_MESSAGE));
    verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
  }
}