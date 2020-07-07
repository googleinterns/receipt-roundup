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
  public static AnalysisResults serveImageText(String url) throws IOException {
    ByteString imageBytes = readImageBytes(url);

    return retrieveText(imageBytes);
  }

  /** Reads the image bytes from the URL. */
  private static ByteString readImageBytes(String url) throws IOException {
    ByteString imageBytes;

    try (InputStream imageInputStream = new URL(url).openStream()) {
      imageBytes = ByteString.readFrom(imageInputStream);
    }

    return imageBytes;
  }

  /** Detects and retrieves text in the provided image. */
  private static AnalysisResults retrieveText(ByteString imageBytes) throws IOException {
    String description = "";

    Image image = Image.newBuilder().setContent(imageBytes).build();
    Feature feature = Feature.newBuilder().setType(Feature.Type.TEXT_DETECTION).build();
    AnnotateImageRequest request =
        AnnotateImageRequest.newBuilder().addFeatures(feature).setImage(image).build();
    ImmutableList<AnnotateImageRequest> requests = ImmutableList.of(request);

    try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {
      // TODO: Throw custom exception if response has an error or is missing
      BatchAnnotateImagesResponse batchResponse = client.batchAnnotateImages(requests);
      AnnotateImageResponse response = Iterables.getOnlyElement(batchResponse.getResponsesList());

      // First element has the entire raw text from the image
      EntityAnnotation annotation = response.getTextAnnotationsList().get(0);

      description = annotation.getDescription();
    }

    return new AnalysisResults(description);
  }
}