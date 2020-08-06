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

import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.appengine.api.datastore.QueryResultList;
import com.google.appengine.api.datastore.Text;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.sps.data.QueryInformation;
import com.google.sps.data.Receipt;
import com.google.sps.data.SearchServletResponse;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.stream.Stream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Servlet that searches and returns matching receipts from datastore. */
@WebServlet("/search-receipts")
public class SearchServlet extends HttpServlet {
  private static final int RECEIPTS_PER_PAGE = 10;

  /** Messages that show up on client-side banner on thrown exception. */
  private static final String NULL_EXCEPTION_MESSAGE =
      "Null Field: Receipt unable to be queried at this time, please try again.";
  private static final String NUMBER_EXCEPTION_MESSAGE =
      "Invalid Price: Receipt unable to be queried at this time, please try again.";
  private static final String PARSE_EXCEPTION_MESSAGE =
      "Dates Unparseable: Receipt unable to be queried at this time, please try again.";
  private static final String AUTHENTICATION_ERROR_MESSAGE =
      "No Authentication: User must be logged in to search receipts.";

  private final DatastoreService datastore;
  private final UserService userService = UserServiceFactory.getUserService();

  private PreparedQuery preparedQuery;
  private QueryInformation queryInformation;

  public SearchServlet() {
    datastore = DatastoreServiceFactory.getDatastoreService();
  }

