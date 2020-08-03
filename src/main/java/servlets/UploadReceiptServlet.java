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
import com.google.appengine.api.datastore.Text;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.sps.data.AnalysisResults;
import com.google.sps.servlets.FormatUtils;
import com.google.sps.servlets.FormatUtils.InvalidDateException;
import com.google.sps.servlets.FormatUtils.InvalidPriceException;
import com.google.sps.servlets.ReceiptAnalysis.ReceiptAnalysisException;
import java.io.IOException;
import java.net.URL;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet with a GET handler that creates a URL that uploads a receipt image to Blobstore and
 * a POST handler that extracts data from the image and inserts it into Datastore.
 */
@WebServlet("/upload-receipt")
public class UploadReceiptServlet extends HttpServlet {
  // Max upload size of 10 MB.
  private static final long MAX_UPLOAD_SIZE_BYTES = 10 * 1024 * 1024;
  // Base URL for the web app running on the Cloud Shell dev server.
  private static final String DEV_SERVER_BASE_URL = "http://0.0.0.0:80";
  // Matches JPEG image filenames.
  private static final Pattern validFilename = Pattern.compile("([^\\s]+(\\.(?i)(jpe?g))$)");

  // Logs to System.err by default.
  private static final Logger logger = Logger.getLogger(UploadReceiptServlet.class.getName());
  private final BlobstoreService blobstoreService;
  private final BlobInfoFactory blobInfoFactory;
  private final DatastoreService datastore;
  private final UserService userService = UserServiceFactory.getUserService();
  private final Clock clock;

  public UploadReceiptServlet() {
    this.blobstoreService = BlobstoreServiceFactory.getBlobstoreService();
    this.blobInfoFactory = new BlobInfoFactory();
    this.datastore = DatastoreServiceFactory.getDatastoreService();
    this.clock = Clock.systemDefaultZone();
  }

  public UploadReceiptServlet(BlobstoreService blobstoreService, BlobInfoFactory blobInfoFactory,
      DatastoreService datastore, Clock clock) {
    this.blobstoreService = blobstoreService;
    this.blobInfoFactory = blobInfoFactory;
    this.datastore = datastore;
    this.clock = clock;
  }

  /**
   * Creates a URL that uploads the receipt image to Blobstore when the user submits the upload
   * form. After Blobstore handles the parsing, storing, and hosting of the image, the form
   * data and a URL where the image can be accessed is forwarded to this servlet in a POST
   * request.
   */
  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    UploadOptions uploadOptions =
        UploadOptions.Builder.withMaxUploadSizeBytesPerBlob(MAX_UPLOAD_SIZE_BYTES);
    String uploadUrl = blobstoreService.createUploadUrl("/upload-receipt", uploadOptions);

