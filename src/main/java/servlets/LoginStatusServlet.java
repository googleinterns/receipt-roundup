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
import com.google.sps.data.AuthenticationInformation;
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

  private final UserService userService;

  public LoginStatusServlet() {
    userService = UserServiceFactory.getUserService();
  }

  public LoginStatusServlet(UserService userService) {
    this.userService = userService;
  }

  /**
   * Retrieves the login status of the user. If they are logged in, their email and a URL
   * to log out will be returned. Otherwise, a URL to log in will be returned.
   */
  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    boolean loggedIn = userService.isUserLoggedIn();
    AuthenticationInformation authentication;

    if (loggedIn) {
      String logoutUrl = userService.createLogoutURL(LOGIN_PAGE_URL);
      String email = userService.getCurrentUser().getEmail();
      authentication = new AuthenticationInformation(logoutUrl, email);
    } else {
      String loginUrl = userService.createLoginURL(HOME_PAGE_URL);
      authentication = new AuthenticationInformation(loginUrl);
    }

    // Convert the authentication information to JSON.
    String json = convertToJson(authentication);

    // Send the JSON as the response.
    response.setContentType("application/json;");
    response.getWriter().println(json);
  }

  /**
   * Converts an AuthenticationInformation object into a JSON string using the Gson library.
   */
  private static String convertToJson(AuthenticationInformation authenticationInformation) {
    Gson gson = new Gson();
    String json = gson.toJson(authenticationInformation);
    return json;
  }
}
