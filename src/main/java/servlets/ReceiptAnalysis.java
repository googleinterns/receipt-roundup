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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Class with static methods that return the text of a specified image using the Cloud Vision API,
 * as well as some categories the text falls into using the Cloud Natural Language API.
 */
public class ReceiptAnalysis {
  // Confidence scores are values in the range [0,1] that indicate how accurate the Cloud Vision API
  // is in identifying the detected logo, with higher scores meaning higher certainty. This is the
  // minimum confidence score that a detected logo must have to be considered significant for
  // receipt analysis.
  private static final float LOGO_DETECTION_CONFIDENCE_THRESHOLD = 0.6f;
  // Matches strings in U.S. date format.
  private static final Pattern dateRegex =
      Pattern.compile("\\d?\\d([/-])\\d?\\d\\1\\d{2}(\\d{2})?");
  // Matches strings formatted as prices in dollars.
  private static final Pattern priceRegex = Pattern.compile("\\$?\\d+\\.\\d\\d");
  // Matches strings containing the word "total" with any capitalization.
  private static final Pattern totalRegex = Pattern.compile(".*total.*", Pattern.CASE_INSENSITIVE);

  /** Returns the text and categorization of the image at the requested URL. */
  public static AnalysisResults analyzeImageAt(URL url) throws IOException {
    ByteString imageBytes = readImageBytes(url);

    return analyzeImage(imageBytes);
  }

