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
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.sps.data.Receipt;
import java.io.IOException;
import java.util.ArrayList;
import java.util.stream.Stream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Servlet that searches and returns matching receipts from datastore. */
@WebServlet("/search-receipts")
public class SearchServlet extends HttpServlet {
  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    String categories = request.getParameter("categories");
    ImmutableList<Receipt> receipts = getReceiptsWithMatchingCategories(categories);

    Gson gson = new Gson();
    response.setContentType("application/json;");
    response.getWriter().println(gson.toJson(receipts));
  }

  /** Returns ImmutableList of receipts from datastore with the same categories. */
  private ImmutableList<Receipt> getReceiptsWithMatchingCategories(String categories) {
    // Set filter to retrieve only receipts with categories equal to categories.
    Filter matchingCategories = new FilterPredicate("categories", FilterOperator.EQUAL, categories);
    Query query = new Query("Receipt");
    query.setFilter(matchingCategories);

    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

    // Convert matching receipt entities to receipt objects and add to return list.
    return datastore.prepare(query)
        .asList(FetchOptions.Builder.withDefaults())
        .stream()
        .map(this::createReceiptFromEntity)
        .collect(ImmutableList.toImmutableList());
  }

  /** Creates a {@link Receipt} from an {@link Entity}. */
  private Receipt createReceiptFromEntity(Entity entity) {
    long id = entity.getKey().getId();
    long userId = (long) entity.getProperty("userId");
    long timestamp = (long) entity.getProperty("timestamp");
    BlobKey blobKey = (BlobKey) entity.getProperty("blobKey");
    String imageUrl = (String) entity.getProperty("imageUrl");
    double price = (double) entity.getProperty("price");
    String store = (String) entity.getProperty("store");
    ImmutableSet<String> categories =
        ImmutableSet.copyOf((ArrayList) entity.getProperty("categories"));
    String rawText = (String) entity.getProperty("rawText");
    return new Receipt(id, userId, timestamp, blobKey, imageUrl, price, store, categories, rawText);
  }
}
