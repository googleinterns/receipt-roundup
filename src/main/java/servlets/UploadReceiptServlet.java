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

package com.google.sps.servlets;

import com.google.appengine.api.blobstore.BlobInfo;
import com.google.appengine.api.blobstore.BlobInfoFactory;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.BlobstoreService;
import com.google.appengine.api.blobstore.BlobstoreServiceFactory;
import com.google.appengine.api.blobstore.UploadOptions;
import com.google.appengine.api.blobstore.UploadOptions.Builder;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.sps.data.AnalysisResults;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.io.UnsupportedEncodingException;

/**
 * Servlet with a GET handler that creates a URL that uploads a receipt image to Blobstore and
 * a POST handler that extracts data from the image and inserts it into Datastore.
 */
@WebServlet("/upload-receipt")
public class UploadReceiptServlet extends HttpServlet {
  // Max upload size of 5 MB.
  private static final long MAX_UPLOAD_SIZE_BYTES = 5 * 1024 * 1024;
  // Matches JPEG image filenames.
  private static final Pattern validFilename = Pattern.compile("([^\\s]+(\\.(?i)(jpe?g))$)");
  // Logs to System.err by default.
  private static final Logger logger = Logger.getLogger(UploadReceiptServlet.class.getName());

  /**
   * Creates a URL that uploads the receipt image to Blobstore when the user submits the upload
   * form. After Blobstore handles the parsing, storing, and hosting of the image, the form
   * data and a URL where the image can be accessed is forwarded to this servlet in a POST
   * request.
   */
  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    BlobstoreService blobstoreService = BlobstoreServiceFactory.getBlobstoreService();
    UploadOptions uploadOptions =
        UploadOptions.Builder.withMaxUploadSizeBytesPerBlob(MAX_UPLOAD_SIZE_BYTES);
    String uploadUrl = blobstoreService.createUploadUrl("/upload-receipt", uploadOptions);

    response.setContentType("text/html");
    response.getWriter().println(uploadUrl);
  }

  /**
   * When the user submits the upload form, Blobstore processes the image and then forwards the
   * request to this servlet, which analyzes the receipt image and inserts information
   * about the receipt into Datastore.
   */
  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    Optional<Entity> receipt = createReceiptEntity(request);

    // Respond with status code 400, Bad Request.
    if (!receipt.isPresent()) {
      logger.warning("Valid JPEG file was not uploaded.");
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No JPEG file uploaded.");
      return;
    }

    // Store the receipt entity in Datastore.
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    datastore.put(receipt.get());
  }

  /**
   * Creates and returns a receipt entity, which includes the receipt image and
   * information about the receipt, or an empty Optional if the user didn't upload a JPEG file.
   */
  private Optional<Entity> createReceiptEntity(HttpServletRequest request) {
    Optional<BlobKey> blobKeyOption = getUploadedBlobKey(request, "receipt-image");

    if (!blobKeyOption.isPresent()) {
      return Optional.empty();
    }

    BlobKey blobKey = blobKeyOption.get();
    String imageUrl = getBlobServingUrl(blobKey);
    long timestamp = System.currentTimeMillis();
    String label = request.getParameter("label");

    // Create an entity with a kind of Receipt.
    Entity receipt = analyzeReceiptImage(imageUrl, request);

    if (receipt == null) {
      return Optional.empty();
    }

    receipt.setProperty("blobKey", blobKey);
    receipt.setProperty("imageUrl", imageUrl);
    receipt.setProperty("timestamp", timestamp);
    receipt.setProperty("label", label);

    return Optional.of(receipt);
  }

  /**
   * Returns a URL that points to the uploaded file, or an empty Optional if the user didn't upload
   * a JPEG file.
   */
  private Optional<BlobKey> getUploadedBlobKey(
      HttpServletRequest request, String formInputElementName) {
    BlobstoreService blobstoreService = BlobstoreServiceFactory.getBlobstoreService();
    Map<String, List<BlobKey>> blobs = blobstoreService.getUploads(request);
    List<BlobKey> blobKeys = blobs.get(formInputElementName);

    // User submitted the form without selecting a file. (dev server)
    if (blobKeys == null || blobKeys.isEmpty()) {
      return Optional.empty();
    }

    // The form only contains a single file input, so get the first index.
    BlobKey blobKey = blobKeys.get(0);

    // User submitted the form without selecting a file. (live server)
    BlobInfo blobInfo = new BlobInfoFactory().loadBlobInfo(blobKey);
    if (blobInfo.getSize() == 0) {
      blobstoreService.delete(blobKey);
      return Optional.empty();
    }

    // User uploaded a file that is not a JPEG.
    String filename = blobInfo.getFilename();
    if (!isValidFilename(filename)) {
      blobstoreService.delete(blobKey);
      return Optional.empty();
    }

    return Optional.of(blobKey);
  }

  /**
   * Checks if the filename is a valid JPEG file.
   */
  private static boolean isValidFilename(String filename) {
    return validFilename.matcher(filename).matches();
  }

  /**
   * Gets a URL that serves the blob file using the blob key.
   */
  private String getBlobServingUrl(BlobKey blobKey) {
    return "/serve-image?blob-key=" + blobKey.getKeyString();
  }

  /**
   * Extracts the raw text from the image with the Cloud Vision API
   */
  private Entity analyzeReceiptImage(String imageUrl, HttpServletRequest request) {
    AnalysisResults results = null;

imageUrl = getBaseUrl(request) + imageUrl;
System.out.println(imageUrl);
    try {
      results = ReceiptAnalysis.serveImageText(imageUrl);
    } catch(IOException e) {
      return null;
    }

    // TODO: Replace hard-coded values using receipt analysis with Cloud Vision.
    double price = 5.89;
    String store = "McDonald's";

    // Create an entity with a kind of Receipt.
    Entity receipt = new Entity("Receipt");
    receipt.setProperty("price", price);
    receipt.setProperty("store", store);
    receipt.setUnindexedProperty("rawText", results.getRawText());

    return receipt;
  }

  private static String encodeValue(String value) {
    try {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
    } catch (UnsupportedEncodingException ex) {
        throw new RuntimeException(ex.getCause());
    }
  }

  /**
   *
   */
  private static String getBaseUrl(HttpServletRequest request) {
    String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath();

    if (baseUrl.equals("http://0.0.0.0:80")) {
      baseUrl = "https://8080-9333ac09-1dd5-4f7f-8a7a-34f16c364c6b.us-east1.cloudshell.dev";
    }

    return baseUrl;
  }
}
