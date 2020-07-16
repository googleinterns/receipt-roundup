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

import com.google.appengine.api.blobstore.BlobInfo;
import com.google.appengine.api.blobstore.BlobInfoFactory;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.BlobstoreService;
import com.google.appengine.api.blobstore.UploadOptions;
import com.google.appengine.api.blobstore.UploadOptions.Builder;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Text;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.appengine.tools.development.testing.LocalUserServiceTestConfig;
import com.google.common.collect.ImmutableMap;
import com.google.sps.data.AnalysisResults;
import com.google.sps.servlets.ReceiptAnalysis;
import com.google.sps.servlets.UploadReceiptServlet;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(ReceiptAnalysis.class)
public final class UploadReceiptServletTest {
  private static final String FILE_NOT_SELECTED_LIVE_SERVER_WARNING =
      "com.google.sps.servlets.UploadReceiptServlet$FileNotSelectedException: No file was uploaded by the user (live server).";
  private static final String FILE_NOT_SELECTED_DEV_SERVER_WARNING =
      "com.google.sps.servlets.UploadReceiptServlet$FileNotSelectedException: No file was uploaded by the user (dev server).";
  private static final String INVALID_FILE_WARNING =
      "com.google.sps.servlets.UploadReceiptServlet$InvalidFileException: Uploaded file must be a JPEG image.";
  private static final String USER_NOT_LOGGED_IN_WARNING =
      "com.google.sps.servlets.UploadReceiptServlet$UserNotLoggedInException: User must be logged in to upload a receipt.";
  private static final String INVALID_DATE_RANGE_WARNING =
      "com.google.sps.servlets.UploadReceiptServlet$InvalidDateException: Transaction date must be in the past.";
  private static final String INVALID_DATE_FORMAT_WARNING =
      "com.google.sps.servlets.UploadReceiptServlet$InvalidDateException: Transaction date must be a long.";
  private static final String PRICE_NOT_PARSABLE_WARNING =
      "com.google.sps.servlets.UploadReceiptServlet$InvalidPriceException: Price could not be parsed.";
  private static final String PRICE_NEGATIVE_WARNING =
      "com.google.sps.servlets.UploadReceiptServlet$InvalidPriceException: Price must be positive.";
  private static final String RECEIPT_ANALYSIS_FAILED_WARNING =
      "com.google.sps.servlets.ReceiptAnalysis$ReceiptAnalysisException: Receipt analysis failed.";

  private static final String INSTANT = "2020-06-22T10:15:30Z";
  private static final long PAST_TIMESTAMP =
      Instant.parse(INSTANT).minusMillis(1234).toEpochMilli();

  private static final long MAX_UPLOAD_SIZE_BYTES = 5 * 1024 * 1024;
  private static final String UPLOAD_URL = "/blobstore/upload-receipt";

  private static final BlobKey BLOB_KEY = new BlobKey("blobKey");
  private static final String VALID_FILENAME = "image.jpg";
  private static final String INVALID_FILENAME = "image.png";
  private static final String VALID_CONTENT_TYPE = "image/jpeg";
  private static final String INVALID_CONTENT_TYPE = "image/png";
  private static final long IMAGE_SIZE_1MB = 1024 * 1024;
  private static final long IMAGE_SIZE_0MB = 0;
  private static final String HASH = "35454B055CC325EA1AF2126E27707052";

  private static final String[] CATEGORIES = new String[] {"burger", "fast food", "restaurant"};
  private static final Collection<String> CATEGORIES_COLLECTION = Arrays.asList(CATEGORIES);
  private static final Text RAW_TEXT = new Text("raw text");
  private static final double PRICE = 5.89;
  private static final String STORE = "McDonald's";
  private static final String INVALID_DATE_TYPE = "2020-05-20";
  private static final AnalysisResults ANALYSIS_RESULTS = new AnalysisResults(RAW_TEXT.getValue());

