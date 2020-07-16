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
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public final class SearchServletTest {
  private static final String EMPTY_STRING = "";
  private static final String NULL_VALUE = null;

  private static final String NULL_EXCEPTION_MESSAGE =
      "Null Field: Receipt unable to be queried at this time, please try again.";
  private static final String NUMBER_EXCEPTION_MESSAGE =
      "Invalid Price: Receipt unable to be queried at this time, please try again.";
  private static final String PARSE_EXCEPTION_MESSAGE =
      "Dates Unparseable: Receipt unable to be queried at this time, please try again.";

  // Values for a valid test.
  private static final String CST_TIMEZONE_ID = "America/Chicago";
  private static final String CATEGORY = "drink";
  private static final String SHORT_DATE_RANGE = "February 1, 2003 - February 28, 2003";
  private static final String LONG_DATE_RANGE = "January 1, 2010 - July 31, 2020";
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
  private StringWriter stringWriter;
  private PrintWriter writer;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    helper.setUp();
    datastore = DatastoreServiceFactory.getDatastoreService();

    servlet = new SearchServlet(datastore);

    stringWriter = new StringWriter();
    writer = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(writer);
  }

  @After
  public void tearDown() {
    helper.tearDown();
  }

  @Test
  public void queryWithAllFieldsFilled() throws IOException {
    // Add mock receipts to datastore.
    TestUtils.addTestReceipts(datastore);

    // Perform doGet - this should retrieve one receipt.
    TestUtils.setRequestParameters(
        request, CST_TIMEZONE_ID, CATEGORY, SHORT_DATE_RANGE, STORE, MIN_PRICE, MAX_PRICE);
    servlet.doGet(request, response);
    writer.flush();

    // Make sure receipt is retrieved by finding the store name in the writer.
    Assert.assertTrue(stringWriter.toString().contains("walmart"));
  }

  @Test
  public void queryWithBlankFieldsIgnoresBlanks() throws IOException {
    // Add mock receipts to datastore.
    TestUtils.addTestReceipts(datastore);

    // Perform doGet - this should retrieve a couple receipts.
    TestUtils.setRequestParameters(request, CST_TIMEZONE_ID, EMPTY_STRING, LONG_DATE_RANGE,
        EMPTY_STRING, MIN_PRICE, MAX_PRICE);
    servlet.doGet(request, response);
    writer.flush();

    // Make sure receipts are retrieved by finding store names in the writer.
    Assert.assertTrue(stringWriter.toString().contains("main street restaurant"));
    Assert.assertTrue(stringWriter.toString().contains("contoso"));
  }

  @Test
  public void checkNullPointerExceptionIsThrown() throws IOException {
    // Add mock receipts to datastore.
    TestUtils.addTestReceipts(datastore);

    // Perform doGet with a null value as a parameter.
    TestUtils.setRequestParameters(
        request, CST_TIMEZONE_ID, CATEGORY, SHORT_DATE_RANGE, STORE, MIN_PRICE, NULL_VALUE);
    servlet.doGet(request, response);
    writer.flush();

    Assert.assertTrue(stringWriter.toString().contains(NULL_EXCEPTION_MESSAGE));
    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }

  @Test
  public void checkNumberFormatExceptionIsThrown() throws IOException {
    // Add mock receipts to datastore.
    TestUtils.addTestReceipts(datastore);

    // Perform doGet with an empty string for price.
    TestUtils.setRequestParameters(
        request, CST_TIMEZONE_ID, CATEGORY, SHORT_DATE_RANGE, STORE, MAX_PRICE, EMPTY_STRING);
    servlet.doGet(request, response);
    writer.flush();

    Assert.assertTrue(stringWriter.toString().contains(NUMBER_EXCEPTION_MESSAGE));
    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }

  @Test
  public void checkParseExceptionIsThrown() throws IOException {
    // Add mock receipts to datastore.
    TestUtils.addTestReceipts(datastore);

    // Perform doGet with an empty string for date range.
    TestUtils.setRequestParameters(
        request, CST_TIMEZONE_ID, CATEGORY, EMPTY_STRING, STORE, MIN_PRICE, MAX_PRICE);
    servlet.doGet(request, response);
    writer.flush();

    Assert.assertTrue(stringWriter.toString().contains(PARSE_EXCEPTION_MESSAGE));
    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }
}