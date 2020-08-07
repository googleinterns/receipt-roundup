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

    Query query = null;
    QueryInformation queryInformation = null;

    // Query is set differently based on type of search.
    if (checkParameter(request, "isPageLoad")) {
      query = getQuery(/* isPageLoad = */ true, queryInformation);
    } else if (checkParameter(request, "isNewSearch")) {
      try {
        queryInformation = createQueryInformation(request);
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
      query = getQuery(/* isPageLoad = */ false, queryInformation);
    }

    QueryResultList<Entity> results = null;

    // Results retrieved differently based on type of search.
    if (checkParameter(request, "getNextPage")) {
      results = getNextPage(request.getParameter("encodedCursor"), query);
    } else if (checkParameter(request, "getPreviousPage")) {
      results = getPreviousPage(request.getParameter("encodedCursor"), query);
    } else {
      results = getFirstPage(query);
    }

    SearchServletResponse servletResponse = createServletResponse(results, queryInformation);

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
  private QueryInformation createQueryInformation(HttpServletRequest request)
      throws IOException, NullPointerException, NumberFormatException, ParseException {
    String timeZoneId = request.getParameter("timeZoneId");
    String category = request.getParameter("category");
    String dateRange = request.getParameter("dateRange");
    String store = request.getParameter("store");
    String minPrice = request.getParameter("min");
    String maxPrice = request.getParameter("max");

    return new QueryInformation(timeZoneId, category, dateRange, store, minPrice, maxPrice);
  }

  /**
   * Gets next receipts page from an existing query.
   * @return list of receipts as entities.
   */
  private QueryResultList<Entity> getNextPage(String encodedCursor, Query query) {
    Cursor cursor = Cursor.fromWebSafeString(encodedCursor);
    FetchOptions options = FetchOptions.Builder.withStartCursor(cursor);
    options.limit(RECEIPTS_PER_PAGE);

    return datastore.prepare(query).asQueryResultList(options);
  }

  /**
   * Gets previous receipts page from an existing query.
   * @return list of receipts as entities.
   */
  private QueryResultList<Entity> getPreviousPage(String encodedCursor, Query query) {
    Cursor cursor = Cursor.fromWebSafeString(encodedCursor);
    FetchOptions options = FetchOptions.Builder.withEndCursor(cursor);
    options.limit(RECEIPTS_PER_PAGE);

    return datastore.prepare(query).asQueryResultList(options);
  }

  /**
   * Gets first receipts page from a new query.
   * @return list of receipts as entities.
   */
  private QueryResultList<Entity> getFirstPage(Query query) {
    PreparedQuery preparedQuery = datastore.prepare(query);
    QueryResultList<Entity> results =
        preparedQuery.asQueryResultList(FetchOptions.Builder.withLimit(RECEIPTS_PER_PAGE));

    return preparedQuery.asQueryResultList(FetchOptions.Builder.withLimit(RECEIPTS_PER_PAGE));
  }

  /**
   * Creates query to be used to retrieve receipts from datastore.
   * @param isPageLoad If true, gets all receipts, else gets receipts matching queryInformation.
   */
  private Query getQuery(boolean isPageLoad, QueryInformation queryInformation) {
    Query query = new Query("Receipt")
                      .addSort("timestamp", SortDirection.DESCENDING)
                      .addSort("__key__", SortDirection.DESCENDING);
    query.addFilter("userId", Query.FilterOperator.EQUAL, userService.getCurrentUser().getUserId());

    // Don't need to set any other filters if it's a pageLoad event.
    if (!isPageLoad) {
      setupQuery(query, queryInformation);
    }

    return query;
  }

  /** Sets up a {@link Query} with filters set based on which values were input by user. */
  private void setupQuery(Query query, QueryInformation queryInformation) {
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

  /** Creates a SearchServletResponse object containing information for the client. */
  private SearchServletResponse createServletResponse(
      QueryResultList<Entity> results, QueryInformation queryInformation) {
    ImmutableList<Receipt> receipts = entitiesListToReceiptsList(results, queryInformation);
    String encodedCursor = results.getCursor().toWebSafeString();
    return new SearchServletResponse(receipts, encodedCursor);
  }

  private ImmutableList<Receipt> entitiesListToReceiptsList(
      QueryResultList<Entity> results, QueryInformation queryInformation) {
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
