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

import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.gson.Gson;
import com.google.sps.data.Account;
import java.io.IOException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet with a GET handler that returns the login status and information about the user.
 */
@WebServlet("/login-status")
public class LoginStatusServlet extends HttpServlet {
  private static final String HOME_PAGE_URL = "/";
  private static final String LOGIN_PAGE_URL = "/login.html";

  private final UserService userService = UserServiceFactory.getUserService();

  /**
   * Retrieves the login status of the user. If they are logged in, their email and a URL
   * to log out will be returned. Otherwise, a URL to log in will be returned.
   */
  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    boolean loggedIn = userService.isUserLoggedIn();
    Account account;

    if (loggedIn) {
      String logoutUrl = userService.createLogoutURL(LOGIN_PAGE_URL);
      String email = userService.getCurrentUser().getEmail();
      account = new Account(logoutUrl, email);
    } else {
      String loginUrl = userService.createLoginURL(HOME_PAGE_URL);
      account = new Account(loginUrl);
    }

    // Convert the account data to JSON.
    String json = new Gson().toJson(account);

    // Send the JSON as the response.
    response.setContentType("application/json;");
    response.getWriter().println(json);
  }
}
