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
import com.google.cloud.vision.v1.AnnotateImageRequest;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.BatchAnnotateImagesResponse;
import com.google.cloud.vision.v1.EntityAnnotation;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.Image;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.protobuf.ByteString;
import com.google.sps.data.AnalysisResults;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Class with a method that returns the text of the specified image using the Cloud Vision API.
 */
public class ReceiptAnalysis {
  /** Returns the text of the image at the requested URL. */
  public static AnalysisResults serveImageText(URL url) throws IOException {
    ByteString imageBytes = readImageBytes(url);
  
    String rawText = retrieveText(imageBytes);
    AnalysisResults results = new AnalysisResults(rawText);

    return results;
  }

  /** Returns the text of the image at the requested blob key. */
  public static AnalysisResults serveImageText(BlobKey blobKey) throws IOException {
    ByteString imageBytes = readImageBytes(blobKey);
  
    String rawText = retrieveText(imageBytes);
    AnalysisResults results = new AnalysisResults(rawText);

    return results;
  }

  /** Reads the image bytes from the URL. */
  private static ByteString readImageBytes(URL url) throws IOException {
    ByteString imageBytes;

    try (InputStream imageInputStream = url.openStream()) {
      imageBytes = ByteString.readFrom(imageInputStream);
    }

    return imageBytes;
  }

  /** Retrieves the binary data stored at the given blob key. */
  private static ByteString readImageBytes(BlobKey blobKey) throws IOException {
    BlobstoreService blobstoreService = BlobstoreServiceFactory.getBlobstoreService();
    BlobInfo blobInfo = new BlobInfoFactory().loadBlobInfo(blobKey);
    long blobSize = blobInfo.getSize();

    ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
    int fetchSize = BlobstoreService.MAX_BLOB_FETCH_SIZE;
    long currentByteIndex = 0;

    // Fetch all the bytes from the blob in fragments of the maximum fetch size.
    while (currentByteIndex < blobSize) {
      // End index is inclusive, so subtract 1 to get fetchSize bytes.
      // Get data starting at currentByteIndex until either the fetch size or
      // the end of the blob is reached.
      byte[] bytes =
          blobstoreService.fetchData(blobKey, currentByteIndex, currentByteIndex + fetchSize - 1);
      outputBytes.write(bytes);

      currentByteIndex += fetchSize;
    }

    return ByteString.copyFrom(outputBytes.toByteArray());
  }

  /** Detects and retrieves text in the provided image. */
  private static String retrieveText(ByteString imageBytes) throws IOException {
    String rawText = "";

    Image image = Image.newBuilder().setContent(imageBytes).build();
    Feature feature = Feature.newBuilder().setType(Feature.Type.TEXT_DETECTION).build();
    AnnotateImageRequest request =
        AnnotateImageRequest.newBuilder().addFeatures(feature).setImage(image).build();
    ImmutableList<AnnotateImageRequest> requests = ImmutableList.of(request);

    try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {
      // TODO: Throw custom exception from PR #12 if response has an error or is missing
      BatchAnnotateImagesResponse batchResponse = client.batchAnnotateImages(requests);
      AnnotateImageResponse response = Iterables.getOnlyElement(batchResponse.getResponsesList());

      // First element has the entire raw text from the image
      EntityAnnotation annotation = response.getTextAnnotationsList().get(0);

      rawText = annotation.getDescription();
    }

    return rawText;
  }

  public static class ReceiptAnalysisException extends Exception {
    public ReceiptAnalysisException(String errorMessage, Throwable err) {
      super(errorMessage, err);
    }
  }
}