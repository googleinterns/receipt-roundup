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
import com.google.protobuf.ByteString;
import com.google.sps.data.AnalysisResults;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Class with a method that returns the text of the specified image using the Cloud Vision API.
 */
public class ReceiptAnalysis {
  /** Returns the text of the image at the requested URL. */
  public static AnalysisResults serveImageText(String url) throws IOException {
    ByteString imgBytes = readImageBytes(url);

    return retrieveText(imgBytes);
  }

  /** Returns the text of the image at the requested blob key. */
  public static AnalysisResults serveImageText(BlobKey blobKey) throws IOException {
    ByteString imageBytes = readImageBytes(blobKey);

    return retrieveText(imageBytes);
  }

  /** Reads the image bytes from the URL. */
  private static ByteString readImageBytes(String url) throws IOException {
    ByteString imgBytes;

    try (InputStream imgInputStream = new URL(url).openStream()) {
      imgBytes = ByteString.readFrom(imgInputStream);
    }

    return imgBytes;
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
      byte[] b =
          blobstoreService.fetchData(blobKey, currentByteIndex, currentByteIndex + fetchSize - 1);
      outputBytes.write(b);

      currentByteIndex += fetchSize;
    }

    return ByteString.copyFrom(outputBytes.toByteArray());
  }

  /** Detects and retrieves text in the provided image. */
  private static AnalysisResults retrieveText(ByteString imgBytes) throws IOException {
    List<AnnotateImageRequest> requests = new ArrayList<>();
    String description = "";

    Image img = Image.newBuilder().setContent(imgBytes).build();
    Feature feat = Feature.newBuilder().setType(Feature.Type.TEXT_DETECTION).build();
    AnnotateImageRequest request =
        AnnotateImageRequest.newBuilder().addFeatures(feat).setImage(img).build();
    requests.add(request);

    try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {
      BatchAnnotateImagesResponse batchResponse = client.batchAnnotateImages(requests);
      AnnotateImageResponse response = batchResponse.getResponsesList().get(0);
      EntityAnnotation annotation = response.getTextAnnotationsList().get(0);

      description = annotation.getDescription();
    }

    return new AnalysisResults(description);
  }

  public static class ReceiptAnalysisException extends Exception {
    public ReceiptAnalysisException(String errorMessage, Throwable err) {
      super(errorMessage, err);
    }
  }
}