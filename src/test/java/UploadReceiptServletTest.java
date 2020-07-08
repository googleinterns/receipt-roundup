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

import static org.mockito.Mockito.*;
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
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Text;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.gson.Gson;
import com.google.sps.data.AnalysisResults;
import com.google.sps.servlets.ReceiptAnalysis;
import com.google.sps.servlets.UploadReceiptServlet;
import java.io.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.*;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(ReceiptAnalysis.class)
public final class UploadReceiptServletTest {
  private UploadReceiptServlet servlet;
  private DatastoreService datastore;
  private Clock clock;
  @Mock private BlobstoreService blobstoreService;
  @Mock private BlobInfoFactory blobInfoFactory;
  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;
  // Uses local Datastore.
  private final LocalServiceTestHelper helper =
      new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig());

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    helper.setUp();
    datastore = DatastoreServiceFactory.getDatastoreService();

    // Create a fixed time clock that always returns the same instant.
    String instantExpected = "2020-06-22T10:15:30Z";
    clock = Clock.fixed(Instant.parse(instantExpected), ZoneId.systemDefault());

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

    String expectedUploadUrl = "/blobstore/upload-receipt";
    final long MAX_UPLOAD_SIZE_BYTES = 5 * 1024 * 1024;
    UploadOptions uploadOptions =
        UploadOptions.Builder.withMaxUploadSizeBytesPerBlob(MAX_UPLOAD_SIZE_BYTES);
    when(blobstoreService.createUploadUrl("/upload-receipt", uploadOptions))
        .thenReturn(expectedUploadUrl);

    servlet.doGet(request, response);
    writer.flush();
    Assert.assertTrue(stringWriter.toString().contains(expectedUploadUrl));
  }

  @Test
  public void doPostUploadsReceiptToDatastoreLiveServer() throws IOException {
    BlobKey blobKey = new BlobKey("blobKey");
    String filename = "image.jpg";
    long imageSize = 1024 * 1024;
    String hash = "35454B055CC325EA1AF2126E27707052";

    // Add mock blob to Blobstore.
    Map<String, List<BlobKey>> blobs = new HashMap<>();
    blobs.put("receipt-image", Arrays.asList(blobKey));
    when(blobstoreService.getUploads(request)).thenReturn(blobs);
    BlobInfo blobInfo =
        new BlobInfo(blobKey, "image/jpeg", new Date(), filename, imageSize, hash, null);
    when(blobInfoFactory.loadBlobInfo(blobKey)).thenReturn(blobInfo);

    String label = "Label";
    when(request.getParameter("label")).thenReturn(label);

    // Stub request with URL components.
    when(request.getScheme()).thenReturn("https");
    when(request.getServerName()).thenReturn("capstone-receipt-step-2020.uc.r.appspot.com");
    when(request.getServerPort()).thenReturn(80);
    when(request.getContextPath()).thenReturn("");
    String imageUrl = "/serve-image?blob-key=" + blobKey.getKeyString();
    String absoluteUrl = "https://capstone-receipt-step-2020.uc.r.appspot.com:80" + imageUrl;

    // Mock receipt analysis.
    String rawText = "raw text";
    mockStatic(ReceiptAnalysis.class);
    when(ReceiptAnalysis.serveImageText(absoluteUrl)).thenReturn(new AnalysisResults(rawText));

    servlet.doPost(request, response);

    Query query = new Query("Receipt");
    PreparedQuery results = datastore.prepare(query);
    Entity receipt = results.asSingleEntity();

    long timestamp = clock.instant().toEpochMilli();
    double price = 5.89;
    String store = "McDonald's";

    Assert.assertEquals(receipt.getProperty("imageUrl"), imageUrl);
    Assert.assertEquals(receipt.getProperty("price"), price);
    Assert.assertEquals(receipt.getProperty("store"), store);
    Assert.assertEquals(receipt.getProperty("rawText"), new Text(rawText));
    Assert.assertEquals(receipt.getProperty("blobKey"), blobKey);
    Assert.assertEquals(receipt.getProperty("timestamp"), timestamp);
    Assert.assertEquals(receipt.getProperty("label"), label);
  }

  // TODO: Test exceptions and dev server.
}