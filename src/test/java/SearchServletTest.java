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
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.sps.servlets.SearchServlet;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;

@PowerMockIgnore("jdk.internal.reflect.*")
@RunWith(PowerMockRunner.class)
public final class SearchServletTest {
  private static final String NULL_EXCEPTION_MESSAGE =
      "Null Field: Receipt unable to be queried at this time, please try again.";
  private static final String NUMBER_EXCEPTION_MESSAGE =
      "Invalid Price: Receipt unable to be queried at this time, please try again.";
  private static final String PARSE_EXCEPTION_MESSAGE =
      "Dates Unparseable: Receipt unable to be queried at this time, please try again.";

   // Values for a valid test.
  private static final String CST_TIMEZONE_ID = "America/Chicago";
  private static final String CATEGORIES = "drink";
  private static final String DATE_RANGE = "February 1, 2003 - February 28, 2003";
  private static final String STORE = "walmart";
  private static final String MIN_PRICE = "5.00";
  private static final String MAX_PRICE = "30.00";

  // Local Datastore
  private final LocalServiceTestHelper helper =
      new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig())
          .setEnvIsAdmin(true)
          .setEnvIsLoggedIn(true);

  @Mock private SearchServlet servlet;
  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;

  private DatastoreService datastore;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    helper.setUp();
    datastore = DatastoreServiceFactory.getDatastoreService();

    servlet = new SearchServlet(datastore);
  }

  @After
  public void tearDown() {
    helper.tearDown();
  }

  @Test
  public void queryWithAllFieldsFilled() throws IOException {
    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(writer);

    // Add mock receipts to datastore.
    long[] ids = TestUtils.addTestReceipts(datastore);

    // Perform doGet - this should retrieve the receipt.
    when(request.getParameter("timeZoneId")).thenReturn(CST_TIMEZONE_ID);
    when(request.getParameter("categories")).thenReturn(CATEGORIES);
    when(request.getParameter("dateRange")).thenReturn(DATE_RANGE);
    when(request.getParameter("store")).thenReturn(STORE);
    when(request.getParameter("min")).thenReturn(MIN_PRICE);
    when(request.getParameter("max")).thenReturn(MAX_PRICE);
    servlet.doGet(request, response);
    writer.flush();

    // Make sure receipt is retrieved by finding the store name in the writer.
    Assert.assertTrue(stringWriter.toString().contains("walmart"));
  }

  @Test
  public void queryWithBlankFieldsIgnoresBlanks() throws IOException {
    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(writer);

    // Add mock receipts to datastore.
    long[] ids = TestUtils.addTestReceipts(datastore);

    // Perform doGet - this should retrieve the receipts.
    when(request.getParameter("timeZoneId")).thenReturn(CST_TIMEZONE_ID);
    when(request.getParameter("categories")).thenReturn("");
    when(request.getParameter("dateRange")).thenReturn("January 1, 2010 - July 31, 2020");
    when(request.getParameter("store")).thenReturn("");
    when(request.getParameter("min")).thenReturn(MIN_PRICE);
    when(request.getParameter("max")).thenReturn(MAX_PRICE);
    servlet.doGet(request, response);
    writer.flush();

    // Make sure receipts are retrieved by finding store names in the writer.
    Assert.assertTrue(stringWriter.toString().contains("main street restaurant"));
    Assert.assertTrue(stringWriter.toString().contains("contoso"));
  }

  @Test
  public void checkNullPointerExceptionIsThrown() throws IOException {
    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(writer);

    // Add mock receipts to datastore.
    long[] ids = TestUtils.addTestReceipts(datastore);

    // Perform doGet - this should retrieve the receipts.
    when(request.getParameter("timeZoneId")).thenReturn(CST_TIMEZONE_ID);
    when(request.getParameter("categories")).thenReturn(CATEGORIES);
    when(request.getParameter("dateRange")).thenReturn(DATE_RANGE);
    when(request.getParameter("store")).thenReturn(STORE);
    when(request.getParameter("min")).thenReturn(MIN_PRICE);
    when(request.getParameter("max")).thenReturn(null);
    servlet.doGet(request, response);
    writer.flush();

    Assert.assertTrue(stringWriter.toString().contains(NULL_EXCEPTION_MESSAGE));
    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }

  @Test
  public void checkNumberFormatExceptionIsThrown() throws IOException {
    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(writer);

    // Add mock receipts to datastore.
    long[] ids = TestUtils.addTestReceipts(datastore);

    // Perform doGet - this should retrieve the receipts.
    when(request.getParameter("timeZoneId")).thenReturn(CST_TIMEZONE_ID);
    when(request.getParameter("categories")).thenReturn(CATEGORIES);
    when(request.getParameter("dateRange")).thenReturn(DATE_RANGE);
    when(request.getParameter("store")).thenReturn(STORE);
    when(request.getParameter("min")).thenReturn(MIN_PRICE);
    when(request.getParameter("max")).thenReturn("");
    servlet.doGet(request, response);
    writer.flush();

    Assert.assertTrue(stringWriter.toString().contains(NUMBER_EXCEPTION_MESSAGE));
    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }

  @Test
  public void checkParseExceptionIsThrown() throws IOException {
    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(writer);

    // Add mock receipts to datastore.
    long[] ids = TestUtils.addTestReceipts(datastore);

    // Perform doGet - this should retrieve the receipts.
    when(request.getParameter("timeZoneId")).thenReturn(CST_TIMEZONE_ID);
    when(request.getParameter("categories")).thenReturn(CATEGORIES);
    when(request.getParameter("dateRange")).thenReturn("");
    when(request.getParameter("store")).thenReturn(STORE);
    when(request.getParameter("min")).thenReturn(MIN_PRICE);
    when(request.getParameter("max")).thenReturn(MAX_PRICE);
    servlet.doGet(request, response);
    writer.flush();

    Assert.assertTrue(stringWriter.toString().contains(PARSE_EXCEPTION_MESSAGE));
    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }
}