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
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
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
  // Test Receipt fields.
  private static final String USER_ID = "1";
  private static final long TIMESTAMP = 6292020;
  private static final String IMAGE_URL = "img/walmart-receipt.jpg";
  private static final ImmutableSet<String> CATEGORIES =
      ImmutableSet.of("Cappuccino", "Sandwich", "Lunch");
  private static final String RAW_TEXT = "Walmart\nAlways Low Prices At Walmart\n";

  // Local Datastore
  private final LocalServiceTestHelper helper =
      new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig()).setEnvIsLoggedIn(true);

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
        /* price = */ 26.12, /* store = */ "walmart", CATEGORIES, RAW_TEXT);
    TestUtils.addTestReceipt(datastore, USER_ID, TIMESTAMP, IMAGE_URL,
        /* price = */ 14.51, /* store = */ "contoso", CATEGORIES, RAW_TEXT);
    TestUtils.addTestReceipt(datastore, USER_ID, TIMESTAMP, IMAGE_URL,
        /* price = */ 29.01, /* store = */ "target", CATEGORIES, RAW_TEXT);

    servlet.doGet(request, response);
    writer.flush();

    // Make sure all stores returned in HashMap.
    HashMap<String, Double> storeAnalytics =
        new ObjectMapper().readValue(stringWriter.toString(), HashMap.class);
    Assert.assertEquals(3, storeAnalytics.size());
    Assert.assertTrue(storeAnalytics.containsKey("walmart"));
    Assert.assertTrue(storeAnalytics.containsKey("contoso"));
    Assert.assertTrue(storeAnalytics.containsKey("target"));
  }

  @Test
  public void doGetWithNoReceiptsInDatastore() throws IOException {
    servlet.doGet(request, response);
    writer.flush();

    // Make sure empty HashMap is returned.
    HashMap<String, Double> storeAnalytics =
        new ObjectMapper().readValue(stringWriter.toString(), HashMap.class);
    Assert.assertTrue(storeAnalytics.isEmpty());
  }
}