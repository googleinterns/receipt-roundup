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

import com.google.api.gax.grpc.GrpcStatusCode;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;
import com.google.api.gax.rpc.StatusCode.Code;
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
import com.google.protobuf.ByteString;
import com.google.rpc.Status;
import com.google.sps.data.AnalysisResults;
import com.google.sps.servlets.ReceiptAnalysis;
import com.google.sps.servlets.ReceiptAnalysis.ReceiptAnalysisException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Instant;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@PowerMockIgnore("jdk.internal.reflect.*")
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
  private static final String IMAGE_REQUEST_FAILED_WARNING = "Image annotation request failed.";
  private static final String TEXT_REQUEST_FAILED_WARNING = "Classify text request failed.";

  private static final ByteString IMAGE_BYTES = ByteString.copyFromUtf8("byte string");
  private static final String RAW_TEXT = "raw text";

  private static final String GENERAL_CATEGORY_NAME = "General";
  private static final String SPECIFIC_CATEGORY_NAME = "Specific";
  private static final String CATEGORY_NAME =
      "/" + GENERAL_CATEGORY_NAME + "/" + SPECIFIC_CATEGORY_NAME;

  private static final ImmutableSet<String> CATEGORIES =
      ImmutableSet.of(GENERAL_CATEGORY_NAME, SPECIFIC_CATEGORY_NAME);

  private static final Optional<String> STORE = Optional.of("Google");
  private static final float LOGO_CONFIDENCE = 0.6f;
  private static final float LOGO_CONFIDENCE_BELOW_THRESHOLD = 0.2f;

  private static final String RAW_TEXT_WITH_DATE = "the date is 05-08-2020";
  private static final String RAW_TEXT_WITH_DATE_USING_SLASHES = "the date is 05/08/2020";
  private static final String RAW_TEXT_WITH_DATE_NO_LEADING_ZEROS = "the date is 5-8-2020";
  private static final String RAW_TEXT_WITH_DATE_TWO_DIGIT_YEAR = "the date is 05-08-20";
  private static final String RAW_TEXT_WITH_DATE_IN_1900S = "the date is 05-08-99";

  private static final Instant INSTANT = Instant.parse("2020-05-08T00:00:00Z");
  private static final Optional<Long> TIMESTAMP = Optional.of(Long.valueOf(INSTANT.toEpochMilli()));
  private static final Instant INSTANT_IN_1900S = Instant.parse("1999-05-08T00:00:00Z");
  private static final Optional<Long> TIMESTAMP_IN_1900S =
      Optional.of(Long.valueOf(INSTANT_IN_1900S.toEpochMilli()));

  private URL url;
  private ImageAnnotatorClient imageClient;
  private LanguageServiceClient languageClient;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);

    url = mock(URL.class);
    InputStream inputStream = new ByteArrayInputStream(IMAGE_BYTES.toByteArray());
    when(url.openStream()).thenReturn(inputStream);

    imageClient = mock(ImageAnnotatorClient.class);
    mockStatic(ImageAnnotatorClient.class);
    when(ImageAnnotatorClient.create()).thenReturn(imageClient);

    languageClient = mock(LanguageServiceClient.class);
    mockStatic(LanguageServiceClient.class);
    when(LanguageServiceClient.create()).thenReturn(languageClient);
  }

  @Test
  public void analyzeImageAtUrlReturnsAnalysisResults()
      throws IOException, ReceiptAnalysisException {
    stubAnnotationResponse(LOGO_CONFIDENCE, RAW_TEXT);
    stubTextClassification();
    ImmutableList<AnnotateImageRequest> imageRequests = createImageRequest();
    ClassifyTextRequest classifyRequest = createClassifyRequest();

    AnalysisResults results = ReceiptAnalysis.analyzeImageAt(url);

    Assert.assertEquals(RAW_TEXT, results.getRawText());
    Assert.assertEquals(CATEGORIES, results.getCategories());
    Assert.assertEquals(STORE, results.getStore());
    verify(imageClient).batchAnnotateImages(imageRequests);
    verify(languageClient).classifyText(classifyRequest);
  }

  @Test
  public void analyzeImageAtUrlReturnsAnalysisResultsWithNoStore()
      throws IOException, ReceiptAnalysisException {
    AnnotateImageResponse imageResponse = createImageResponseWithText(RAW_TEXT).build();
    BatchAnnotateImagesResponse batchResponse =
        BatchAnnotateImagesResponse.newBuilder().addResponses(imageResponse).build();
    when(imageClient.batchAnnotateImages(anyList())).thenReturn(batchResponse);

    stubTextClassification();

    AnalysisResults results = ReceiptAnalysis.analyzeImageAt(url);

    Assert.assertEquals(Optional.empty(), results.getStore());
  }

  @Test
  public void analyzeImageAtUrlIgnoresLogoIfLowConfidence()
      throws IOException, ReceiptAnalysisException {
    stubAnnotationResponse(LOGO_CONFIDENCE_BELOW_THRESHOLD, RAW_TEXT);
    stubTextClassification();
    ImmutableList<AnnotateImageRequest> imageRequests = createImageRequest();
    ClassifyTextRequest classifyRequest = createClassifyRequest();

    AnalysisResults results = ReceiptAnalysis.analyzeImageAt(url);

    Assert.assertEquals(Optional.empty(), results.getStore());
  }

  @Test
  public void analyzeImageAtUrlReturnsDate() throws IOException, ReceiptAnalysisException {
    stubAnnotationResponse(LOGO_CONFIDENCE, RAW_TEXT_WITH_DATE);
    stubTextClassification();

    AnalysisResults results = ReceiptAnalysis.analyzeImageAt(url);

    Assert.assertEquals(TIMESTAMP, results.getTimestamp());
  }

  @Test
  public void analyzeImageAtUrlReturnsDateUsingSlashes()
      throws IOException, ReceiptAnalysisException {
    stubAnnotationResponse(LOGO_CONFIDENCE, RAW_TEXT_WITH_DATE_USING_SLASHES);
    stubTextClassification();

    AnalysisResults results = ReceiptAnalysis.analyzeImageAt(url);

    Assert.assertEquals(TIMESTAMP, results.getTimestamp());
  }

  @Test
  public void analyzeImageAtUrlReturnsDateWithNoLeadingZeros()
      throws IOException, ReceiptAnalysisException {
    stubAnnotationResponse(LOGO_CONFIDENCE, RAW_TEXT_WITH_DATE_NO_LEADING_ZEROS);
    stubTextClassification();

    AnalysisResults results = ReceiptAnalysis.analyzeImageAt(url);

    Assert.assertEquals(TIMESTAMP, results.getTimestamp());
  }

  @Test
  public void analyzeImageAtUrlReturnsDateWithTwoDigitYear()
      throws IOException, ReceiptAnalysisException {
    stubAnnotationResponse(LOGO_CONFIDENCE, RAW_TEXT_WITH_DATE_TWO_DIGIT_YEAR);
    stubTextClassification();

    AnalysisResults results = ReceiptAnalysis.analyzeImageAt(url);

    Assert.assertEquals(TIMESTAMP, results.getTimestamp());
  }

  @Test
  public void analyzeImageAtUrlReturnsDateIn1900s() throws IOException, ReceiptAnalysisException {
    stubAnnotationResponse(LOGO_CONFIDENCE, RAW_TEXT_WITH_DATE_IN_1900S);
    stubTextClassification();

    AnalysisResults results = ReceiptAnalysis.analyzeImageAt(url);

    Assert.assertEquals(TIMESTAMP_IN_1900S, results.getTimestamp());
  }

  @Test
  public void analyzeImageAtThrowsIfEmptyBatchResponse()
      throws IOException, ReceiptAnalysisException {
    BatchAnnotateImagesResponse batchResponse = BatchAnnotateImagesResponse.newBuilder().build();
    when(imageClient.batchAnnotateImages(anyList())).thenReturn(batchResponse);

    ReceiptAnalysisException exception = Assertions.assertThrows(
        ReceiptAnalysisException.class, () -> { ReceiptAnalysis.analyzeImageAt(url); });

    Assert.assertEquals(EMPTY_BATCH_RESPONSE_WARNING, exception.getMessage());
  }

  @Test
  public void analyzeImageAtThrowsIfResponseHasError()
      throws IOException, ReceiptAnalysisException {
    AnnotateImageResponse response =
        AnnotateImageResponse.newBuilder().setError(Status.getDefaultInstance()).build();
    BatchAnnotateImagesResponse batchResponse =
        BatchAnnotateImagesResponse.newBuilder().addResponses(response).build();
    when(imageClient.batchAnnotateImages(anyList())).thenReturn(batchResponse);

    ReceiptAnalysisException exception = Assertions.assertThrows(
        ReceiptAnalysisException.class, () -> { ReceiptAnalysis.analyzeImageAt(url); });

    Assert.assertEquals(RESPONSE_ERROR_WARNING, exception.getMessage());
  }

  @Test
  public void analyzeImageAtThrowsIfEmptyTextAnnotationsList()
      throws IOException, ReceiptAnalysisException {
    AnnotateImageResponse response = AnnotateImageResponse.newBuilder().build();
    BatchAnnotateImagesResponse batchResponse =
        BatchAnnotateImagesResponse.newBuilder().addResponses(response).build();
    when(imageClient.batchAnnotateImages(anyList())).thenReturn(batchResponse);

    ReceiptAnalysisException exception = Assertions.assertThrows(
        ReceiptAnalysisException.class, () -> { ReceiptAnalysis.analyzeImageAt(url); });

    Assert.assertEquals(EMPTY_TEXT_ANNOTATIONS_LIST_WARNING, exception.getMessage());
  }

  @Test
  public void analyzeImageAtThrowsIfImageRequestFails()
      throws IOException, ReceiptAnalysisException {
    StatusCode statusCode = GrpcStatusCode.of(io.grpc.Status.INTERNAL.getCode());
    ApiException clientException = new ApiException(null, statusCode, false);
    when(imageClient.batchAnnotateImages(anyList())).thenThrow(clientException);

    ReceiptAnalysisException exception = Assertions.assertThrows(
        ReceiptAnalysisException.class, () -> { ReceiptAnalysis.analyzeImageAt(url); });

    Assert.assertEquals(IMAGE_REQUEST_FAILED_WARNING, exception.getMessage());
    Assert.assertEquals(clientException, exception.getCause());
  }

  @Test
  public void analyzeImageAtThrowsIfTextRequestFails()
      throws IOException, ReceiptAnalysisException {
    EntityAnnotation annotation = EntityAnnotation.newBuilder().setDescription(RAW_TEXT).build();
    AnnotateImageResponse imageResponse =
        AnnotateImageResponse.newBuilder().addTextAnnotations(annotation).build();
    BatchAnnotateImagesResponse batchResponse =
        BatchAnnotateImagesResponse.newBuilder().addResponses(imageResponse).build();
    when(imageClient.batchAnnotateImages(anyList())).thenReturn(batchResponse);

    StatusCode statusCode = GrpcStatusCode.of(io.grpc.Status.INTERNAL.getCode());
    ApiException clientException = new ApiException(null, statusCode, false);
    when(languageClient.classifyText(any(ClassifyTextRequest.class))).thenThrow(clientException);

    ReceiptAnalysisException exception = Assertions.assertThrows(
        ReceiptAnalysisException.class, () -> { ReceiptAnalysis.analyzeImageAt(url); });

    Assert.assertEquals(TEXT_REQUEST_FAILED_WARNING, exception.getMessage());
    Assert.assertEquals(clientException, exception.getCause());
  }

  private void stubAnnotationResponse(float confidenceScore, String rawText) {
    EntityAnnotation logoAnnotation =
        EntityAnnotation.newBuilder().setDescription(STORE.get()).setScore(confidenceScore).build();
    AnnotateImageResponse imageResponse =
        createImageResponseWithText(rawText).addLogoAnnotations(logoAnnotation).build();
    BatchAnnotateImagesResponse batchResponse =
        BatchAnnotateImagesResponse.newBuilder().addResponses(imageResponse).build();
    when(imageClient.batchAnnotateImages(anyList())).thenReturn(batchResponse);
  }

  private AnnotateImageResponse.Builder createImageResponseWithText(String rawText) {
    EntityAnnotation annotation = EntityAnnotation.newBuilder().setDescription(rawText).build();
    return AnnotateImageResponse.newBuilder().addTextAnnotations(annotation);
  }

  private void stubTextClassification() {
    ClassificationCategory category =
        ClassificationCategory.newBuilder().setName(CATEGORY_NAME).build();
    ClassifyTextResponse classifyResponse =
        ClassifyTextResponse.newBuilder().addCategories(category).build();
    when(languageClient.classifyText(any(ClassifyTextRequest.class))).thenReturn(classifyResponse);
  }

  private ImmutableList<AnnotateImageRequest> createImageRequest() {
    Image image = Image.newBuilder().setContent(IMAGE_BYTES).build();
    ImmutableList<Feature> features =
        ImmutableList.of(Feature.newBuilder().setType(Feature.Type.TEXT_DETECTION).build(),
            Feature.newBuilder().setType(Feature.Type.LOGO_DETECTION).build());
    AnnotateImageRequest imageRequest =
        AnnotateImageRequest.newBuilder().addAllFeatures(features).setImage(image).build();
    return ImmutableList.of(imageRequest);
  }

  private ClassifyTextRequest createClassifyRequest() {
    Document document = Document.newBuilder().setContent(RAW_TEXT).setType(Type.PLAIN_TEXT).build();
    return ClassifyTextRequest.newBuilder().setDocument(document).build();
  }
}
