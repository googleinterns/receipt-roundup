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
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.gson.Gson;
import com.google.sps.data.Receipt;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Spliterator;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Servlet that searches and returns matching receipts from datastore. */
@WebServlet("/search-receipts")
public class SearchServlet extends HttpServlet {
  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    String desiredLabel = request.getParameter("label");
    List<Receipt> receipts = getReceiptsWithMatchingLabel(desiredLabel);

    Gson gson = new Gson();
    response.setContentType("application/json;");
    response.getWriter().println(gson.toJson(receipts));
  }

  /* Return a list of receipts from datastore with the same label as desiredLabel.*/
  private List<Receipt> getReceiptsWithMatchingLabel(String desiredLabel) {
    // Set filter to retrieve only receipts with label equal to desiredLabel.
    Filter matchingLabels = new FilterPredicate("label", FilterOperator.EQUAL, desiredLabel);
    Query query = new Query("Receipt");
    query.setFilter(matchingLabels);

    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

    // Convert matching receipt entities to receipt objects and add to return list.
    Spliterator<Entity> spliterator = datastore.prepare(query).asIterable().spliterator();
    Stream<Entity> entities = StreamSupport.stream(spliterator, false);
    List<Receipt> receipts = entities.map(entity -> {
      long id = entity.getKey().getId();
      long userId = (long) entity.getProperty("userId");
      long timestamp = (long) entity.getProperty("timestamp");
      BlobKey blobKey = (BlobKey) entity.getProperty("blobKey");
      String imageUrl = (String) entity.getProperty("imageUrl");
      double price = (double) entity.getProperty("price");
      String store = (String) entity.getProperty("store");
      String label = (String) entity.getProperty("label");
      Set<String> categories = new HashSet<String>((ArrayList) entity.getProperty("categories"));
      String rawText = (String) entity.getProperty("rawText");
    return new Receipt(id, userId, timestamp, blobKey, imageUrl, price, store, label, categories, rawText);
    }).collect(Collectors.toList());

    return receipts;
  }
}
