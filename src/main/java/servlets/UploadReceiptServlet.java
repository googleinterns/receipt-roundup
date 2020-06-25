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
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.images.ImagesService;
import com.google.appengine.api.images.ImagesServiceFactory;
import com.google.appengine.api.images.ServingUrlOptions;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
  // Matches JPEG image filenames.
  private static final Pattern validFilename = Pattern.compile("([^\\s]+(\\.(?i)(jpe?g))$)");

  /**
   * Creates a URL that uploads the receipt image to Blobstore when the user submits the upload
   * form. After Blobstore handles the parsing, storing, and hosting of the image, the form
   * data and a URL where the image can be accessed is forwarded to this servlet as a POST
   * request.
   */
  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    BlobstoreService blobstoreService = BlobstoreServiceFactory.getBlobstoreService();
    String uploadUrl = blobstoreService.createUploadUrl("/upload-receipt");

    response.setContentType("text/html");
    response.getWriter().println(uploadUrl);
  }

  /**
   * When the user submits the upload form, Blobstore processes the image and then forwards the
   * request to this servlet. This servlet analyzes the receipt image and inserts information
   * about the receipt into Datastore.
   */
  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    String imageUrl = getUploadedFileUrl(request, "receipt-image");

    // TODO: Replace dummy values using receipt analysis with Cloud Vision.
    String label = request.getParameter("label");
    double price = 5.89;
    String store = "McDonald's";
    String rawText = "McDonald’s Restaurant \n Order No. 389 \n "
        + "Qty Item Total \n 1 Big Mac 3.99 \n 1 M Iced Coffee 1.40 \n"
        + "Subtotal 5.39 \n Tax 0.50 \n Total 5.89";
    long timestamp = System.currentTimeMillis();

    // Create an entity with a kind of Receipt.
    Entity receipt = new Entity("Receipt");
    receipt.setProperty("imageUrl", imageUrl);
    receipt.setProperty("label", label);
    receipt.setProperty("price", price);
    receipt.setProperty("store", store);
    receipt.setProperty("rawText", rawText);
    receipt.setProperty("timestamp", timestamp);

    // Store the receipt entity in Datastore.
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    datastore.put(receipt);

    response.sendRedirect("/");
  }

  /**
   * Returns a URL that points to the uploaded file, or null if the user didn't upload a file.
   */
  private String getUploadedFileUrl(HttpServletRequest request, String formInputElementName) {
    BlobstoreService blobstoreService = BlobstoreServiceFactory.getBlobstoreService();
    Map<String, List<BlobKey>> blobs = blobstoreService.getUploads(request);
    List<BlobKey> blobKeys = blobs.get(formInputElementName);

    // User submitted the form without selecting a file. (dev server)
    if (blobKeys == null || blobKeys.isEmpty()) {
      return null;
    }

    // The form only contains a single file input, so get the first index.
    BlobKey blobKey = blobKeys.get(0);

    // User submitted the form without selecting a file. (live server)
    BlobInfo blobInfo = new BlobInfoFactory().loadBlobInfo(blobKey);
    if (blobInfo.getSize() == 0) {
      blobstoreService.delete(blobKey);
      return null;
    }

    // User uploaded a file that is not a JPEG.
    String filename = blobInfo.getFilename();
    if (!isValidFilename(filename)) {
      blobstoreService.delete(blobKey);
      return null;
    }

    // Use ImagesService to get a URL that points to the uploaded file.
    ImagesService imagesService = ImagesServiceFactory.getImagesService();
    ServingUrlOptions options = ServingUrlOptions.Builder.withBlobKey(blobKey);

    // To support running in Google Cloud Shell with AppEngine's devserver, we must use the relative
    // path to the image, rather than the path returned by imagesService which contains a host.
    try {
      URL url = new URL(imagesService.getServingUrl(options));
      return url.getPath();
    } catch (MalformedURLException e) {
      return imagesService.getServingUrl(options);
    }
  }

  /**
   * Checks if the filename is a valid JPEG file..
   */
  private static boolean isValidFilename(String filename) {
    return validFilename.matcher(filename).matches();
  }
}
