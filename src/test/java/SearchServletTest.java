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

import com.dampcake.gson.immutable.ImmutableAdapterFactory;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.appengine.tools.development.testing.LocalUserServiceTestConfig;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.sps.data.Receipt;
import com.google.sps.servlets.SearchServlet;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public final class SearchServletTest {
  private static final String NULL_EXCEPTION_MESSAGE =
      "Null Field: Receipt unable to be queried at this time, please try again.";
  private static final String NUMBER_EXCEPTION_MESSAGE =
      "Invalid Price: Receipt unable to be queried at this time, please try again.";
  private static final String PARSE_EXCEPTION_MESSAGE =
      "Dates Unparseable: Receipt unable to be queried at this time, please try again.";
  private static final String AUTHENTICATION_ERROR_MESSAGE =
      "No Authentication: User must be logged in to search receipts.";

  // Values for a valid test.
  private static final String CST_TIMEZONE_ID = "America/Chicago";
  private static final String CATEGORY = "drink";
  private static final String SHORT_DATE_RANGE = "February 1, 2003 - February 28, 2003";
  private static final String LONG_DATE_RANGE = "January 1, 2010 - July 31, 2020";
  private static final String STORE = "walmart";
  private static final String MIN_PRICE = "5.00";
  private static final String MAX_PRICE = "30.00";

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

  private final Gson gson =
      new GsonBuilder().registerTypeAdapterFactory(ImmutableAdapterFactory.forGuava()).create();

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
    // Columns ommitted from database visual: id, userId, imageUrl, rawText.
    //
    // id   Timestamp      Price          Store                    Categories
    // 1  1045237591000    26.12        "walmart"         ["candy", "drink", "personal"]
    // 2  1560193140000    14.51        "contoso"         ["cappuccino", "sandwich", "lunch"]
    // 3  1491582960000    29.01   "main st restaurant"   ["food", "meal", "lunch"]
    //
    // Query: drink, 2/1/03-2/28/03, walmart, $5.00-$30.00.
    // Will only return walmart receipt.

    // Add mock receipts to datastore.
    TestUtils.addTestReceipts(datastore);

    // Perform doGet - this should retrieve one receipt.
    TestUtils.setSearchServletRequestParameters(
        request, CST_TIMEZONE_ID, CATEGORY, SHORT_DATE_RANGE, STORE, MIN_PRICE, MAX_PRICE);
    servlet.doGet(request, response);
    writer.flush();

    // Make sure receipt is retrieved by finding the receipt id in the writer.
    Assert.assertTrue(stringWriter.toString().contains("\"id\":1"));
  }

  @Test
  public void queryWithoutStore() throws IOException {
    // Columns ommitted from database visual: id, userId, imageUrl, rawText.
    //
    // id   Timestamp      Price          Store                    Categories
    // 1  1045237591000    26.12        "walmart"         ["candy", "drink", "personal"]
    // 2  1560193140000    14.51        "contoso"         ["cappuccino", "sandwich", "lunch"]
    // 3  1491582960000    29.01   "main st restaurant"   ["food", "meal", "lunch"]
    //
    // Query: drink, 2/1/03-2/28/03, $5.00-$30.00.
    // Will only return walmart.

    // Add mock receipts to datastore.
    TestUtils.addTestReceipts(datastore);

    // Perform doGet - this should retrieve one receipt.
    TestUtils.setSearchServletRequestParameters(
        request, CST_TIMEZONE_ID, CATEGORY, SHORT_DATE_RANGE, /*store=*/"", MIN_PRICE, MAX_PRICE);
    servlet.doGet(request, response);
    writer.flush();

    // Make sure receipt is retrieved by finding receipt id in the writer.
    Assert.assertTrue(stringWriter.toString().contains("\"id\":1"));
  }

  @Test
  public void queryWithoutCategory() throws IOException {
    // Columns ommitted from database visual: id, userId, imageUrl, rawText.
    //
    // id   Timestamp      Price          Store                    Categories
    // 1  1045237591000    26.12        "walmart"         ["candy", "drink", "personal"]
    // 2  1560193140000    14.51        "contoso"         ["cappuccino", "sandwich", "lunch"]
    // 3  1491582960000    29.01   "main st restaurant"   ["food", "meal", "lunch"]
    //
    // Query: 1/1/10-7/31/20, contoso, $5.00-$30.00.
    // Will only return contoso receipt.

    // Add mock receipts to datastore.
    TestUtils.addTestReceipts(datastore);

    // Perform doGet - this should retrieve one receipt.
    TestUtils.setSearchServletRequestParameters(request, CST_TIMEZONE_ID, /*category=*/"",
        LONG_DATE_RANGE, "CONTOSO", MIN_PRICE, MAX_PRICE);
    servlet.doGet(request, response);
    writer.flush();

    // Make sure receipt is retrieved by finding receipt id in the writer.
    Assert.assertTrue(stringWriter.toString().contains("\"id\":2"));
  }

  @Test
  public void queryWithoutStoreAndCategory() throws IOException {
    // Columns ommitted from database visual: id, userId, imageUrl, rawText.
    //
    // id   Timestamp      Price          Store                    Categories
    // 1  1045237591000    26.12        "walmart"         ["candy", "drink", "personal"]
    // 2  1560193140000    14.51        "contoso"         ["cappuccino", "sandwich", "lunch"]
    // 3  1491582960000    29.01   "main st restaurant"   ["food", "meal", "lunch"]
    //
    // Query: 1/1/10-7/31/20, $5.00-$30.00.
    // Will return main st restaurant and contoso receipts.

    // Add mock receipts to datastore.
    TestUtils.addTestReceipts(datastore);

    // Perform doGet - this should retrieve a couple receipts.
    TestUtils.setSearchServletRequestParameters(request, CST_TIMEZONE_ID, /*category=*/"",
        LONG_DATE_RANGE, /*store=*/"", MIN_PRICE, MAX_PRICE);
    servlet.doGet(request, response);
    writer.flush();

    // Make sure receipts are retrieved by finding receipt ids in the writer.
    Assert.assertTrue(stringWriter.toString().contains("\"id\":2"));
    Assert.assertTrue(stringWriter.toString().contains("\"id\":3"));
  }

  @Test
  public void queryAllReceipts() throws IOException {
    // Columns ommitted from database visual: id, userId, imageUrl, rawText.
    //
    // id   Timestamp      Price          Store                    Categories
    // 1  1045237591000    26.12        "walmart"         ["candy", "drink", "personal"]
    // 2  1560193140000    14.51        "contoso"         ["cappuccino", "sandwich", "lunch"]
    // 3  1491582960000    29.01   "main st restaurant"   ["food", "meal", "lunch"]

    // Add mock receipts to datastore.
    ImmutableSet<Entity> expectedReceipts = TestUtils.addTestReceipts(datastore);

    when(request.getParameter("isPageLoad")).thenReturn("true");

    // Perform doGet - this should retrieve all receipts.
    servlet.doGet(request, response);
    writer.flush();

    // Make sure all receipts retrieved by checking their ids.
    Gson gson =
        new GsonBuilder().registerTypeAdapterFactory(ImmutableAdapterFactory.forGuava()).create();
    Receipt[] returnedReceipts = gson.fromJson(stringWriter.toString(), Receipt[].class);
    Assert.assertEquals(expectedReceipts.size(), returnedReceipts.length);
    for (Entity expectedReceipt : expectedReceipts) {
      Assert.assertTrue(
          Arrays.stream(returnedReceipts)
              .anyMatch(
                  receipt -> receipt.getId() == expectedReceipt.getKey().getId())); // check id
    }
  }

  @Test
  public void checkNullPointerExceptionIsThrown() throws IOException {
    // Query: drink, 2/1/03-2/28/03, walmart, $5.00-null.
    // Will throw NullPointerException since maxPrice was null.

    // Add mock receipts to datastore.
    TestUtils.addTestReceipts(datastore);

    // Perform doGet with a null value as a parameter.
    TestUtils.setSearchServletRequestParameters(
        request, CST_TIMEZONE_ID, CATEGORY, SHORT_DATE_RANGE, STORE, MIN_PRICE, /*maxPrice=*/null);
    servlet.doGet(request, response);
    writer.flush();

    Assert.assertTrue(stringWriter.toString().contains(NULL_EXCEPTION_MESSAGE));
    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }

  @Test
  public void checkNumberFormatExceptionIsThrown() throws IOException {
    // Query: drink, 2/1/03-2/28/03, walmart, $5.00-"".
    // Will throw NumberFormatException since maxPrice was the empty string.

    // Add mock receipts to datastore.
    TestUtils.addTestReceipts(datastore);

    // Perform doGet with an empty string for price.
    TestUtils.setSearchServletRequestParameters(
        request, CST_TIMEZONE_ID, CATEGORY, SHORT_DATE_RANGE, STORE, MAX_PRICE, /*minPrice=*/"");
    servlet.doGet(request, response);
    writer.flush();

    Assert.assertTrue(stringWriter.toString().contains(NUMBER_EXCEPTION_MESSAGE));
    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }

  @Test
  public void checkParseExceptionIsThrown() throws IOException {
    // Query: drink, "", walmart, $5.00-$30.00.
    // Will throw ParseExceptionThrow since dateRange was the empty string.

    // Add mock receipts to datastore.
    TestUtils.addTestReceipts(datastore);

    // Perform doGet with an empty string for date range.
    TestUtils.setSearchServletRequestParameters(
        request, CST_TIMEZONE_ID, CATEGORY, /*dateRange=*/"", STORE, MIN_PRICE, MAX_PRICE);
    servlet.doGet(request, response);
    writer.flush();

    Assert.assertTrue(stringWriter.toString().contains(PARSE_EXCEPTION_MESSAGE));
    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
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

  @Test
  public void paginationNextPage() throws IOException {
    // Add 12 mock receipts to datastore.
    ImmutableSet<Entity> expectedReceipts = TestUtils.addManyTestReceipts(datastore);

    // Perform doGet - this should retrieve max receipts for page, which is 10.
    when(request.getParameter("isPageLoad")).thenReturn("true");
    servlet.doGet(request, response);
    writer.flush();

    // Make sure first page of returned receipts matches expected by checking ids.
    ImmutableList<Entity> expectedFirstPage = expectedReceipts.asList().subList(2, 12);
    Receipt[] returnedFirstPage = gson.fromJson(stringWriter.toString(), Receipt[].class);

    Assert.assertEquals(expectedFirstPage.size(), returnedFirstPage.length);
    Assert.assertTrue(TestUtils.checkIdsMatch(expectedFirstPage, returnedFirstPage));

    // Perform doGet - this should retrieve last few receipts, which is 2.
    when(request.getParameter("isNewSearch")).thenReturn("false");
    when(request.getParameter("getNextPage")).thenReturn("true");

    stringWriter.getBuffer().setLength(0); // Clear stringwriter of last receipts.
    servlet.doGet(request, response);
    writer.flush();

    // Make sure second page of returned receipts matches expected by checking ids.
    ImmutableList<Entity> expectedSecondPage = expectedReceipts.asList().subList(0, 2);
    Receipt[] returnedSecondPage = gson.fromJson(stringWriter.toString(), Receipt[].class);

    Assert.assertEquals(expectedSecondPage.size(), returnedSecondPage.length);
    Assert.assertTrue(TestUtils.checkIdsMatch(expectedSecondPage, returnedSecondPage));
  }

  @Test
  public void paginationPreviousPage() throws IOException {
    // This test simulates starting on page 1, moving to page 2, then moving back to 1.

    // Add 12 mock receipts to datastore.
    ImmutableSet<Entity> expectedReceipts = TestUtils.addManyTestReceipts(datastore);

    // Perform doGet - this should retrieve first page with 10 receipts.
    when(request.getParameter("isPageLoad")).thenReturn("true");
    servlet.doGet(request, response);
    writer.flush();

    // Perform doGet - this should retrieve second page with last 2 receipts.
    when(request.getParameter("isNewSearch")).thenReturn("false");
    when(request.getParameter("getNextPage")).thenReturn("true");
    stringWriter.getBuffer().setLength(0); // Clear stringwriter of last receipts.
    servlet.doGet(request, response);
    writer.flush();

    // Perform doGet - this should retrieve first page again with 10 receipts.
    when(request.getParameter("getNextPage")).thenReturn("false");
    when(request.getParameter("getPreviousPage")).thenReturn("true");
    stringWriter.getBuffer().setLength(0); // Clear stringwriter of last receipts.
    servlet.doGet(request, response);
    writer.flush();

    // Make sure first page of returned receipts matches expected by checking ids.
    ImmutableList<Entity> expectedFirstPage = expectedReceipts.asList().subList(2, 12);
    Receipt[] returnedFirstPage = gson.fromJson(stringWriter.toString(), Receipt[].class);

    Assert.assertEquals(expectedFirstPage.size(), returnedFirstPage.length);
    Assert.assertTrue(TestUtils.checkIdsMatch(expectedFirstPage, returnedFirstPage));
  }
}
