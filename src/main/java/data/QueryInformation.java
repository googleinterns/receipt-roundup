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

package com.google.sps.data;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.TimeZone;

/** Class to hold query information. */
public class QueryInformation {
  /** Milliseconds equivalent to 11:59:59.999 PM. */
  private static final long MILLISECONDS_TO_END_OF_DAY = (24L * 60L * 60L * 1000L) - 1L;

  private final TimeZone timeZone;
  private final ImmutableSet<String> category;
  private final long startTimestamp;
  private final long endTimestamp;
  private final String store;
  private final double minPrice;
  private final double maxPrice;

  public QueryInformation(String timeZoneId, String category, String dateRange, String store,
      String minPrice, String maxPrice) throws ParseException, NumberFormatException {
    this.timeZone = TimeZone.getTimeZone(timeZoneId);

    String formattedCategory = formatInput(category);

    if (Strings.isNullOrEmpty(formattedCategory)) {
      this.category = ImmutableSet.of();
    } else {
      this.category = ImmutableSet.of(formattedCategory);
    }

    String[] dates = dateRange.split("-");
    this.startTimestamp = dateToMilliseconds(formatInput(dates[0]));
    this.endTimestamp = dateToMilliseconds(formatInput(dates[1])) + MILLISECONDS_TO_END_OF_DAY;

    this.store = formatInput(store);
    this.minPrice = Double.parseDouble(formatInput(minPrice));
    this.maxPrice = Double.parseDouble(formatInput(maxPrice));
  }

  /** Sets input to lowercase and replaces all extra whitespace before/after/between. */
  private String formatInput(String rawInput) {
    return rawInput.toLowerCase().replaceAll("\\s+", " ").trim();
  }

  /** Converts a formatted date (month day, year) to milliseconds since epoch. */
  private long dateToMilliseconds(String date) throws ParseException {
    DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.LONG);
    dateFormat.setTimeZone(timeZone);

    return dateFormat.parse(date).getTime();
  }

  public TimeZone getTimeZone() {
    return timeZone;
  }

  public ImmutableSet<String> getCategory() {
    return category;
  }

  public long getStartTimestamp() {
    return startTimestamp;
  }

  public long getEndTimestamp() {
    return endTimestamp;
  }

  public String getStore() {
    return store;
  }

  public double getMinPrice() {
    return minPrice;
  }

  public double getMaxPrice() {
    return maxPrice;
  }
}
