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
import static org.powermock.api.mockito.PowerMockito.mockStatic;

import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Text;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.appengine.tools.development.testing.LocalUserServiceTestConfig;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.sps.servlets.EditReceiptServlet;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
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
public final class EditReceiptServletTest {
  private static final String USER_NOT_LOGGED_IN_WARNING =
      "User must be logged in to edit a receipt.\n";
  private static final String INVALID_DATE_RANGE_WARNING =
      "com.google.sps.servlets.UploadReceiptServlet$InvalidDateException: Transaction date must be in the past.\n";
  private static final String INVALID_DATE_FORMAT_WARNING =
      "com.google.sps.servlets.UploadReceiptServlet$InvalidDateException: Transaction date must be a long.\n";
  private static final String PRICE_NOT_PARSABLE_WARNING =
      "com.google.sps.servlets.UploadReceiptServlet$InvalidPriceException: Price could not be parsed.\n";
  private static final String PRICE_NEGATIVE_WARNING =
      "com.google.sps.servlets.UploadReceiptServlet$InvalidPriceException: Price must be positive.\n";
  private static final String INVALID_ID_WARNING =
      "java.lang.NumberFormatException: For input string: \"invalid\"\n";
  private static final String ENTITY_NOT_FOUND_WARNING =
      "com.google.appengine.api.datastore.EntityNotFoundException: No entity was found matching the key: Receipt(-1)\n";

  private static final String INSTANT = "2020-06-22T10:15:30Z";

  private static final String[] ORIGINAL_CATEGORIES =
      new String[] {"burger", "fast food", "restaurant", "dining"};
  private static final double ORIGINAL_PRICE = 5.89;
  private static final String ORIGINAL_STORE = "mcdonald's";
  private static final long ORIGINAL_TIMESTAMP =
      Instant.parse(INSTANT).minusMillis(1234).toEpochMilli();

  private static final String[] NEW_CATEGORIES = new String[] {"fast food", "restaurant", "lunch"};
  private static final double NEW_PRICE = 12.03;
  private static final String NEW_STORE = "subway";
  private static final long NEW_TIMESTAMP = Instant.parse(INSTANT).minusMillis(612).toEpochMilli();

  private static final String DOMAIN_NAME = "gmail.com";
  private static final String USER_EMAIL = "test@gmail.com";
  private static final String USER_ID = "testID";

  // Uses local Datastore and UserService.
  private final LocalServiceTestHelper helper =
      new LocalServiceTestHelper(
          new LocalDatastoreServiceTestConfig(), new LocalUserServiceTestConfig())
          .setEnvEmail(USER_EMAIL)
          .setEnvAuthDomain(DOMAIN_NAME)
          .setEnvAttributes(new HashMap(
              ImmutableMap.of("com.google.appengine.api.users.UserService.user_id_key", USER_ID)));

  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;

  private EditReceiptServlet servlet;
  private DatastoreService datastore;
  private Clock clock;
  private StringWriter stringWriter;
  private PrintWriter writer;
  private long receiptId;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    helper.setUp();
    datastore = DatastoreServiceFactory.getDatastoreService();
    receiptId = addReceipt();

    // Create a fixed time clock that always returns the same instant.
    clock = Clock.fixed(Instant.parse(INSTANT), ZoneId.systemDefault());

    stringWriter = new StringWriter();
    writer = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(writer);

