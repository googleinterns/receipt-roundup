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

import com.google.common.collect.ImmutableSet;
import java.time.Clock;
import java.util.Arrays;
import java.util.stream.Stream;
import javax.servlet.http.HttpServletRequest;

/**
 * Contains methods used by the upload and edit servlets for formatting receipt fields.
 */
public final class FormatUtils {
  /**
   * Prevents instantiation.
   */
  private FormatUtils() {
    throw new UnsupportedOperationException();
  }

  /**
   * Sanitizes and formats the set of categories from the request.
   */
  public static ImmutableSet<String> getCategories(HttpServletRequest request) {
    return sanitizeCategories(Arrays.stream(request.getParameterValues("categories")));
  }

  /**
   * Sanitizes and formats the given stream of categories.
   */
  public static ImmutableSet<String> sanitizeCategories(Stream<String> categories) {
    return categories.map(FormatUtils::sanitize).collect(ImmutableSet.toImmutableSet());
  }

  /**
   * Converts the date parameter from the request to a timestamp and verifies that the date is in
   * the past.
   */
  public static long getTimestamp(HttpServletRequest request, Clock clock)
      throws InvalidDateException {
    long currentTimestamp = clock.instant().toEpochMilli();
    long transactionTimestamp;

    try {
      transactionTimestamp = Long.parseLong(request.getParameter("date"));
    } catch (NumberFormatException e) {
      throw new InvalidDateException("Transaction date must be a long.");
    }

    if (transactionTimestamp > currentTimestamp) {
      throw new InvalidDateException("Transaction date must be in the past.");
    }

    return transactionTimestamp;
  }

  /**
   * Converts the input to all lowercase with exactly 1 whitespace separating words and no leading
   * or trailing whitespace.
   */
  public static String sanitize(String input) {
    return input.trim().replaceAll("\\s+", " ").toLowerCase();
  }

  /**
   * Converts a price string into a double rounded to 2 decimal places.
   */
  public static double roundPrice(String price) throws InvalidPriceException {
    double parsedPrice;
    try {
      parsedPrice = Double.parseDouble(price);
    } catch (NumberFormatException e) {
      throw new InvalidPriceException("Price could not be parsed.");
    }

    if (parsedPrice < 0) {
      throw new InvalidPriceException("Price must be positive.");
    }

    return Math.round(parsedPrice * 100.0) / 100.0;
  }

  public static class InvalidDateException extends Exception {
    public InvalidDateException(String errorMessage) {
      super(errorMessage);
    }
  }

  public static class InvalidPriceException extends Exception {
    public InvalidPriceException(String errorMessage) {
      super(errorMessage);
    }
  }
}
