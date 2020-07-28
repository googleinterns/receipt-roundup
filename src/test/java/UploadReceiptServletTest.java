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
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.appengine.tools.development.testing.LocalUserServiceTestConfig;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.sps.data.AnalysisResults;
import com.google.sps.servlets.ReceiptAnalysis;
import com.google.sps.servlets.ReceiptAnalysis.ReceiptAnalysisException;
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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@PowerMockIgnore("jdk.internal.reflect.*")
@RunWith(PowerMockRunner.class)
@PrepareForTest(ReceiptAnalysis.class)
public final class UploadReceiptServletTest {
  private static final String FILE_NOT_SELECTED_LIVE_SERVER_WARNING =
      "com.google.sps.servlets.UploadReceiptServlet$FileNotSelectedException: No file was uploaded by the user (live server).\n";
  private static final String FILE_NOT_SELECTED_DEV_SERVER_WARNING =
      "com.google.sps.servlets.UploadReceiptServlet$FileNotSelectedException: No file was uploaded by the user (dev server).\n";
  private static final String INVALID_FILE_WARNING =
      "com.google.sps.servlets.UploadReceiptServlet$InvalidFileException: Uploaded file must be a JPEG image.\n";
  private static final String USER_NOT_LOGGED_IN_WARNING =
      "com.google.sps.servlets.UploadReceiptServlet$UserNotLoggedInException: User must be logged in to upload a receipt.\n";
  private static final String INVALID_DATE_RANGE_WARNING =
      "com.google.sps.servlets.FormatUtils$InvalidDateException: Transaction date must be in the past.\n";
  private static final String INVALID_DATE_FORMAT_WARNING =
      "com.google.sps.servlets.FormatUtils$InvalidDateException: Transaction date must be a long.\n";
  private static final String PRICE_NOT_PARSABLE_WARNING =
      "com.google.sps.servlets.FormatUtils$InvalidPriceException: Price could not be parsed.\n";
  private static final String PRICE_NEGATIVE_WARNING =
      "com.google.sps.servlets.FormatUtils$InvalidPriceException: Price must be positive.\n";
  private static final String RECEIPT_ANALYSIS_FAILED_WARNING =
      "com.google.sps.servlets.ReceiptAnalysis$ReceiptAnalysisException: Receipt analysis failed.\n";

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

  private static final Set<String> GENERATED_CATEGORIES =
      ImmutableSet.of("burger", "fast food", "restaurant");
  private static final Collection<String> CATEGORIES_COLLECTION =
      GENERATED_CATEGORIES.stream().collect(Collectors.toList());
  private static final Text RAW_TEXT = new Text("raw text");
  private static final double PRICE = 5.89;
  private static final String STORE = "mcdonald's";
  private static final String INVALID_DATE_TYPE = "2020-05-20";
  private static final AnalysisResults ANALYSIS_RESULTS =
      new AnalysisResults.Builder(RAW_TEXT.getValue())
          .setCategories(GENERATED_CATEGORIES)
          .setStore(STORE)
          .build();

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
  private StringWriter stringWriter;
  private PrintWriter writer;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    helper.setUp();
    helper.setEnvIsLoggedIn(true);
    datastore = DatastoreServiceFactory.getDatastoreService();

    // Create a fixed time clock that always returns the same instant.
    clock = Clock.fixed(Instant.parse(INSTANT), ZoneId.systemDefault());

    stringWriter = new StringWriter();
    writer = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(writer);

