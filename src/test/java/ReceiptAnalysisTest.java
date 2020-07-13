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

import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

import com.google.cloud.vision.v1.AnnotateImageRequest;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.BatchAnnotateImagesResponse;
import com.google.cloud.vision.v1.EntityAnnotation;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.protobuf.ByteString;
import com.google.sps.data.AnalysisResults;
import com.google.sps.servlets.ReceiptAnalysis;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ImageAnnotatorClient.class, ReceiptAnalysis.class, URL.class})
public final class ReceiptAnalysisTest {
  private static final ByteString IMAGE_BYTES = ByteString.copyFromUtf8("byte string");
  private static final String RAW_TEXT = "raw text";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void serveImageTextUrlReturnsAnalysisResults() throws IOException {
    URL url = mock(URL.class);
    InputStream inputStream = new ByteArrayInputStream(IMAGE_BYTES.toByteArray());
    when(url.openStream()).thenReturn(inputStream);

    ImageAnnotatorClient client = mock(ImageAnnotatorClient.class);
    mockStatic(ImageAnnotatorClient.class);
    when(ImageAnnotatorClient.create()).thenReturn(client);

    EntityAnnotation annotation = EntityAnnotation.newBuilder().setDescription(RAW_TEXT).build();
    AnnotateImageResponse response =
        AnnotateImageResponse.newBuilder().addTextAnnotations(annotation).build();
    BatchAnnotateImagesResponse batchResponse =
        BatchAnnotateImagesResponse.newBuilder().addResponses(response).build();
    when(client.batchAnnotateImages(Mockito.<AnnotateImageRequest>anyList()))
        .thenReturn(batchResponse);

    AnalysisResults results = ReceiptAnalysis.serveImageText(url);

    Assert.assertEquals(results.getRawText(), RAW_TEXT);
  }
}