    servlet = new EditReceiptServlet(datastore, clock);
  }

  @After
  public void tearDown() {
    helper.tearDown();
  }

  @Test
  public void doPostEditsReceipt() throws IOException {
    helper.setEnvIsLoggedIn(true);

    stubRequestBody(request, receiptId, NEW_CATEGORIES, NEW_STORE, NEW_PRICE, NEW_TIMESTAMP);

    servlet.doPost(request, response);
    writer.flush();

    Entity receipt = queryReceipt();

    // Check that the receipt was updated.
    Assert.assertEquals(NEW_PRICE, receipt.getProperty("price"));
    Assert.assertEquals(NEW_STORE, receipt.getProperty("store"));
    Assert.assertEquals(NEW_TIMESTAMP, receipt.getProperty("timestamp"));
    Assert.assertEquals(Arrays.asList(NEW_CATEGORIES), receipt.getProperty("categories"));

    // Verify the JSON response.
    String response = TestUtils.extractProperties(stringWriter.toString());
    Entity expectedReceipt =
        createReceiptEntity(NEW_PRICE, NEW_STORE, NEW_TIMESTAMP, NEW_CATEGORIES);
    String expectedResponse =
        TestUtils.extractProperties(new Gson().toJson(expectedReceipt)) + "\n";
    Assert.assertEquals(expectedResponse, response);
  }

  @Test
  public void doPostEditsReceiptWithNoInitialProperties()
      throws IOException, EntityNotFoundException {
    helper.setEnvIsLoggedIn(true);

    Entity originalReceipt = new Entity("Receipt");
    datastore.put(originalReceipt);
    long id = originalReceipt.getKey().getId();

    stubRequestBody(request, id, NEW_CATEGORIES, NEW_STORE, NEW_PRICE, NEW_TIMESTAMP);

    servlet.doPost(request, response);
    writer.flush();

    Entity receipt = datastore.get(KeyFactory.createKey("Receipt", id));

    Assert.assertEquals(NEW_PRICE, receipt.getProperty("price"));
    Assert.assertEquals(NEW_STORE, receipt.getProperty("store"));
    Assert.assertEquals(NEW_TIMESTAMP, receipt.getProperty("timestamp"));
    Assert.assertEquals(Arrays.asList(NEW_CATEGORIES), receipt.getProperty("categories"));
  }

  @Test
  public void doPostSanitizesStore() throws IOException {
    helper.setEnvIsLoggedIn(true);

    String store = "    TraDeR   JOE's  ";
    stubRequestBody(request, receiptId, NEW_CATEGORIES, store, NEW_PRICE, NEW_TIMESTAMP);

    servlet.doPost(request, response);

    Entity receipt = queryReceipt();

    String expectedStore = "trader joe's";
    Assert.assertEquals(expectedStore, receipt.getProperty("store"));
  }

  @Test
  public void doPostRemovesDuplicateCategories() throws IOException {
    helper.setEnvIsLoggedIn(true);

    String[] categories = new String[] {"lunch", "restaurant", "lunch", "lunch", "restaurant"};
    stubRequestBody(request, receiptId, categories, NEW_STORE, NEW_PRICE, NEW_TIMESTAMP);

    servlet.doPost(request, response);

    Entity receipt = queryReceipt();

    Collection<String> expectedCategories = Arrays.asList("lunch", "restaurant");
    Assert.assertEquals(expectedCategories, receipt.getProperty("categories"));
  }

  @Test
  public void doPostSanitizesCategories() throws IOException {
    helper.setEnvIsLoggedIn(true);

    String[] categories =
        new String[] {"   fast   Food ", " Burger ", "  rEstaUrAnt ", "    LUNCH"};
    stubRequestBody(request, receiptId, categories, NEW_STORE, NEW_PRICE, NEW_TIMESTAMP);

    servlet.doPost(request, response);

    Entity receipt = queryReceipt();

    Collection<String> expectedCategories =
        Arrays.asList("fast food", "burger", "restaurant", "lunch");
    Assert.assertEquals(expectedCategories, receipt.getProperty("categories"));
  }

  @Test
  public void doPostThrowsIfUserIsLoggedOut() throws IOException {
    helper.setEnvIsLoggedIn(false);

    servlet.doPost(request, response);
    writer.flush();

    Assert.assertEquals(USER_NOT_LOGGED_IN_WARNING, stringWriter.toString());
    verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
  }

  @Test
  public void doPostThrowsIfDateIsInTheFuture() throws IOException {
    helper.setEnvIsLoggedIn(true);

    long futureTimestamp = Instant.parse(INSTANT).plusMillis(1234).toEpochMilli();
    stubRequestBody(request, receiptId, NEW_CATEGORIES, NEW_STORE, NEW_PRICE, futureTimestamp);

    servlet.doPost(request, response);
    writer.flush();

    Assert.assertEquals(INVALID_DATE_RANGE_WARNING, stringWriter.toString());
    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }

  @Test
  public void doPostThrowsIfInvalidDateFormat() throws IOException {
    helper.setEnvIsLoggedIn(true);

    stubRequestBody(request, receiptId, NEW_CATEGORIES, NEW_STORE, NEW_PRICE, NEW_TIMESTAMP);

    String invalidDateType = "2020-05-20";
    when(request.getParameter("date")).thenReturn(invalidDateType);

    servlet.doPost(request, response);
    writer.flush();

    Assert.assertEquals(INVALID_DATE_FORMAT_WARNING, stringWriter.toString());
    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }

  @Test
  public void doPostRoundPrice() throws IOException {
    helper.setEnvIsLoggedIn(true);

    double price = 17.236;
    double roundedPrice = 17.24;
    stubRequestBody(request, receiptId, NEW_CATEGORIES, NEW_STORE, price, NEW_TIMESTAMP);

    servlet.doPost(request, response);

    Entity receipt = queryReceipt();

    Assert.assertEquals(roundedPrice, receipt.getProperty("price"));
  }

  @Test
  public void doPostThrowsIfPriceNotParsable() throws IOException {
    helper.setEnvIsLoggedIn(true);

    stubRequestBody(request, receiptId, NEW_CATEGORIES, NEW_STORE, NEW_PRICE, NEW_TIMESTAMP);

    String invalidPrice = "text";
    when(request.getParameter("price")).thenReturn(invalidPrice);

    servlet.doPost(request, response);
    writer.flush();

    Assert.assertEquals(PRICE_NOT_PARSABLE_WARNING, stringWriter.toString());
    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }

  @Test
  public void doPostThrowsIfPriceNegative() throws IOException {
    helper.setEnvIsLoggedIn(true);

    double negativePrice = -12.55;
    stubRequestBody(request, receiptId, NEW_CATEGORIES, NEW_STORE, negativePrice, NEW_TIMESTAMP);

    servlet.doPost(request, response);
    writer.flush();

    Assert.assertEquals(PRICE_NEGATIVE_WARNING, stringWriter.toString());
    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }

  @Test
  public void doPostThrowsIfInvalidIdFormat() throws IOException {
    helper.setEnvIsLoggedIn(true);

    when(request.getParameter("id")).thenReturn("invalid");

    servlet.doPost(request, response);
    writer.flush();

    Assert.assertEquals(INVALID_ID_WARNING, stringWriter.toString());
    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }

  @Test
  public void doPostThrowsIfEntityNotFound() throws IOException {
    helper.setEnvIsLoggedIn(true);

    when(request.getParameter("id")).thenReturn(String.valueOf(-1));

    servlet.doPost(request, response);
    writer.flush();

    Assert.assertEquals(ENTITY_NOT_FOUND_WARNING, stringWriter.toString());
    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }

  /**
   * Creates a receipt entity, adds it to Datastore, and returns its identifier.
   */
  private long addReceipt() {
    Entity receipt = createReceiptEntity(
        ORIGINAL_PRICE, ORIGINAL_STORE, ORIGINAL_TIMESTAMP, ORIGINAL_CATEGORIES);
    datastore.put(receipt);
    return receipt.getKey().getId();
  }

  /**
   * Creates an entity with the given properties.
   */
  private Entity createReceiptEntity(
      double price, String store, long timestamp, String[] categories) {
    Entity receipt = new Entity("Receipt");
    receipt.setProperty("categories", Arrays.asList(categories));
    receipt.setProperty("timestamp", timestamp);
    receipt.setProperty("store", store);
    receipt.setProperty("price", price);

    return receipt;
  }

  /**
   * Stubs the request with the given id, categories, store, price, and timestamp parameters.
   */
  private void stubRequestBody(HttpServletRequest request, long id, String[] categories,
      String store, double price, long timestamp) {
    when(request.getParameter("id")).thenReturn(String.valueOf(id));
    when(request.getParameterValues("categories")).thenReturn(categories);
    when(request.getParameter("store")).thenReturn(store);
    when(request.getParameter("price")).thenReturn(String.valueOf(price));
    when(request.getParameter("date")).thenReturn(Long.toString(timestamp));
  }

  /**
   * Gets the receipt entity from Datastore.
   */
  private Entity queryReceipt() {
    Query query = new Query("Receipt");
    PreparedQuery results = datastore.prepare(query);
    return results.asSingleEntity();
  }
}
