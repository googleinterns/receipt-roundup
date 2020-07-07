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

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import static org.mockito.Mockito.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Calendar;
import com.google.appengine.api.blobstore.BlobstoreService;
import com.google.appengine.api.blobstore.UploadOptions;
import com.google.appengine.api.blobstore.UploadOptions.Builder;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.gson.Gson;
import com.google.sps.servlets.UploadReceiptServlet;
import java.io.*;
import javax.servlet.http.*;

@RunWith(JUnit4.class)
public final class UploadReceiptServletTest {

  private UploadReceiptServlet servlet;
  @Mock private BlobstoreService blobstoreService;
  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;
  private final LocalServiceTestHelper helper =
      new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig());

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    helper.setUp();
    servlet = new UploadReceiptServlet(blobstoreService);
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
    when(blobstoreService.createUploadUrl("/upload-receipt", uploadOptions)).thenReturn(expectedUploadUrl);

    servlet.doGet(request, response);
    writer.flush();
    Assert.assertTrue(stringWriter.toString().contains(expectedUploadUrl));
  }
}