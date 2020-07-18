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

import com.google.common.collect.ImmutableSet;
import com.google.sps.data.QueryInformation;
import java.text.ParseException;
import java.util.TimeZone;
import org.junit.Assert;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class QueryInformationTest {
  private static final double ERROR_THRESHOLD = 0.1;

  // Values for a valid test.
  private static final String CST_TIMEZONE_ID = "America/Chicago";
  private static final String CATEGORIES = "Breakfast";
  private static final String DATE_RANGE = "June 1, 2020 - June 30, 2020";
  private static final String STORE = "McDonald's";
  private static final String MIN_PRICE = "21.30";
  private static final String MAX_PRICE = "87.60";

  // Expected values.
  private static final TimeZone CST_TIMEZONE = TimeZone.getTimeZone("America/Chicago");
  private static final ImmutableSet EXPECTED_CATEGORIES = ImmutableSet.of("breakfast");
  private static final long JUNE_1_2020_START_OF_DAY = 1590987600000L;
  private static final long JUNE_30_2020_END_OF_DAY = 1593579599999L;
  private static final String EXPECTED_STORE = "mcdonald's";
  private static final double EXPECTED_MIN_PRICE = 21.30;
  private static final double EXPECTED_MAX_PRICE = 87.60;

  // Values for an invalid test.
  private static final TimeZone GMT_TIMEZONE = TimeZone.getTimeZone("GMT");

  // Edge case values.
  private static final String EDGE_CASE_CATEGORIES = "   breAkfaSt ";
  private static final String EDGE_CASE_STORE = "  MCdoNald'S";

  @Test
  public void validTimeZoneIdSetsToCst() throws ParseException {
    QueryInformation queryInformation =
        new QueryInformation(CST_TIMEZONE_ID, CATEGORIES, DATE_RANGE, STORE, MIN_PRICE, MAX_PRICE);
    Assert.assertEquals(CST_TIMEZONE, queryInformation.getTimeZone());
  }

  @Test
  public void invalidTimeZoneIdSetsToGmt() throws ParseException {
    // Test with empty string.
    QueryInformation queryInformation = new QueryInformation(
        /*timeZoneId=*/"", CATEGORIES, DATE_RANGE, STORE, MIN_PRICE, MAX_PRICE);
    Assert.assertEquals(GMT_TIMEZONE, queryInformation.getTimeZone());
  }

  @Test
  public void validCategories() throws ParseException {
    QueryInformation queryInformation =
        new QueryInformation(CST_TIMEZONE_ID, CATEGORIES, DATE_RANGE, STORE, MIN_PRICE, MAX_PRICE);
    Assert.assertEquals(EXPECTED_CATEGORIES, queryInformation.getCategories());
  }

  @Test
  public void categoriesLowercasedAndWhitespaceRemoved() throws ParseException {
    QueryInformation queryInformation = new QueryInformation(
        CST_TIMEZONE_ID, EDGE_CASE_CATEGORIES, DATE_RANGE, STORE, MIN_PRICE, MAX_PRICE);
    Assert.assertEquals(EXPECTED_CATEGORIES, queryInformation.getCategories());
  }

  @Test
  public void invalidCategoriesEmptyString() throws ParseException {
    QueryInformation queryInformation = new QueryInformation(
        CST_TIMEZONE_ID, /*categories=*/"", DATE_RANGE, STORE, MIN_PRICE, MAX_PRICE);
    Assert.assertEquals(ImmutableSet.of(), queryInformation.getCategories());
  }

  @Test
  public void valideDateRangeParse() throws ParseException {
    QueryInformation queryInformation =
        new QueryInformation(CST_TIMEZONE_ID, CATEGORIES, DATE_RANGE, STORE, MIN_PRICE, MAX_PRICE);
    Assert.assertEquals(JUNE_1_2020_START_OF_DAY, queryInformation.getStartTimestamp());
    Assert.assertEquals(JUNE_30_2020_END_OF_DAY, queryInformation.getEndTimestamp());
  }

  @Test
  public void invalidDateRangeEmptyString() throws ParseException {
    Assertions.assertThrows(ParseException.class, () -> {
      QueryInformation queryInformation = new QueryInformation(
          CST_TIMEZONE_ID, CATEGORIES, /*dateRange=*/"", STORE, MIN_PRICE, MAX_PRICE);
    });
  }

  @Test
  public void storeCorrectlySet() throws ParseException {
    QueryInformation queryInformation =
        new QueryInformation(CST_TIMEZONE_ID, CATEGORIES, DATE_RANGE, STORE, MIN_PRICE, MAX_PRICE);
    Assert.assertEquals(EXPECTED_STORE, queryInformation.getStore());
  }

  @Test
  public void storeLowercasedAndWhitespaceRemoved() throws ParseException {
    QueryInformation queryInformation = new QueryInformation(
        CST_TIMEZONE_ID, CATEGORIES, DATE_RANGE, EDGE_CASE_STORE, MIN_PRICE, MAX_PRICE);
    Assert.assertEquals(EXPECTED_STORE, queryInformation.getStore());
  }

  @Test
  public void pricesCorrectlySet() throws ParseException {
    QueryInformation queryInformation =
        new QueryInformation(CST_TIMEZONE_ID, CATEGORIES, DATE_RANGE, STORE, MIN_PRICE, MAX_PRICE);
    Assert.assertEquals(EXPECTED_MIN_PRICE, queryInformation.getMinPrice(), ERROR_THRESHOLD);
    Assert.assertEquals(EXPECTED_MAX_PRICE, queryInformation.getMaxPrice(), ERROR_THRESHOLD);
  }

  @Test
  public void emptyStringPriceThrows() throws ParseException {
    Assertions.assertThrows(NumberFormatException.class, () -> {
      QueryInformation queryInformation = new QueryInformation(
          CST_TIMEZONE_ID, CATEGORIES, DATE_RANGE, STORE, /*minPrice=*/"", MAX_PRICE);
    });
  }

  @Test
  public void nullPriceThrows() throws ParseException {
    Assertions.assertThrows(NullPointerException.class, () -> {
      QueryInformation queryInformation = new QueryInformation(
          CST_TIMEZONE_ID, CATEGORIES, DATE_RANGE, STORE, MIN_PRICE, /*maxPrice=*/null);
    });
  }
}
