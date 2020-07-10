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

import com.google.appengine.api.datastore.DatastoreFailureException;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import java.io.IOException;
import java.lang.NumberFormatException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Servlet responsible for deleting a single receipt. */
@WebServlet("/delete-receipt")
public class DeleteReceiptServlet extends HttpServlet {
  private static DatastoreService datastore;

  public DeleteReceiptServlet() {}

  public DeleteReceiptServlet(DatastoreService datastore) {
    this.datastore = datastore;
  }

  @Override
  public void init() {
    datastore = DatastoreServiceFactory.getDatastoreService();
  }

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    long id = 0;

    try {
      id = Long.parseLong(request.getParameter("id"));
    } catch (NumberFormatException exception) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      response.getWriter().println(
          "Invalid ID: Receipt unable to be deleted at this time, please try again.");
      return;
    }

    Key key = KeyFactory.createKey("Receipt", id);
    datastore.delete(key);
  }
}