  private static final String IMAGE_URL = "/serve-image?blob-key=" + BLOB_KEY.getKeyString();
  private static final String LIVE_SERVER_BASE_URL =
      "https://capstone-receipt-step-2020.uc.r.appspot.com:80";
  private static final String LIVE_SERVER_ABSOLUTE_URL = LIVE_SERVER_BASE_URL + IMAGE_URL;
  private static final String LIVE_SERVER_SCHEME = "https";
  private static final String LIVE_SERVER_NAME = "capstone-receipt-step-2020.uc.r.appspot.com";
  private static final int LIVE_SERVER_PORT = 80;
  private static final String LIVE_SERVER_CONTEXT_PATH = "";
  private static final String DEV_SERVER_SCHEME = "http";
  private static final String DEV_SERVER_NAME = "0.0.0.0";
  private static final int DEV_SERVER_PORT = 80;
  private static final String DEV_SERVER_CONTEXT_PATH = "";

  private static final String DOMAIN_NAME = "gmail.com";
  private static final String USER_EMAIL = "test@gmail.com";
  private static final String USER_ID = "testID";

  // Uses local Datastore.
  private final LocalServiceTestHelper helper =
      new LocalServiceTestHelper(
          new LocalDatastoreServiceTestConfig(), new LocalUserServiceTestConfig())
          .setEnvEmail(USER_EMAIL)
          .setEnvAuthDomain(DOMAIN_NAME)
          .setEnvAttributes(new HashMap(
              ImmutableMap.of("com.google.appengine.api.users.UserService.user_id_key", USER_ID)));

  @Mock private BlobstoreService blobstoreService;
  @Mock private BlobInfoFactory blobInfoFactory;
  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;

  private UploadReceiptServlet servlet;
  private DatastoreService datastore;
  private Clock clock;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    helper.setUp();
    datastore = DatastoreServiceFactory.getDatastoreService();

    // Create a fixed time clock that always returns the same instant.
    clock = Clock.fixed(Instant.parse(INSTANT), ZoneId.systemDefault());

