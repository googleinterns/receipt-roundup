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

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.sps.data.SpendingAnalytics;
import java.io.IOException;
import java.util.HashMap;
import java.util.stream.Stream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Servlet that computes user spending analytics from datastore data. */
@WebServlet("/compute-analytics")
public class SpendingAnalyticsServlet extends HttpServlet {
  private static final String AUTHENTICATION_ERROR_MESSAGE =
      "No Authentication: User must be logged in to search receipts.";

  private final DatastoreService datastore;
  private final UserService userService;

  public SpendingAnalyticsServlet() {
    datastore = DatastoreServiceFactory.getDatastoreService();
    userService = UserServiceFactory.getUserService();
  }

  public SpendingAnalyticsServlet(DatastoreService datastore) {
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

    SpendingAnalytics analytics = getSpendingAnalytics();

    Gson gson = new Gson();
    response.setContentType("application/json;");
    response.getWriter().println(gson.toJson(analytics));
  }

  /** Returns information for both category and store analytics. */
  private SpendingAnalytics getSpendingAnalytics() {
    Query query = new Query("Receipt");
    query.addFilter("userId", Query.FilterOperator.EQUAL, userService.getCurrentUser().getUserId());
    ImmutableSet<Entity> allReceipts = datastore.prepare(query)
                                           .asList(FetchOptions.Builder.withDefaults())
                                           .stream()
                                           .collect(ImmutableSet.toImmutableSet());

    SpendingAnalytics analytics = new SpendingAnalytics(allReceipts);

    return analytics;
  }
}