    response.setContentType("text/html");
    response.getWriter().println(uploadUrl);
  }

  /**
   * When the user submits the upload form, Blobstore processes the image and then forwards the
   * request to this servlet, which analyzes the receipt image and inserts information
   * about the receipt into Datastore. The JSON response contains the receipt that was added.
   */
  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    Entity receipt = null;

    try {
      receipt = createReceiptEntity(request);
    } catch (FileNotSelectedException | InvalidFileException | InvalidPriceException
        | InvalidDateException e) {
      logger.warning(e.toString());
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      response.getWriter().println(e.toString());
      return;
    } catch (UserNotLoggedInException e) {
      logger.warning(e.toString());
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      response.getWriter().println(e.toString());
      return;
    } catch (ReceiptAnalysisException e) {
      logger.warning(e.toString());
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      response.getWriter().println(e.toString());
      return;
    }

    // Store the receipt entity in Datastore.
    datastore.put(receipt);

    // Convert the receipt to JSON.
    String json = new Gson().toJson(receipt);

    // Send the JSON as the response.
    response.setContentType("application/json;");
    response.getWriter().println(json);
  }

  /**
   * Creates and returns a receipt entity, which includes the receipt image and
   * information about the receipt.
   */
  private Entity createReceiptEntity(HttpServletRequest request)
      throws FileNotSelectedException, InvalidFileException, UserNotLoggedInException,
             InvalidPriceException, InvalidDateException, ReceiptAnalysisException {
    BlobKey blobKey = getUploadedBlobKey(request, "receipt-image");

    if (!userService.isUserLoggedIn()) {
      blobstoreService.delete(blobKey);
      throw new UserNotLoggedInException("User must be logged in to upload a receipt.");
    }

    String userId = userService.getCurrentUser().getUserId();

    // Populate a receipt entity with the information extracted from the image with Cloud Vision.
    Entity receipt = analyzeReceiptImage(blobKey, request);
    receipt.setProperty("userId", userId);

    return receipt;
  }

  /**
   * Returns a blob key that points to the uploaded file.
   */
  private BlobKey getUploadedBlobKey(HttpServletRequest request, String formInputElementName)
      throws FileNotSelectedException, InvalidFileException {
    Map<String, List<BlobKey>> blobs = blobstoreService.getUploads(request);
    List<BlobKey> blobKeys = blobs.get(formInputElementName);

    // User submitted the form without selecting a file. (dev server)
    if (blobKeys == null || blobKeys.isEmpty()) {
      throw new FileNotSelectedException("No file was uploaded by the user (dev server).");
    }

    // The form only contains a single file input, so get the first index.
    BlobKey blobKey = blobKeys.get(0);

    // User submitted the form without selecting a file. (live server)
    BlobInfo blobInfo = blobInfoFactory.loadBlobInfo(blobKey);
    if (blobInfo.getSize() == 0) {
      blobstoreService.delete(blobKey);
      throw new FileNotSelectedException("No file was uploaded by the user (live server).");
    }

    String filename = blobInfo.getFilename();
    if (!isValidFilename(filename)) {
      blobstoreService.delete(blobKey);
      throw new InvalidFileException("Uploaded file must be a JPEG image.");
    }

    return blobKey;
  }

  /**
   * Checks if the filename is a valid JPEG file.
   */
  private static boolean isValidFilename(String filename) {
    return validFilename.matcher(filename).matches();
  }

  /**
   * Extracts the raw text from the image with the Cloud Vision API. Returns a receipt
   * entity populated with the extracted fields.
   */
  private Entity analyzeReceiptImage(BlobKey blobKey, HttpServletRequest request)
      throws ReceiptAnalysisException, InvalidPriceException, InvalidDateException {
    String imageUrl = getBlobServingUrl(blobKey);
    String baseUrl = getBaseUrl(request);

    AnalysisResults results = null;

    try {
      // For the dev server, authentication is required to access the image served at the URL, so
      // fetch the bytes directly from Blobstore instead.
      if (baseUrl.equals(DEV_SERVER_BASE_URL)) {
        results = ReceiptAnalysis.analyzeImageAt(blobKey);
      } else {
        URL absoluteUrl = new URL(baseUrl + imageUrl);
        results = ReceiptAnalysis.analyzeImageAt(absoluteUrl);
      }
    } catch (IOException e) {
      blobstoreService.delete(blobKey);
      throw new ReceiptAnalysisException("Receipt analysis failed.", e);
    }

    // Create an entity with a kind of Receipt.
    Entity receipt = new Entity("Receipt");
    receipt.setUnindexedProperty("imageUrl", imageUrl);

    // Set the timestamp if a date was parsed.
    results.getTransactionTimestamp().ifPresent(timestamp -> {
      try {
        FormatUtils.checkTimestampIsInPast(timestamp, clock);
      } catch (InvalidDateException exception) {
        // Don't add timestamp property if parsing is invalid.
        return;
      }

      receipt.setProperty("timestamp", timestamp);
    });

    // Set the price if it was parsed.
    results.getPrice().ifPresent(price -> {
      try {
        receipt.setProperty("price", FormatUtils.roundPrice(price));
      } catch (InvalidPriceException exception) {
        // Don't add price property if parsing is invalid
        return;
      }
    });

    // Text objects wrap around a string of unlimited size while strings are limited to 1500 bytes.
    receipt.setUnindexedProperty("rawText", new Text(results.getRawText()));
    receipt.setProperty(
        "categories", FormatUtils.sanitizeCategories(results.getCategories().stream()));
    // If a logo was detected, set the store name.
    results.getStore().ifPresent(
        store -> receipt.setProperty("store", FormatUtils.sanitize(store)));

    return receipt;
  }

  /**
   * Gets a URL that serves the blob file using the blob key.
   */
  private String getBlobServingUrl(BlobKey blobKey) {
    return "/serve-image?blob-key=" + blobKey.getKeyString();
  }

  /**
   * Get the base URL of the web application.
   */
  private String getBaseUrl(HttpServletRequest request) {
    String baseUrl = request.getScheme() + "://" + request.getServerName() + ":"
        + request.getServerPort() + request.getContextPath();

    return baseUrl;
  }

  public static class InvalidFileException extends Exception {
    public InvalidFileException(String errorMessage) {
      super(errorMessage);
    }
  }

  public static class FileNotSelectedException extends Exception {
    public FileNotSelectedException(String errorMessage) {
      super(errorMessage);
    }
  }

  public static class UserNotLoggedInException extends Exception {
    public UserNotLoggedInException(String errorMessage) {
      super(errorMessage);
    }
  }
}