    servlet = new UploadReceiptServlet(blobstoreService, blobInfoFactory, datastore, clock);
  }

  @After
  public void tearDown() {
    helper.tearDown();
  }

  @Test
  public void doGetReturnsBlobstoreUploadUrl() throws IOException {
    UploadOptions uploadOptions =
        UploadOptions.Builder.withMaxUploadSizeBytesPerBlob(MAX_UPLOAD_SIZE_BYTES);
    when(blobstoreService.createUploadUrl("/upload-receipt", uploadOptions)).thenReturn(UPLOAD_URL);

    servlet.doGet(request, response);
    writer.flush();

    Assert.assertEquals(UPLOAD_URL + "\n", stringWriter.toString());
  }

  @Test
  public void doPostUploadsReceiptToDatastoreLiveServer()
      throws IOException, ReceiptAnalysisException {
    createMockBlob(request, VALID_CONTENT_TYPE, VALID_FILENAME, IMAGE_SIZE_1MB);
    stubRequestBody(request, PRICE, PAST_TIMESTAMP);
    stubUrlComponents(
        request, LIVE_SERVER_SCHEME, LIVE_SERVER_NAME, LIVE_SERVER_PORT, LIVE_SERVER_CONTEXT_PATH);

    // Mock receipt analysis.
    mockStatic(ReceiptAnalysis.class);
    when(ReceiptAnalysis.analyzeImageAt(new URL(LIVE_SERVER_ABSOLUTE_URL)))
        .thenReturn(ANALYSIS_RESULTS);

    servlet.doPost(request, response);

    Query query = new Query("Receipt");
    PreparedQuery results = datastore.prepare(query);
    Entity receipt = results.asSingleEntity();

    Assert.assertEquals(IMAGE_URL, receipt.getProperty("imageUrl"));
    Assert.assertEquals(PRICE, receipt.getProperty("price"));
    Assert.assertEquals(STORE, receipt.getProperty("store"));
    Assert.assertEquals(RAW_TEXT, receipt.getProperty("rawText"));
    Assert.assertEquals(BLOB_KEY, receipt.getProperty("blobKey"));
    Assert.assertEquals(PAST_TIMESTAMP, receipt.getProperty("timestamp"));
    Assert.assertEquals(CATEGORIES_COLLECTION, receipt.getProperty("categories"));
    Assert.assertEquals(USER_ID, receipt.getProperty("userId"));

    String response = TestUtils.extractProperties(stringWriter.toString());
    String expectedResponse = createReceiptEntity(IMAGE_URL, PRICE, STORE, RAW_TEXT, BLOB_KEY,
        PAST_TIMESTAMP, CATEGORIES_COLLECTION, USER_ID);
    Assert.assertEquals(expectedResponse, response);
  }

  @Test
  public void doPostUploadsReceiptToDatastoreDevServer()
      throws IOException, ReceiptAnalysisException {
    createMockBlob(request, VALID_CONTENT_TYPE, VALID_FILENAME, IMAGE_SIZE_1MB);
    stubRequestBody(request, PRICE, PAST_TIMESTAMP);
    stubUrlComponents(
        request, DEV_SERVER_SCHEME, DEV_SERVER_NAME, DEV_SERVER_PORT, DEV_SERVER_CONTEXT_PATH);

    // Mock receipt analysis.
    mockStatic(ReceiptAnalysis.class);
    when(ReceiptAnalysis.analyzeImageAt(BLOB_KEY)).thenReturn(ANALYSIS_RESULTS);

    servlet.doPost(request, response);

    Query query = new Query("Receipt");
    PreparedQuery results = datastore.prepare(query);
    Entity receipt = results.asSingleEntity();

    Assert.assertEquals(IMAGE_URL, receipt.getProperty("imageUrl"));
    Assert.assertEquals(PRICE, receipt.getProperty("price"));
    Assert.assertEquals(STORE, receipt.getProperty("store"));
    Assert.assertEquals(RAW_TEXT, receipt.getProperty("rawText"));
    Assert.assertEquals(BLOB_KEY, receipt.getProperty("blobKey"));
    Assert.assertEquals(CATEGORIES_COLLECTION, receipt.getProperty("categories"));
    Assert.assertEquals(PAST_TIMESTAMP, receipt.getProperty("timestamp"));
    Assert.assertEquals(USER_ID, receipt.getProperty("userId"));

    String response = TestUtils.extractProperties(stringWriter.toString());
    String expectedResponse = createReceiptEntity(IMAGE_URL, PRICE, STORE, RAW_TEXT, BLOB_KEY,
        PAST_TIMESTAMP, CATEGORIES_COLLECTION, USER_ID);
    Assert.assertEquals(expectedResponse, response);
  }

  @Test
  public void doPostSanitizesStore() throws IOException, ReceiptAnalysisException {
    createMockBlob(request, VALID_CONTENT_TYPE, VALID_FILENAME, IMAGE_SIZE_1MB);
    stubRequestBody(request, PRICE, PAST_TIMESTAMP);
    stubUrlComponents(
        request, LIVE_SERVER_SCHEME, LIVE_SERVER_NAME, LIVE_SERVER_PORT, LIVE_SERVER_CONTEXT_PATH);

    // Mock receipt analysis.
    String store = "    TraDeR   JOE's  ";
    AnalysisResults analysisResults = new AnalysisResults.Builder(RAW_TEXT.getValue())
                                          .setCategories(GENERATED_CATEGORIES)
                                          .setStore(store)
                                          .build();
    mockStatic(ReceiptAnalysis.class);
    when(ReceiptAnalysis.analyzeImageAt(new URL(LIVE_SERVER_ABSOLUTE_URL)))
        .thenReturn(analysisResults);

    servlet.doPost(request, response);

    Query query = new Query("Receipt");
    PreparedQuery results = datastore.prepare(query);
    Entity receipt = results.asSingleEntity();

    String expectedStore = "trader joe's";
    Assert.assertEquals(expectedStore, receipt.getProperty("store"));
  }

  @Test
  public void doPostUploadsReceiptWithoutLogo() throws IOException, ReceiptAnalysisException {
    helper.setEnvIsLoggedIn(true);

    createMockBlob(request, VALID_CONTENT_TYPE, VALID_FILENAME, IMAGE_SIZE_1MB);
    stubRequestBody(request, PRICE, PAST_TIMESTAMP);
    stubUrlComponents(
        request, LIVE_SERVER_SCHEME, LIVE_SERVER_NAME, LIVE_SERVER_PORT, LIVE_SERVER_CONTEXT_PATH);

    // Mock receipt analysis.
    AnalysisResults analysisResults = new AnalysisResults.Builder(RAW_TEXT.getValue())
                                          .setCategories(GENERATED_CATEGORIES)
                                          .build();
    mockStatic(ReceiptAnalysis.class);
    when(ReceiptAnalysis.analyzeImageAt(new URL(LIVE_SERVER_ABSOLUTE_URL)))
        .thenReturn(analysisResults);

    servlet.doPost(request, response);

    Query query = new Query("Receipt");
    PreparedQuery results = datastore.prepare(query);
    Entity receipt = results.asSingleEntity();

    Assert.assertFalse(receipt.hasProperty("store"));
  }

  @Test
  public void doPostSanitizesCategories() throws IOException, ReceiptAnalysisException {
    createMockBlob(request, VALID_CONTENT_TYPE, VALID_FILENAME, IMAGE_SIZE_1MB);
    stubRequestBody(request, PRICE, PAST_TIMESTAMP);
    stubUrlComponents(
        request, LIVE_SERVER_SCHEME, LIVE_SERVER_NAME, LIVE_SERVER_PORT, LIVE_SERVER_CONTEXT_PATH);

    // Mock receipt analysis.
    Set<String> generatedCategories =
        ImmutableSet.of("   fast   Food ", " Burger ", "  rEstaUrAnt ", "    LUNCH", "  dIninG ");
    AnalysisResults analysisResults = new AnalysisResults.Builder(RAW_TEXT.getValue())
                                          .setCategories(generatedCategories)
                                          .setStore(STORE)
                                          .build();
    mockStatic(ReceiptAnalysis.class);
    when(ReceiptAnalysis.analyzeImageAt(new URL(LIVE_SERVER_ABSOLUTE_URL)))
        .thenReturn(analysisResults);

    servlet.doPost(request, response);

    Query query = new Query("Receipt");
    PreparedQuery results = datastore.prepare(query);
    Entity receipt = results.asSingleEntity();

    Collection<String> expectedCategories =
        Arrays.asList("fast food", "burger", "restaurant", "lunch", "dining");
    Assert.assertEquals(expectedCategories, receipt.getProperty("categories"));
  }

  @Test
  public void doPostThrowsIfFileNotSelectedLiveServer() throws IOException {
    createMockBlob(request, VALID_CONTENT_TYPE, VALID_FILENAME, IMAGE_SIZE_0MB);

    servlet.doPost(request, response);
    writer.flush();

    Assert.assertEquals(FILE_NOT_SELECTED_LIVE_SERVER_WARNING, stringWriter.toString());
    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);

    verify(blobstoreService).delete(BLOB_KEY);
  }

  @Test
  public void doPostThrowsIfFileNotSelectedDevServer() throws IOException {
    Map<String, List<BlobKey>> blobs = new HashMap<>();
    when(blobstoreService.getUploads(request)).thenReturn(blobs);

    servlet.doPost(request, response);
    writer.flush();

    Assert.assertEquals(FILE_NOT_SELECTED_DEV_SERVER_WARNING, stringWriter.toString());
    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }

  @Test
  public void doPostThrowsIfInvalidFile() throws IOException {
    createMockBlob(request, INVALID_CONTENT_TYPE, INVALID_FILENAME, IMAGE_SIZE_1MB);

    servlet.doPost(request, response);
    writer.flush();

    Assert.assertEquals(INVALID_FILE_WARNING, stringWriter.toString());
    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);

    verify(blobstoreService).delete(BLOB_KEY);
  }

  public void doPostThrowsIfUserIsLoggedOut() throws IOException {
    helper.setEnvIsLoggedIn(false);
    createMockBlob(request, VALID_CONTENT_TYPE, VALID_FILENAME, IMAGE_SIZE_1MB);

    servlet.doPost(request, response);
    writer.flush();

    Assert.assertEquals(USER_NOT_LOGGED_IN_WARNING, stringWriter.toString());
    verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);

    verify(blobstoreService).delete(BLOB_KEY);
  }

  @Test
  public void doPostThrowsIfDateIsInTheFuture() throws IOException, ReceiptAnalysisException {
    createMockBlob(request, VALID_CONTENT_TYPE, VALID_FILENAME, IMAGE_SIZE_1MB);
    long futureTimestamp = Instant.parse(INSTANT).plusMillis(1234).toEpochMilli();
    when(request.getParameter("date")).thenReturn(Long.toString(futureTimestamp));
    stubUrlComponents(
        request, LIVE_SERVER_SCHEME, LIVE_SERVER_NAME, LIVE_SERVER_PORT, LIVE_SERVER_CONTEXT_PATH);

    // Mock receipt analysis.
    mockStatic(ReceiptAnalysis.class);
    when(ReceiptAnalysis.analyzeImageAt(new URL(LIVE_SERVER_ABSOLUTE_URL)))
        .thenReturn(ANALYSIS_RESULTS);

    servlet.doPost(request, response);
    writer.flush();

    Assert.assertEquals(INVALID_DATE_RANGE_WARNING, stringWriter.toString());
    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }

  @Test
  public void doPostThrowsIfInvalidDateFormat() throws IOException, ReceiptAnalysisException {
    createMockBlob(request, VALID_CONTENT_TYPE, VALID_FILENAME, IMAGE_SIZE_1MB);
    stubUrlComponents(
        request, LIVE_SERVER_SCHEME, LIVE_SERVER_NAME, LIVE_SERVER_PORT, LIVE_SERVER_CONTEXT_PATH);

    when(request.getParameter("date")).thenReturn(INVALID_DATE_TYPE);

    // Mock receipt analysis.
    mockStatic(ReceiptAnalysis.class);
    when(ReceiptAnalysis.analyzeImageAt(new URL(LIVE_SERVER_ABSOLUTE_URL)))
        .thenReturn(ANALYSIS_RESULTS);

    servlet.doPost(request, response);
    writer.flush();

    Assert.assertEquals(INVALID_DATE_FORMAT_WARNING, stringWriter.toString());
    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }

  @Test
  public void doPostThrowsIfReceiptAnalysisFails() throws IOException, ReceiptAnalysisException {
    createMockBlob(request, VALID_CONTENT_TYPE, VALID_FILENAME, IMAGE_SIZE_1MB);
    stubUrlComponents(
        request, LIVE_SERVER_SCHEME, LIVE_SERVER_NAME, LIVE_SERVER_PORT, LIVE_SERVER_CONTEXT_PATH);

    // Mock receipt analysis exception.
    mockStatic(ReceiptAnalysis.class);
    when(ReceiptAnalysis.analyzeImageAt(new URL(LIVE_SERVER_ABSOLUTE_URL)))
        .thenThrow(IOException.class);

    servlet.doPost(request, response);
    writer.flush();

    Assert.assertEquals(RECEIPT_ANALYSIS_FAILED_WARNING, stringWriter.toString());
    verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

    verify(blobstoreService).delete(BLOB_KEY);
  }

  @Test
  public void doPostRoundPrice() throws IOException, ReceiptAnalysisException {
    createMockBlob(request, VALID_CONTENT_TYPE, VALID_FILENAME, IMAGE_SIZE_1MB);

    double price = 17.236;
    double roundedPrice = 17.24;
    stubRequestBody(request, price, PAST_TIMESTAMP);
    stubUrlComponents(
        request, LIVE_SERVER_SCHEME, LIVE_SERVER_NAME, LIVE_SERVER_PORT, LIVE_SERVER_CONTEXT_PATH);

    // Mock receipt analysis.
    mockStatic(ReceiptAnalysis.class);
    when(ReceiptAnalysis.analyzeImageAt(new URL(LIVE_SERVER_ABSOLUTE_URL)))
        .thenReturn(ANALYSIS_RESULTS);

    servlet.doPost(request, response);

    Query query = new Query("Receipt");
    PreparedQuery results = datastore.prepare(query);
    Entity receipt = results.asSingleEntity();

    Assert.assertEquals(roundedPrice, receipt.getProperty("price"));
  }

  @Test
  public void doPostThrowsIfPriceNotParsable() throws IOException, ReceiptAnalysisException {
    createMockBlob(request, VALID_CONTENT_TYPE, VALID_FILENAME, IMAGE_SIZE_1MB);
    stubUrlComponents(
        request, LIVE_SERVER_SCHEME, LIVE_SERVER_NAME, LIVE_SERVER_PORT, LIVE_SERVER_CONTEXT_PATH);
    when(request.getParameter("date")).thenReturn(Long.toString(PAST_TIMESTAMP));

    String invalidPrice = "text";
    when(request.getParameter("price")).thenReturn(invalidPrice);

    // Mock receipt analysis.
    mockStatic(ReceiptAnalysis.class);
    when(ReceiptAnalysis.analyzeImageAt(new URL(LIVE_SERVER_ABSOLUTE_URL)))
        .thenReturn(ANALYSIS_RESULTS);

    servlet.doPost(request, response);
    writer.flush();

    Assert.assertEquals(PRICE_NOT_PARSABLE_WARNING, stringWriter.toString());
    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }

  @Test
  public void doPostThrowsIfPriceNegative() throws IOException, ReceiptAnalysisException {
    createMockBlob(request, VALID_CONTENT_TYPE, VALID_FILENAME, IMAGE_SIZE_1MB);
    stubUrlComponents(
        request, LIVE_SERVER_SCHEME, LIVE_SERVER_NAME, LIVE_SERVER_PORT, LIVE_SERVER_CONTEXT_PATH);
    when(request.getParameter("date")).thenReturn(Long.toString(PAST_TIMESTAMP));

    String negativePrice = "-12.55";
    when(request.getParameter("price")).thenReturn(negativePrice);

    // Mock receipt analysis.
    mockStatic(ReceiptAnalysis.class);
    when(ReceiptAnalysis.analyzeImageAt(new URL(LIVE_SERVER_ABSOLUTE_URL)))
        .thenReturn(ANALYSIS_RESULTS);

    servlet.doPost(request, response);
    writer.flush();

    Assert.assertEquals(PRICE_NEGATIVE_WARNING, stringWriter.toString());
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
   * Creates an entity with the given properties and converts it to JSON format.
   */
  private String createReceiptEntity(String imageUrl, double price, String store, Text rawText,
      BlobKey blobKey, long timestamp, Collection<String> categories, String userId) {
    Entity receipt = new Entity("Receipt");
    receipt.setUnindexedProperty("imageUrl", imageUrl);
    receipt.setUnindexedProperty("rawText", rawText);
    receipt.setProperty("categories", categories);
    receipt.setUnindexedProperty("blobKey", blobKey);
    receipt.setProperty("timestamp", timestamp);
    receipt.setProperty("store", store);
    receipt.setProperty("price", price);
    receipt.setProperty("userId", userId);

    String json = new Gson().toJson(receipt);

    return TestUtils.extractProperties(json) + "\n";
  }

  /**
   * Stubs the request with the given price and date parameters.
   */
  private void stubRequestBody(HttpServletRequest request, double price, long timestamp) {
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
