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
import com.google.cloud.language.v1.Document;
import com.google.cloud.language.v1.LanguageServiceClient;
import com.google.cloud.language.v1.Sentiment;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.HashSet; 
import java.util.List;
import java.util.Set; 
import java.io.IOException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.google.sps.data.Receipt;

/** Servlet that adds a comment to the datastore. */
@WebServlet("/search-receipts")
public class SearchServlet extends HttpServlet {

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    String label = request.getParameter("label");
    System.out.println("Label is: " + label);

    // Filter niceComments = new FilterPredicate("sentimentScore", FilterOperator.GREATER_THAN_OR_EQUAL, COMMENT_FILTER_THRESHOLD);
    // Query query = new Query("Comment").addSort("sentimentScore", SortDirection.DESCENDING);    
    // query.setFilter(niceComments);

    // DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    // List<Entity> results = datastore.prepare(query).asList(FetchOptions.Builder.withLimit(numCommentsToDisplay));

    // List<Comment> comments = new ArrayList<>();
    // for (Entity entity : results) {
    //   long id = entity.getKey().getId(); 
    //   String name = (String) entity.getProperty("name");
    //   String email = (String) entity.getProperty("email");
    //   String subject = (String) entity.getProperty("subject");
    //   String message = (String) entity.getProperty("message");
    //   long timestamp = (long) entity.getProperty("timestamp");
    //   double sentimentScore = (double) entity.getProperty("sentimentScore");

    //   Comment comment = new Comment(id, name, email, subject, message, timestamp, sentimentScore);
    //   comments.add(comment);
    // }

    List<Receipt> receipts = new ArrayList<>();
    addTestReceipts(receipts);

    Gson gson = new Gson();
    response.setContentType("application/json;");
    response.getWriter().println(gson.toJson(receipts)); 
  }

  private void addTestReceipts(List<Receipt> receipts) {
    // Create test blobkey object
    BlobKey blobkey = new BlobKey("Test");

    // Create test categories tags
    Set<String> categories = new HashSet<String>(); 
    categories.add("Candy"); 
    categories.add("Drink"); 
    categories.add("Personal");

    // Create test text translation
    String rawText = "Wal-Mart\nAlways Low Prices at Wal-Mart\nAlways:\nWe Sell For Less\nManager Tim Stryczek...";
    
    // Add test receipts
    Receipt receipt = new Receipt(1, 123, 6292020, blobkey, "img/walmart-receipt.jpg", 26.12, "Walmart", "hello", categories, rawText);
    receipts.add(receipt);

    Receipt receipt1 = new Receipt(2, 123, 6302020, blobkey, "img/walmart-receipt.jpg", 26.12, "Walmart", "hello", categories, rawText);
    receipts.add(receipt1);

    Receipt receipt2 = new Receipt(3, 123, 7012020, blobkey, "img/walmart-receipt.jpg", 26.12, "Walmart", "test", categories, rawText);
    receipts.add(receipt2);

    Receipt receipt3 = new Receipt(4, 123, 7022020, blobkey, "img/walmart-receipt.jpg", 26.12, "Walmart", "test", categories, rawText);
    receipts.add(receipt3);

    Receipt receipt4 = new Receipt(5, 123, 7032020, blobkey, "img/walmart-receipt.jpg", 26.12, "Walmart", "test", categories, rawText);
    receipts.add(receipt4);
  }
}