  public SearchServlet(DatastoreService datastore) {
    this.datastore = datastore;
  }

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    if (!userService.isUserLoggedIn()) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      response.getWriter().println(AUTHENTICATION_ERROR_MESSAGE);
      return;
    }

    SearchServletResponse servletResponse = null;

    if (checkParameter(request, "isPageLoad")) {
      servletResponse = getMatchingReceipts(/* isPageLoad = */ true);
    } else if (checkParameter(request, "isNewSearch")) {
      try {
        createQueryInformation(request);
      } catch (NullPointerException exception) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.getWriter().println(NULL_EXCEPTION_MESSAGE);
        return;
      } catch (NumberFormatException exception) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.getWriter().println(NUMBER_EXCEPTION_MESSAGE);
        return;
      } catch (ParseException exception) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.getWriter().println(PARSE_EXCEPTION_MESSAGE);
        return;
      }

      servletResponse = getMatchingReceipts(/* isPageLoad = */ false);
    } else if (checkParameter(request, "getNextPage")) {
      servletResponse = getNextPage(request.getParameter("encodedCursor"));
    } else if (checkParameter(request, "getPreviousPage")) {
      servletResponse = getPreviousPage(request.getParameter("encodedCursor"));
    }

    Gson gson = new Gson();
    response.setContentType("application/json;");
    response.getWriter().println(gson.toJson(servletResponse));
  }

  /**
   * Checks if the passed in parameter was true or false.
   * @param parameter A string representation of a boolean: "true" or "false".
   * @return the input string parsed to a boolean.
   */
  private boolean checkParameter(HttpServletRequest request, String parameter) {
    return Boolean.parseBoolean(request.getParameter(parameter));
  }

  /** Creates a {@link QueryInformation} based on request parameters. */
  private void createQueryInformation(HttpServletRequest request)
      throws IOException, NullPointerException, NumberFormatException, ParseException {
    String timeZoneId = request.getParameter("timeZoneId");
    String category = request.getParameter("category");
    String dateRange = request.getParameter("dateRange");
    String store = request.getParameter("store");
    String minPrice = request.getParameter("min");
    String maxPrice = request.getParameter("max");

    queryInformation =
        new QueryInformation(timeZoneId, category, dateRange, store, minPrice, maxPrice);
  }

  /**
   * Gets next receipts page from an existing query.
   * @return wrapper object containing the receipts page and encodedCursor.
   */
  private SearchServletResponse getNextPage(String encodedCursor) {
    Cursor cursor = Cursor.fromWebSafeString(encodedCursor);
    FetchOptions options = FetchOptions.Builder.withStartCursor(cursor);
    options.limit(RECEIPTS_PER_PAGE);

    QueryResultList<Entity> results = preparedQuery.asQueryResultList(options);
    ImmutableList<Receipt> receipts = entitiesListToReceiptsList(results);
    encodedCursor = results.getCursor().toWebSafeString();

    return new SearchServletResponse(receipts, encodedCursor);
  }

  /**
   * Gets previous receipts page from an existing query.
   * @return wrapper object containing the receipts page and encodedCursor.
   */
  private SearchServletResponse getPreviousPage(String encodedCursor) {
    Cursor cursor = Cursor.fromWebSafeString(encodedCursor);
    FetchOptions options = FetchOptions.Builder.withEndCursor(cursor);
    options.limit(RECEIPTS_PER_PAGE);

    QueryResultList<Entity> results = preparedQuery.asQueryResultList(options);
    ImmutableList<Receipt> receipts = entitiesListToReceiptsList(results);
    encodedCursor = results.getCursor().toWebSafeString();

    return new SearchServletResponse(receipts, encodedCursor);
  }

  /**
   * Gets receipts from datastore matching queryInformation fields and encodedCursor.
   * @return wrapper object containing the receipts and encodedCursor.
   */
  private SearchServletResponse getMatchingReceipts(boolean isPageLoad) {
    Query query = new Query("Receipt")
                      .addSort("timestamp", SortDirection.DESCENDING)
                      .addSort("__key__", SortDirection.DESCENDING);
    query.addFilter("userId", Query.FilterOperator.EQUAL, userService.getCurrentUser().getUserId());

    // Don't need to set any other filters if it's a pageLoad event.
    if (!isPageLoad) {
      setupQuery(query);
    }

    preparedQuery = datastore.prepare(query);
    QueryResultList<Entity> results =
        preparedQuery.asQueryResultList(FetchOptions.Builder.withLimit(RECEIPTS_PER_PAGE));

    ImmutableList<Receipt> receipts = entitiesListToReceiptsList(results);
    String encodedCursor = results.getCursor().toWebSafeString();

    return new SearchServletResponse(receipts, encodedCursor);
  }

  /** Sets up a {@link Query} with filters set based on which values were input by user. */
  private void setupQuery(Query query) {
    query.addFilter("timestamp", Query.FilterOperator.GREATER_THAN_OR_EQUAL,
        queryInformation.getStartTimestamp());
    query.addFilter(
        "timestamp", Query.FilterOperator.LESS_THAN_OR_EQUAL, queryInformation.getEndTimestamp());

    if (queryInformation.getCategory() != null && queryInformation.getCategory().size() != 0) {
      query.addFilter("categories", Query.FilterOperator.IN, queryInformation.getCategory());
    }

    if (!Strings.isNullOrEmpty(queryInformation.getStore())) {
      query.addFilter("store", Query.FilterOperator.EQUAL, queryInformation.getStore());
    }
  }

  private ImmutableList<Receipt> entitiesListToReceiptsList(QueryResultList<Entity> results) {
    Stream<Receipt> receipts = results.stream().map(this::createReceiptFromEntity);

    if (queryInformation != null) {
      /**
       * Datastore doesn't support queries with multiple inequality filters
       * (i.e price and timestamp) so price filtering is manually done here.
       */
      receipts = receipts.filter(receipt
          -> receipt.getPrice() >= queryInformation.getMinPrice()
              && receipt.getPrice() <= queryInformation.getMaxPrice());
    }

    return receipts.collect(ImmutableList.toImmutableList());
  }

  /** Creates a {@link Receipt} from an {@link Entity}. */
  private Receipt createReceiptFromEntity(Entity entity) {
    long id = entity.getKey().getId();
    String userId = (String) entity.getProperty("userId");
    long timestamp = (long) entity.getProperty("timestamp");
    String imageUrl = (String) entity.getProperty("imageUrl");
    double price = (double) entity.getProperty("price");
    String store = (String) entity.getProperty("store");
    ImmutableSet<String> categories =
        ImmutableSet.copyOf((ArrayList) entity.getProperty("categories"));
    // String rawText = (new Text((String) entity.getProperty("rawText"))).getValue();
    String rawText = "Test";
    return new Receipt(id, userId, timestamp, imageUrl, price, store, categories, rawText);
  }
}
