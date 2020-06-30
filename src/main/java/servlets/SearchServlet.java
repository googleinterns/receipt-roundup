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
import com.google.cloud.language.v1.Document;
import com.google.cloud.language.v1.LanguageServiceClient;
import com.google.cloud.language.v1.Sentiment;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.sps.data.Receipt;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    List<Receipt> receipts = new ArrayList<>();
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

    // Iterate through matching receipt entities, convert them to receipt objects, and add to return
    // list.
    for (Entity entity : datastore.prepare(query).asIterable()) {
      long id = entity.getKey().getId();
      long userId = (long) entity.getProperty("userId");
      long timestamp = (long) entity.getProperty("timestamp");
      BlobKey blobkey = (BlobKey) entity.getProperty("blobkey");
      String imageURL = (String) entity.getProperty("imageURL");
      double price = (double) entity.getProperty("price");
      String store = (String) entity.getProperty("store");
      String label = (String) entity.getProperty("label");
      Set<String> categories = new HashSet<String>((ArrayList) entity.getProperty("categories"));
      String rawText = (String) entity.getProperty("rawText");

      Receipt receipt = new Receipt(
          id, userId, timestamp, blobkey, imageURL, price, store, label, categories, rawText);
      receipts.add(receipt);
    }
    return receipts;
  }
}
