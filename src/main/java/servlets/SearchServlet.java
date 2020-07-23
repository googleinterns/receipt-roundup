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

import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Text;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.sps.data.QueryInformation;
import com.google.sps.data.Receipt;
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
  private final UserService userService;

  public SearchServlet() {
    datastore = DatastoreServiceFactory.getDatastoreService();
    userService = UserServiceFactory.getUserService();
  }

  public SearchServlet(DatastoreService datastore) {
    this.datastore = datastore;
    userService = UserServiceFactory.getUserService();
  }

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    if (!userService.isUserLoggedIn()) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      response.getWriter().println(AUTHENTICATION_ERROR_MESSAGE);
      return;
    }

    QueryInformation queryInformation;
    ImmutableList<Receipt> receipts;

    if (Boolean.parseBoolean(request.getParameter("isNewLoad"))) {
      receipts = getAllReceipts();
    } else {
      try {
        queryInformation = createQueryInformation(request, response);
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

      receipts = getMatchingReceipts(queryInformation);
    }

    Gson gson = new Gson();
    response.setContentType("application/json;");
    response.getWriter().println(gson.toJson(receipts));
  }

  /** Creates a {@link QueryInformation} based on request parameters. */
  private QueryInformation createQueryInformation(
      HttpServletRequest request, HttpServletResponse response)
      throws IOException, NullPointerException, NumberFormatException, ParseException {
    String timeZoneId = request.getParameter("timeZoneId");
    String category = request.getParameter("category");
    String dateRange = request.getParameter("dateRange");
    String store = request.getParameter("store");
    String minPrice = request.getParameter("min");
    String maxPrice = request.getParameter("max");

    QueryInformation queryInformation =
        new QueryInformation(timeZoneId, category, dateRange, store, minPrice, maxPrice);

    return queryInformation;
  }

  /** Returns ImmutableList of receipts from datastore matching queryInformation fields. */
  private ImmutableList<Receipt> getMatchingReceipts(QueryInformation queryInformation) {
    Query query = setupQuery(queryInformation);

    /**
     * Datastore doesn't support queries with multiple inequality filters
     * (i.e price and timestamp) so price filtering is manually done here.
     */
    return datastore.prepare(query)
        .asList(FetchOptions.Builder.withDefaults())
        .stream()
        .map(this::createReceiptFromEntity)
        .filter(receipt
            -> receipt.getPrice() >= queryInformation.getMinPrice()
                && receipt.getPrice() <= queryInformation.getMaxPrice())
        .collect(ImmutableList.toImmutableList());
  }

  /** Returns ImmutableList of all receipts from datastore. */
  private ImmutableList<Receipt> getAllReceipts() {
    Query query = new Query("Receipt");

    return datastore.prepare(query)
        .asList(FetchOptions.Builder.withDefaults())
        .stream()
        .map(this::createReceiptFromEntity)
        .collect(ImmutableList.toImmutableList());
  }

  /** Creates a {@link Query} with filters set based on which values were input by user. */
  private Query setupQuery(QueryInformation queryInformation) {
    Query query = new Query("Receipt");

    if (queryInformation.getCategory() != null && queryInformation.getCategory().size() != 0) {
      query.addFilter("categories", Query.FilterOperator.IN, queryInformation.getCategory());
    }

    query.addFilter("timestamp", Query.FilterOperator.GREATER_THAN_OR_EQUAL,
        queryInformation.getStartTimestamp());
    query.addFilter(
        "timestamp", Query.FilterOperator.LESS_THAN_OR_EQUAL, queryInformation.getEndTimestamp());

    if (!Strings.isNullOrEmpty(queryInformation.getStore())) {
      query.addFilter("store", Query.FilterOperator.EQUAL, queryInformation.getStore());
    }

    return query;
  }

  /** Creates a {@link Receipt} from an {@link Entity}. */
  private Receipt createReceiptFromEntity(Entity entity) {
    long id = entity.getKey().getId();
    String userId = (String) entity.getProperty("userId");
    long timestamp = (long) entity.getProperty("timestamp");
    BlobKey blobKey = (BlobKey) entity.getProperty("blobKey");
    String imageUrl = (String) entity.getProperty("imageUrl");
    double price = (double) entity.getProperty("price");
    String store = (String) entity.getProperty("store");
    ImmutableSet<String> categories =
        ImmutableSet.copyOf((ArrayList) entity.getProperty("categories"));
    // String rawText = (new Text((String) entity.getProperty("rawText"))).getValue();
    String rawText = "Test";
    return new Receipt(id, userId, timestamp, blobKey, imageUrl, price, store, categories, rawText);
  }
}