  /** Returns the text and categorization of the image at the requested blob key. */
  public static AnalysisResults analyzeImageAt(BlobKey blobKey) throws IOException {
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
  private static AnalysisResults analyzeImage(ByteString imageBytes) throws IOException {
    AnalysisResults.Builder analysisBuilder = retrieveText(imageBytes);

    // Generate categories and parse date and price if text was extracted.
    if (analysisBuilder.getRawText().isPresent()) {
      System.out.println(analysisBuilder.getRawText().get());
      ImmutableSet<String> categories = categorizeText(analysisBuilder.getRawText().get());
      analysisBuilder.setCategories(categories);

      checkForParsableDate(analysisBuilder);
      checkForParsablePrices(analysisBuilder);
    }

    return analysisBuilder.build();
  }

  /** Detects and retrieves text and store logo in the provided image. */
  private static AnalysisResults.Builder retrieveText(ByteString imageBytes) throws IOException {
    AnalysisResults.Builder analysisBuilder = new AnalysisResults.Builder();

    Image image = Image.newBuilder().setContent(imageBytes).build();
    ImmutableList<Feature> features =
        ImmutableList.of(Feature.newBuilder().setType(Feature.Type.TEXT_DETECTION).build(),
            Feature.newBuilder().setType(Feature.Type.LOGO_DETECTION).build());
    AnnotateImageRequest request =
        AnnotateImageRequest.newBuilder().addAllFeatures(features).setImage(image).build();
    ImmutableList<AnnotateImageRequest> requests = ImmutableList.of(request);

    try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {
      BatchAnnotateImagesResponse batchResponse = client.batchAnnotateImages(requests);

      if (batchResponse.getResponsesList().isEmpty()) {
        return analysisBuilder;
      }

      AnnotateImageResponse response = Iterables.getOnlyElement(batchResponse.getResponsesList());

      if (response.hasError()) {
        return analysisBuilder;
      }

      // Add extracted raw text to builder.
      if (!response.getTextAnnotationsList().isEmpty()) {
        // First element has the entire raw text from the image.
        EntityAnnotation textAnnotation = response.getTextAnnotationsList().get(0);

        String rawText = textAnnotation.getDescription();
        analysisBuilder.setRawText(rawText);
      }

      // If a logo was detected with a confidence above the threshold, use it to set the store.
      if (!response.getLogoAnnotationsList().isEmpty()
          && response.getLogoAnnotationsList().get(0).getScore()
              > LOGO_DETECTION_CONFIDENCE_THRESHOLD) {
        String store = response.getLogoAnnotationsList().get(0).getDescription();
        analysisBuilder.setStore(store);
      }
    } catch (ApiException e) {
      // Return default builder if image annotation request failed.
      return analysisBuilder;
    }

    return analysisBuilder;
  }

  /** Generates categories for the provided text. */
  private static ImmutableSet<String> categorizeText(String text) throws IOException {
    ImmutableSet<String> categories = ImmutableSet.of();

    try (LanguageServiceClient client = LanguageServiceClient.create()) {
      Document document = Document.newBuilder().setContent(text).setType(Type.PLAIN_TEXT).build();
      ClassifyTextRequest request = ClassifyTextRequest.newBuilder().setDocument(document).build();

      ClassifyTextResponse response = client.classifyText(request);

      categories = response.getCategoriesList()
                       .stream()
                       .flatMap(ReceiptAnalysis::parseCategory)
                       .collect(ImmutableSet.toImmutableSet());
    } catch (ApiException e) {
      // Return empty set if classification request failed.
      return categories;
    }

    return categories;
  }

  /**
   * Parse category strings into more natural categories
   * e.g. "/Food & Drink/Restaurants" becomes "Food", "Drink", and "Restaurants"
   */
  private static Stream<String> parseCategory(ClassificationCategory category) {
    return Stream.of(category.getName().substring(1).split("/| & "));
  }

  /**
   * Checks the raw text in the builder for a date that can be parsed. If one is found, it is added
   * to the builder as a timestamp.
   */
  private static void checkForParsableDate(AnalysisResults.Builder analysisBuilder) {
    Stream<String> dates =
        getTokensFromRawText(analysisBuilder.getRawText().get()).filter(ReceiptAnalysis::isDate);
    // Assume that the first date on the receipt is the transaction date.
    Optional<String> firstDate = dates.findFirst();

    firstDate.ifPresent(date -> ReceiptAnalysis.addDateIfValid(analysisBuilder, date));
  }

  /**
   * Adds a valid date to the builder as a timestamp. If the date has an invalid month or day, then
   * nothing is added.
   */
  private static void addDateIfValid(AnalysisResults.Builder analysisBuilder, String date) {
    String separator = date.contains("-") ? "-" : "/";
    String formatterPattern = "M" + separator + "d" + separator;

    // Determine if the date has 2 or 4 digits for the year
    if (date.lastIndexOf(separator) + 3 == date.length()) {
      formatterPattern += "yy";
    } else {
      formatterPattern += "yyyy";
    }

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(formatterPattern);

    try {
      ZonedDateTime dateAndTime = LocalDate.parse(date, formatter).atStartOfDay(ZoneOffset.UTC);
      dateAndTime = fixYearIfInFuture(dateAndTime);

      long timestamp = dateAndTime.toInstant().toEpochMilli();
      analysisBuilder.setTransactionTimestamp(timestamp);
    } catch (DateTimeParseException e) {
      // Invalid month or day
      return;
    }
  }

  /**
   * DateTimeFormatter assumes that two-digit years are in the 2000s. This method checks if the
   * given date is after the current date and if so, subtracts 100 years to move it to the 1900s.
   */
  private static ZonedDateTime fixYearIfInFuture(ZonedDateTime dateAndTime) {
    if (dateAndTime.isAfter(ZonedDateTime.now())) {
      return dateAndTime.minusYears(100);
    }
    return dateAndTime;
  }

  /**
   * Splits a string on whitespace and returns a stream of the resulting strings.
   */
  private static Stream<String> getTokensFromRawText(String rawText) {
    return Stream.of(rawText.split("\\s"));
  }

  /**
   * Checks if the token is formatted as a date.
   */
  private static boolean isDate(String token) {
    return dateRegex.matcher(token).matches();
  }

  /**
   * Searches the raw text in the builder for the total price using multiple heuristics. If any find
   * a price, it is added to the builder.
   */
  private static void checkForParsablePrices(AnalysisResults.Builder analysisBuilder) {
    if (!findPriceAfterTotal(analysisBuilder)) {
      findLargestPrice(analysisBuilder);
    }
  }

  /**
   * Checks the raw text in the builder for a price appearing after the word "total" and adds it to
   * the builder if found.
   *
   * @return whether or not a price was added.
   */
  private static boolean findPriceAfterTotal(AnalysisResults.Builder analysisBuilder) {
    ImmutableList<String> relevantTokens =
        getTokensFromRawText(analysisBuilder.getRawText().get())
            .filter(token -> isPrice(token) || containsTotal(token))
            .collect(ImmutableList.toImmutableList());

    double price = 0;
    boolean priceFound = false;
    boolean priceFoundAfterMostRecentTotal = true;

    // Try to find the first price after each token containing "total", and keep the last one found.
    for (String token : relevantTokens) {
      if (containsTotal(token)) {
        priceFoundAfterMostRecentTotal = false;
      } else if (!priceFoundAfterMostRecentTotal && parsePrice(token) != Double.NEGATIVE_INFINITY) {
        price = parsePrice(token);
        priceFound = true;
        priceFoundAfterMostRecentTotal = true;
      }
    }

    if (priceFound) {
      analysisBuilder.setPrice(price);
      return true;
    }

    return false;
  }

  /**
   * Checks the raw text in the builder for prices that can be parsed. The largest price found, if
   * it exists, is added to the builder. The return value indicates whether a price was added.
   */
  private static boolean findLargestPrice(AnalysisResults.Builder analysisBuilder) {
    double largestPrice = getTokensFromRawText(analysisBuilder.getRawText().get())
                              .filter(ReceiptAnalysis::isPrice)
                              .mapToDouble(ReceiptAnalysis::parsePrice)
                              .reduce(Double.NEGATIVE_INFINITY, Double::max);

    if (largestPrice != Double.NEGATIVE_INFINITY) {
      analysisBuilder.setPrice(largestPrice);
      return true;
    }

    return false;
  }

  /**
   * Returns the price represented by the string as a double, or Double.NEGATIVE_INFINITY if the
   * string cannot be parsed.
   */
  private static double parsePrice(String price) {
    if (price.startsWith("$")) {
      price = price.substring(1);
    }

    try {
      return Double.parseDouble(price);
    } catch (NumberFormatException e) {
      return Double.NEGATIVE_INFINITY;
    }
  }

  /**
   * Checks if the token is formatted as a price.
   */
  private static boolean isPrice(String token) {
    return priceRegex.matcher(token).matches();
  }

  /**
   * Checks if the token contains the word "total".
   */
  private static boolean containsTotal(String token) {
    return totalRegex.matcher(token).matches();
  }

  public static class ReceiptAnalysisException extends Exception {
    public ReceiptAnalysisException(String errorMessage, Throwable err) {
      super(errorMessage, err);
    }
  }
}
