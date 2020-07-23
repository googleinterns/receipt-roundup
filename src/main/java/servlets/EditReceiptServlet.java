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
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.sps.servlets.UploadReceiptServlet.InvalidDateException;
import com.google.sps.servlets.UploadReceiptServlet.InvalidPriceException;
import java.io.IOException;
import java.time.Clock;
import java.util.Arrays;
import java.util.logging.Logger;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet with a POST handler that updates an existing receipt entity in Datastore.
 */
@WebServlet("/edit-receipt")
public class EditReceiptServlet extends HttpServlet {
  private static final String USER_NOT_LOGGED_IN_WARNING =
      "User must be logged in to edit a receipt.";

  // Logs to System.err by default.
  private static final Logger logger = Logger.getLogger(UploadReceiptServlet.class.getName());

  private final DatastoreService datastore;
  private final UserService userService = UserServiceFactory.getUserService();
  private final Clock clock;

  public EditReceiptServlet() {
    this.datastore = DatastoreServiceFactory.getDatastoreService();
    this.clock = Clock.systemDefaultZone();
  }

  public EditReceiptServlet(DatastoreService datastore, Clock clock) {
    this.datastore = datastore;
    this.clock = clock;
  }

  /**
   * Updates the receipt entity with the given ID using the properties sent in the request body from
   * the receipt analysis page.
   */
  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    if (!userService.isUserLoggedIn()) {
      logger.warning(USER_NOT_LOGGED_IN_WARNING);
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      response.getWriter().println(USER_NOT_LOGGED_IN_WARNING);
      return;
    }

    Entity receipt;
    try {
      long id = Long.parseLong(request.getParameter("id"));
      receipt = createUpdatedReceipt(request, id);
    } catch (EntityNotFoundException | InvalidPriceException | InvalidDateException
        | NumberFormatException e) {
      logger.warning(e.toString());
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      response.getWriter().println(e.toString());
      return;
    }

    datastore.put(receipt);

    // Send the JSON receipt as the response.
    String json = new Gson().toJson(receipt);
    response.setContentType("application/json;");
    response.getWriter().println(json);
  }

  /**
   * Uses the given ID to lookup the receipt, modifies the entity using the fields in the request
   * body, and stores it back in the database.
   */
  private Entity createUpdatedReceipt(HttpServletRequest request, long id)
      throws EntityNotFoundException, InvalidPriceException, InvalidDateException {
    Key key = KeyFactory.createKey("Receipt", id);
    Entity receipt = datastore.get(key);

    String store = UploadReceiptServlet.sanitize(request.getParameter("store"));
    double price = UploadReceiptServlet.roundPrice(request.getParameter("price"));
    long timestamp = UploadReceiptServlet.getTimestamp(request, clock);

    receipt.setProperty("categories", getCategories(request));
    receipt.setProperty("store", store);
    receipt.setProperty("price", price);
    receipt.setProperty("timestamp", timestamp);

    return receipt;
  }

  /**
   * Sanitizes and formats the set of categories from the request.
   */
  private ImmutableSet<String> getCategories(HttpServletRequest request) {
    return Arrays.stream(request.getParameterValues("categories"))
        .map(UploadReceiptServlet::sanitize)
        .collect(ImmutableSet.toImmutableSet());
  }
}
