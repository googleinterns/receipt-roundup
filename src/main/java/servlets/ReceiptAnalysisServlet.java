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
import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import com.google.sps.data.AnalysisResults;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet with a GET handler that serves the text of the provided image using the Cloud Vision API.
 */
@WebServlet("/receipt-analysis")
public class ReceiptAnalysisServlet extends HttpServlet {
  /** Serves the text of the image at the requested file path. */
  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    String filePath = request.getParameter("file-path");
    ByteString imgBytes;

    // Ignore requests that don't specify a file path
    if (filePath == null) {
      return;
    }

    imgBytes = readImageBytes(filePath);

    AnalysisResults results = new AnalysisResults(retrieveText(imgBytes));

    Gson gson = new Gson();
    response.setContentType("text/html;");
    response.getWriter().println(gson.toJson(results));
  }

  /** Reads the image bytes from the specified path. */
  private static ByteString readImageBytes(String filePath) throws IOException {
    ByteString imgBytes;

    try (InputStream fileInputStream = new FileInputStream(filePath)) {
      imgBytes = ByteString.readFrom(fileInputStream);
    }

    return imgBytes;
  }

  /** Detects and retrieves text in the provided image. */
  private static String retrieveText(ByteString imgBytes) throws IOException {
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

    return description;
  }
}