    servlet = new UploadReceiptServlet(blobstoreService, blobInfoFactory, datastore, clock);
  }

  @After
  public void tearDown() {
    helper.tearDown();
  }

  @Test
  public void doGetReturnsBlobstoreUploadUrl() throws IOException {
    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(writer);

    UploadOptions uploadOptions =
        UploadOptions.Builder.withMaxUploadSizeBytesPerBlob(MAX_UPLOAD_SIZE_BYTES);
    when(blobstoreService.createUploadUrl("/upload-receipt", uploadOptions)).thenReturn(UPLOAD_URL);

    servlet.doGet(request, response);
    writer.flush();

    Assert.assertTrue(stringWriter.toString().contains(UPLOAD_URL));
  }

  @Test
  public void doPostUploadsReceiptToDatastoreLiveServer() throws IOException {
    helper.setEnvIsLoggedIn(true);

    createMockBlob(request, VALID_CONTENT_TYPE, VALID_FILENAME, IMAGE_SIZE_1MB);
    stubRequestBody(request, CATEGORIES, STORE, PRICE, PAST_TIMESTAMP);
    stubUrlComponents(
        request, LIVE_SERVER_SCHEME, LIVE_SERVER_NAME, LIVE_SERVER_PORT, LIVE_SERVER_CONTEXT_PATH);

    // Mock receipt analysis.
    mockStatic(ReceiptAnalysis.class);
    when(ReceiptAnalysis.serveImageText(new URL(LIVE_SERVER_ABSOLUTE_URL)))
        .thenReturn(ANALYSIS_RESULTS);

    servlet.doPost(request, response);

    Query query = new Query("Receipt");
    PreparedQuery results = datastore.prepare(query);
    Entity receipt = results.asSingleEntity();

    Assert.assertEquals(receipt.getProperty("imageUrl"), IMAGE_URL);
    Assert.assertEquals(receipt.getProperty("price"), PRICE);
    Assert.assertEquals(receipt.getProperty("store"), STORE);
    Assert.assertEquals(receipt.getProperty("rawText"), RAW_TEXT);
    Assert.assertEquals(receipt.getProperty("blobKey"), BLOB_KEY);
    Assert.assertEquals(receipt.getProperty("timestamp"), PAST_TIMESTAMP);
    Assert.assertEquals(receipt.getProperty("categories"), CATEGORIES_COLLECTION);
    Assert.assertEquals(receipt.getProperty("userId"), USER_ID);
  }

  @Test
  public void doPostUploadsReceiptToDatastoreDevServer() throws IOException {
    helper.setEnvIsLoggedIn(true);

    createMockBlob(request, VALID_CONTENT_TYPE, VALID_FILENAME, IMAGE_SIZE_1MB);
    stubRequestBody(request, CATEGORIES, STORE, PRICE, PAST_TIMESTAMP);
    stubUrlComponents(
        request, DEV_SERVER_SCHEME, DEV_SERVER_NAME, DEV_SERVER_PORT, DEV_SERVER_CONTEXT_PATH);

    // Mock receipt analysis.
    mockStatic(ReceiptAnalysis.class);
    when(ReceiptAnalysis.serveImageText(BLOB_KEY)).thenReturn(ANALYSIS_RESULTS);

    servlet.doPost(request, response);

    Query query = new Query("Receipt");
    PreparedQuery results = datastore.prepare(query);
    Entity receipt = results.asSingleEntity();

    Assert.assertEquals(receipt.getProperty("imageUrl"), IMAGE_URL);
    Assert.assertEquals(receipt.getProperty("price"), PRICE);
    Assert.assertEquals(receipt.getProperty("store"), STORE);
    Assert.assertEquals(receipt.getProperty("rawText"), RAW_TEXT);
    Assert.assertEquals(receipt.getProperty("blobKey"), BLOB_KEY);
    Assert.assertEquals(receipt.getProperty("categories"), CATEGORIES_COLLECTION);
    Assert.assertEquals(receipt.getProperty("timestamp"), PAST_TIMESTAMP);
    Assert.assertEquals(receipt.getProperty("userId"), USER_ID);
  }

  @Test
  public void doPostRemovesDuplicateCategories() throws IOException {
    helper.setEnvIsLoggedIn(true);

    String[] categories = new String[] {"lunch", "restaurant", "lunch", "lunch", "restaurant"};
    createMockBlob(request, VALID_CONTENT_TYPE, VALID_FILENAME, IMAGE_SIZE_1MB);
    stubRequestBody(request, categories, STORE, PRICE, PAST_TIMESTAMP);
    stubUrlComponents(
        request, LIVE_SERVER_SCHEME, LIVE_SERVER_NAME, LIVE_SERVER_PORT, LIVE_SERVER_CONTEXT_PATH);

    // Mock receipt analysis.
    mockStatic(ReceiptAnalysis.class);
    when(ReceiptAnalysis.serveImageText(new URL(LIVE_SERVER_ABSOLUTE_URL)))
        .thenReturn(ANALYSIS_RESULTS);

    servlet.doPost(request, response);

    Query query = new Query("Receipt");
    PreparedQuery results = datastore.prepare(query);
    Entity receipt = results.asSingleEntity();

    Collection<String> categoriesWithoutDuplicates = Arrays.asList("lunch", "restaurant");
    Assert.assertEquals(receipt.getProperty("categories"), categoriesWithoutDuplicates);
  }

  @Test
  public void doPostSanitizesCategories() throws IOException {
    helper.setEnvIsLoggedIn(true);

    String[] categories =
        new String[] {"   fast   Food ", " Burger ", "  rEstaUrAnt ", "    LUNCH"};
    createMockBlob(request, VALID_CONTENT_TYPE, VALID_FILENAME, IMAGE_SIZE_1MB);
    stubRequestBody(request, categories, STORE, PRICE, PAST_TIMESTAMP);
    stubUrlComponents(
        request, LIVE_SERVER_SCHEME, LIVE_SERVER_NAME, LIVE_SERVER_PORT, LIVE_SERVER_CONTEXT_PATH);

    // Mock receipt analysis.
    mockStatic(ReceiptAnalysis.class);
    when(ReceiptAnalysis.serveImageText(new URL(LIVE_SERVER_ABSOLUTE_URL)))
        .thenReturn(ANALYSIS_RESULTS);

    servlet.doPost(request, response);

    Query query = new Query("Receipt");
    PreparedQuery results = datastore.prepare(query);
    Entity receipt = results.asSingleEntity();

    Collection<String> formattedCategories =
        Arrays.asList("fast food", "burger", "restaurant", "lunch");
    Assert.assertEquals(receipt.getProperty("categories"), formattedCategories);
  }

  @Test
  public void doPostThrowsIfFileNotSelectedLiveServer() throws IOException {
    helper.setEnvIsLoggedIn(true);

    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(writer);

    createMockBlob(request, VALID_CONTENT_TYPE, VALID_FILENAME, IMAGE_SIZE_0MB);
    stubRequestBody(request, CATEGORIES, STORE, PRICE, PAST_TIMESTAMP);

    servlet.doPost(request, response);
    writer.flush();

    Assert.assertTrue(stringWriter.toString().contains(FILE_NOT_SELECTED_LIVE_SERVER_WARNING));
    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);

    verify(blobstoreService).delete(BLOB_KEY);
  }

  @Test
  public void doPostThrowsIfFileNotSelectedDevServer() throws IOException {
    helper.setEnvIsLoggedIn(true);

    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(writer);

    Map<String, List<BlobKey>> blobs = new HashMap<>();
    when(blobstoreService.getUploads(request)).thenReturn(blobs);

    stubRequestBody(request, CATEGORIES, STORE, PRICE, PAST_TIMESTAMP);

    servlet.doPost(request, response);
    writer.flush();

    Assert.assertTrue(stringWriter.toString().contains(FILE_NOT_SELECTED_DEV_SERVER_WARNING));
    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }

  @Test
  public void doPostThrowsIfInvalidFile() throws IOException {
    helper.setEnvIsLoggedIn(true);

    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(writer);

    createMockBlob(request, INVALID_CONTENT_TYPE, INVALID_FILENAME, IMAGE_SIZE_1MB);
    stubRequestBody(request, CATEGORIES, STORE, PRICE, PAST_TIMESTAMP);

    servlet.doPost(request, response);
    writer.flush();

    Assert.assertTrue(stringWriter.toString().contains(INVALID_FILE_WARNING));
    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);

    verify(blobstoreService).delete(BLOB_KEY);
  }

  public void doPostThrowsIfUserIsLoggedOut() throws IOException {
    helper.setEnvIsLoggedIn(false);

    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(writer);

    createMockBlob(request, VALID_CONTENT_TYPE, VALID_FILENAME, IMAGE_SIZE_1MB);

    servlet.doPost(request, response);
    writer.flush();

    Assert.assertTrue(stringWriter.toString().contains(USER_NOT_LOGGED_IN_WARNING));
    verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);

    verify(blobstoreService).delete(BLOB_KEY);
  }

  @Test
  public void doPostThrowsIfDateIsInTheFuture() throws IOException {
    helper.setEnvIsLoggedIn(true);

    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(writer);

    long futureTimestamp = Instant.parse(INSTANT).plusMillis(1234).toEpochMilli();
    stubRequestBody(request, CATEGORIES, STORE, PRICE, futureTimestamp);
    stubUrlComponents(
        request, LIVE_SERVER_SCHEME, LIVE_SERVER_NAME, LIVE_SERVER_PORT, LIVE_SERVER_CONTEXT_PATH);

    servlet.doPost(request, response);
    writer.flush();

    Assert.assertTrue(stringWriter.toString().contains(INVALID_DATE_RANGE_WARNING));
    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }

  @Test
  public void doPostThrowsIfInvalidDateFormat() throws IOException {
    helper.setEnvIsLoggedIn(true);

    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(writer);

    when(request.getParameter("date")).thenReturn(INVALID_DATE_TYPE);

    servlet.doPost(request, response);
    writer.flush();

    Assert.assertTrue(stringWriter.toString().contains(INVALID_DATE_FORMAT_WARNING));
    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }

  @Test
  public void doPostThrowsIfReceiptAnalysisFails() throws IOException {
    helper.setEnvIsLoggedIn(true);

    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(writer);

    createMockBlob(request, VALID_CONTENT_TYPE, VALID_FILENAME, IMAGE_SIZE_1MB);
    stubRequestBody(request, CATEGORIES, STORE, PRICE, PAST_TIMESTAMP);
    stubUrlComponents(
        request, LIVE_SERVER_SCHEME, LIVE_SERVER_NAME, LIVE_SERVER_PORT, LIVE_SERVER_CONTEXT_PATH);

    // Mock receipt analysis exception.
    mockStatic(ReceiptAnalysis.class);
    when(ReceiptAnalysis.serveImageText(new URL(LIVE_SERVER_ABSOLUTE_URL)))
        .thenThrow(IOException.class);

    servlet.doPost(request, response);
    writer.flush();

    Assert.assertTrue(stringWriter.toString().contains(RECEIPT_ANALYSIS_FAILED_WARNING));
    verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

    verify(blobstoreService).delete(BLOB_KEY);
  }

  @Test
  public void doPostRoundPrice() throws IOException {
    helper.setEnvIsLoggedIn(true);

    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(writer);

    createMockBlob(request, VALID_CONTENT_TYPE, VALID_FILENAME, IMAGE_SIZE_1MB);

    double price = 17.236;
    double roundedPrice = 17.24;
    stubRequestBody(request, CATEGORIES, STORE, price, PAST_TIMESTAMP);
    stubUrlComponents(
        request, LIVE_SERVER_SCHEME, LIVE_SERVER_NAME, LIVE_SERVER_PORT, LIVE_SERVER_CONTEXT_PATH);

    // Mock receipt analysis.
    mockStatic(ReceiptAnalysis.class);
    when(ReceiptAnalysis.serveImageText(new URL(LIVE_SERVER_ABSOLUTE_URL)))
        .thenReturn(ANALYSIS_RESULTS);

    servlet.doPost(request, response);

    Query query = new Query("Receipt");
    PreparedQuery results = datastore.prepare(query);
    Entity receipt = results.asSingleEntity();

    Assert.assertEquals(receipt.getProperty("price"), roundedPrice);
  }

  @Test
  public void doPostThrowsIfPriceNotParsable() throws IOException {
    helper.setEnvIsLoggedIn(true);

    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(writer);

    String invalidPrice = "text";
    when(request.getParameter("date")).thenReturn(Long.toString(PAST_TIMESTAMP));
    when(request.getParameter("price")).thenReturn(invalidPrice);

    servlet.doPost(request, response);
    writer.flush();

    Assert.assertTrue(stringWriter.toString().contains(PRICE_NOT_PARSABLE_WARNING));
    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }

  @Test
  public void doPostThrowsIfPriceNegative() throws IOException {
    helper.setEnvIsLoggedIn(true);

    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(writer);

    String negativePrice = "-12.55";
    when(request.getParameter("date")).thenReturn(Long.toString(PAST_TIMESTAMP));
    when(request.getParameter("price")).thenReturn(negativePrice);

    servlet.doPost(request, response);
    writer.flush();

    Assert.assertTrue(stringWriter.toString().contains(PRICE_NEGATIVE_WARNING));
    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }

  /**
   * Adds a mock blob with the given content type, filename, and size to the mocked Blobstore.
   */
  private void createMockBlob(
      HttpServletRequest request, String contentType, String filename, long size) {
    Map<String, List<BlobKey>> blobs = new HashMap<>();
    blobs.put("receipt-image", Arrays.asList(BLOB_KEY));
    when(blobstoreService.getUploads(request)).thenReturn(blobs);
    BlobInfo blobInfo = new BlobInfo(BLOB_KEY, contentType, new Date(), filename, size, HASH, null);
    when(blobInfoFactory.loadBlobInfo(BLOB_KEY)).thenReturn(blobInfo);
  }

  /**
   * Stubs the request with the given label, store, and price parameters.
   */
  private void stubRequestBody(
      HttpServletRequest request, String[] categories, String store, double price, long timestamp) {
    when(request.getParameterValues("categories")).thenReturn(categories);
    when(request.getParameter("store")).thenReturn(store);
    when(request.getParameter("price")).thenReturn(String.valueOf(price));
    when(request.getParameter("date")).thenReturn(Long.toString(timestamp));
  }

  /**
   * Stubs the request with the given scheme, server name, port, and context path URL components.
   */
  private void stubUrlComponents(
      HttpServletRequest request, String scheme, String serverName, int port, String contextPath) {
    when(request.getScheme()).thenReturn(scheme);
    when(request.getServerName()).thenReturn(serverName);
    when(request.getServerPort()).thenReturn(port);
    when(request.getContextPath()).thenReturn(contextPath);
  }
}
