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

import com.google.api.gax.rpc.ApiException;
import com.google.appengine.api.blobstore.BlobInfo;
import com.google.appengine.api.blobstore.BlobInfoFactory;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.BlobstoreService;
import com.google.appengine.api.blobstore.BlobstoreServiceFactory;
import com.google.cloud.language.v1.ClassificationCategory;
import com.google.cloud.language.v1.ClassifyTextRequest;
import com.google.cloud.language.v1.ClassifyTextResponse;
import com.google.cloud.language.v1.Document;
import com.google.cloud.language.v1.Document.Type;
import com.google.cloud.language.v1.LanguageServiceClient;
import com.google.cloud.vision.v1.AnnotateImageRequest;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.BatchAnnotateImagesResponse;
import com.google.cloud.vision.v1.EntityAnnotation;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.Image;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.protobuf.ByteString;
import com.google.sps.data.AnalysisResults;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Class with a method that returns the text of the specified image using the Cloud Vision API.
 */
public class ReceiptAnalysis {
  /** Returns the text of the image at the requested URL. */
  public static AnalysisResults serveImageText(URL url)
      throws IOException, ReceiptAnalysisException {
    ByteString imageBytes = readImageBytes(url);

    return analyzeImage(imageBytes);
  }

  /** Returns the text of the image at the requested blob key. */
  public static AnalysisResults serveImageText(BlobKey blobKey)
      throws IOException, ReceiptAnalysisException {
    ByteString imageBytes = readImageBytes(blobKey);

    return analyzeImage(imageBytes);
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

  /** Analyzes the image represented by the given ByteString. */
  private static AnalysisResults analyzeImage(ByteString imageBytes)
      throws IOException, ReceiptAnalysisException {
    String rawText = retrieveText(imageBytes);
    ImmutableSet<String> categories = categorizeText(rawText);
    AnalysisResults results = new AnalysisResults(rawText, categories);

    return results;
  }

  /** Detects and retrieves text in the provided image. */
  private static String retrieveText(ByteString imageBytes)
      throws IOException, ReceiptAnalysisException {
    String rawText = "";

    Image image = Image.newBuilder().setContent(imageBytes).build();
    Feature feature = Feature.newBuilder().setType(Feature.Type.TEXT_DETECTION).build();
    AnnotateImageRequest request =
        AnnotateImageRequest.newBuilder().addFeatures(feature).setImage(image).build();
    ImmutableList<AnnotateImageRequest> requests = ImmutableList.of(request);

    try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {
      BatchAnnotateImagesResponse batchResponse = client.batchAnnotateImages(requests);

      if (batchResponse.getResponsesList().isEmpty()) {
        throw new ReceiptAnalysisException("Received empty batch image annotation response.");
      }

      AnnotateImageResponse response = Iterables.getOnlyElement(batchResponse.getResponsesList());

      if (response.hasError()) {
        throw new ReceiptAnalysisException("Received image annotation response with error.");
      } else if (response.getTextAnnotationsList().isEmpty()) {
        throw new ReceiptAnalysisException(
            "Received image annotation response without text annotations.");
      }

      // First element has the entire raw text from the image
      EntityAnnotation annotation = response.getTextAnnotationsList().get(0);

      rawText = annotation.getDescription();
    } catch (ApiException e) {
      throw new ReceiptAnalysisException("Image annotation request failed.", e);
    }

    return rawText;
  }

  /** Generates categories for the provided text. */
  private static ImmutableSet<String> categorizeText(String text) throws IOException {
    ImmutableSet<String> categories = ImmutableSet.of();

    try (LanguageServiceClient client = LanguageServiceClient.create()) {
      Document document = Document.newBuilder().setContent(text).setType(Type.PLAIN_TEXT).build();
      ClassifyTextRequest request = ClassifyTextRequest.newBuilder().setDocument(document).build();

      // TODO: Check if ApiException was thrown
      ClassifyTextResponse response = client.classifyText(request);

      // TODO: Parse category strings into more natural categories
      categories = response.getCategoriesList()
                       .stream()
                       .map(category -> category.getName())
                       .collect(ImmutableSet.toImmutableSet());
    }

    return categories;
  }

  public static class ReceiptAnalysisException extends Exception {
    public ReceiptAnalysisException(String errorMessage, Throwable err) {
      super(errorMessage, err);
    }

    public ReceiptAnalysisException(String errorMessage) {
      super(errorMessage);
    }
  }
}