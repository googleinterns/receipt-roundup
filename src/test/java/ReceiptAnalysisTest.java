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

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

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
import com.google.protobuf.ByteString;
import com.google.rpc.Status;
import com.google.sps.data.AnalysisResults;
import com.google.sps.servlets.ReceiptAnalysis;
import com.google.sps.servlets.ReceiptAnalysis.ReceiptAnalysisException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Set;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(
    {ImageAnnotatorClient.class, LanguageServiceClient.class, ReceiptAnalysis.class, URL.class})
public final class ReceiptAnalysisTest {
  private static final String EMPTY_BATCH_RESPONSE_WARNING =
      "Received empty batch image annotation response.";
  private static final String RESPONSE_ERROR_WARNING =
      "Received image annotation response with error.";
  private static final String EMPTY_TEXT_ANNOTATIONS_LIST_WARNING =
      "Received image annotation response without text annotations.";

  private static final ByteString IMAGE_BYTES = ByteString.copyFromUtf8("byte string");
  private static final String RAW_TEXT = "raw text";
  private static final String CATEGORY_NAME = "category";
  private static final Set<String> CATEGORIES = Collections.singleton(CATEGORY_NAME);

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Rule public ExpectedException expectedException = ExpectedException.none();

  @Test
  public void serveImageTextUrlReturnsAnalysisResults()
      throws IOException, ReceiptAnalysisException {
    URL url = mock(URL.class);
    InputStream inputStream = new ByteArrayInputStream(IMAGE_BYTES.toByteArray());
    when(url.openStream()).thenReturn(inputStream);

    ImageAnnotatorClient imageClient = mock(ImageAnnotatorClient.class);
    mockStatic(ImageAnnotatorClient.class);
    when(ImageAnnotatorClient.create()).thenReturn(imageClient);

    EntityAnnotation annotation = EntityAnnotation.newBuilder().setDescription(RAW_TEXT).build();
    AnnotateImageResponse imageResponse =
        AnnotateImageResponse.newBuilder().addTextAnnotations(annotation).build();
    BatchAnnotateImagesResponse batchResponse =
        BatchAnnotateImagesResponse.newBuilder().addResponses(imageResponse).build();
    when(imageClient.batchAnnotateImages(anyList())).thenReturn(batchResponse);

    LanguageServiceClient languageClient = mock(LanguageServiceClient.class);
    mockStatic(LanguageServiceClient.class);
    when(LanguageServiceClient.create()).thenReturn(languageClient);

    ClassificationCategory category =
        ClassificationCategory.newBuilder().setName(CATEGORY_NAME).build();
    ClassifyTextResponse classifyResponse =
        ClassifyTextResponse.newBuilder().addCategories(category).build();
    when(languageClient.classifyText(any(ClassifyTextRequest.class))).thenReturn(classifyResponse);

    Image image = Image.newBuilder().setContent(IMAGE_BYTES).build();
    Feature feature = Feature.newBuilder().setType(Feature.Type.TEXT_DETECTION).build();
    AnnotateImageRequest imageRequest =
        AnnotateImageRequest.newBuilder().addFeatures(feature).setImage(image).build();
    ImmutableList<AnnotateImageRequest> imageRequests = ImmutableList.of(imageRequest);

    Document document = Document.newBuilder().setContent(RAW_TEXT).setType(Type.PLAIN_TEXT).build();
    ClassifyTextRequest classifyRequest =
        ClassifyTextRequest.newBuilder().setDocument(document).build();

    AnalysisResults results = ReceiptAnalysis.serveImageText(url);

    Assert.assertEquals(results.getRawText(), RAW_TEXT);
    Assert.assertEquals(results.getCategories(), CATEGORIES);
    verify(imageClient).batchAnnotateImages(imageRequests);
    verify(languageClient).classifyText(classifyRequest);
  }

  @Test
  public void serveImageTextThrowsIfEmptyBatchResponse()
      throws IOException, ReceiptAnalysisException {
    URL url = mock(URL.class);
    InputStream inputStream = new ByteArrayInputStream(IMAGE_BYTES.toByteArray());
    when(url.openStream()).thenReturn(inputStream);

    ImageAnnotatorClient client = mock(ImageAnnotatorClient.class);
    mockStatic(ImageAnnotatorClient.class);
    when(ImageAnnotatorClient.create()).thenReturn(client);

    BatchAnnotateImagesResponse batchResponse = BatchAnnotateImagesResponse.newBuilder().build();
    when(client.batchAnnotateImages(Mockito.<AnnotateImageRequest>anyList()))
        .thenReturn(batchResponse);

    expectedException.expect(ReceiptAnalysisException.class);
    expectedException.expectMessage(EMPTY_BATCH_RESPONSE_WARNING);

    ReceiptAnalysis.serveImageText(url);
  }

  @Test
  public void serveImageTextThrowsIfResponseHasError()
      throws IOException, ReceiptAnalysisException {
    URL url = mock(URL.class);
    InputStream inputStream = new ByteArrayInputStream(IMAGE_BYTES.toByteArray());
    when(url.openStream()).thenReturn(inputStream);

    ImageAnnotatorClient client = mock(ImageAnnotatorClient.class);
    mockStatic(ImageAnnotatorClient.class);
    when(ImageAnnotatorClient.create()).thenReturn(client);

    AnnotateImageResponse response =
        AnnotateImageResponse.newBuilder().setError(Status.getDefaultInstance()).build();
    BatchAnnotateImagesResponse batchResponse =
        BatchAnnotateImagesResponse.newBuilder().addResponses(response).build();
    when(client.batchAnnotateImages(Mockito.<AnnotateImageRequest>anyList()))
        .thenReturn(batchResponse);

    expectedException.expect(ReceiptAnalysisException.class);
    expectedException.expectMessage(RESPONSE_ERROR_WARNING);

    ReceiptAnalysis.serveImageText(url);
  }

  @Test
  public void serveImageTextThrowsIfEmptyTextAnnotationsList()
      throws IOException, ReceiptAnalysisException {
    URL url = mock(URL.class);
    InputStream inputStream = new ByteArrayInputStream(IMAGE_BYTES.toByteArray());
    when(url.openStream()).thenReturn(inputStream);

    ImageAnnotatorClient client = mock(ImageAnnotatorClient.class);
    mockStatic(ImageAnnotatorClient.class);
    when(ImageAnnotatorClient.create()).thenReturn(client);

    AnnotateImageResponse response = AnnotateImageResponse.newBuilder().build();
    BatchAnnotateImagesResponse batchResponse =
        BatchAnnotateImagesResponse.newBuilder().addResponses(response).build();
    when(client.batchAnnotateImages(Mockito.<AnnotateImageRequest>anyList()))
        .thenReturn(batchResponse);

    expectedException.expect(ReceiptAnalysisException.class);
    expectedException.expectMessage(EMPTY_TEXT_ANNOTATIONS_LIST_WARNING);

    ReceiptAnalysis.serveImageText(url);
  }
}
