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

package com.google.sps;

import static org.mockito.Mockito.when;

import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.appengine.tools.development.testing.LocalUserServiceTestConfig;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.sps.data.Account;
import com.google.sps.servlets.LoginStatusServlet;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
public final class LoginStatusServletTest {
  private static final String DOMAIN_NAME = "gmail.com";
  private static final String USER_EMAIL = "test@gmail.com";
  private static final String USER_ID = "testID";

  private static final String HOME_PAGE_URL = "/";
  private static final String LOGIN_PAGE_URL = "/login.html";

  private static final String LOGIN_URL = "/_ah/login?continue\u003d%2F";
  private static final String LOGOUT_URL = "/_ah/logout?continue\u003d%2Flogin.html";

  private static final Account LOGGED_OUT_ACCOUNT = new Account(LOGIN_URL);
  private static final Account LOGGED_IN_ACCOUNT = new Account(LOGOUT_URL, USER_EMAIL);

  // Uses local UserService.
  private final LocalServiceTestHelper helper =
      new LocalServiceTestHelper(new LocalUserServiceTestConfig())
          .setEnvEmail(USER_EMAIL)
          .setEnvAuthDomain(DOMAIN_NAME)
          .setEnvAttributes(new HashMap(
              ImmutableMap.of("com.google.appengine.api.users.UserService.user_id_key", USER_ID)));

  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;

  private LoginStatusServlet servlet;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    helper.setUp();

    servlet = new LoginStatusServlet();
  }

  @After
  public void tearDown() {
    helper.tearDown();
  }

  @Test
  public void doGetIfUserIsNotLoggedIn() throws IOException {
    helper.setEnvIsLoggedIn(false);

    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(writer);

    servlet.doGet(request, response);
    writer.flush();

    String actual = stringWriter.toString();
    String expected = getExpectedJsonResponse(LOGGED_OUT_ACCOUNT);
    Assert.assertEquals(expected, actual);
  }

  @Test
  public void doGetIfUserIsLoggedIn() throws IOException {
    helper.setEnvIsLoggedIn(true);

    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(writer);

    servlet.doGet(request, response);
    writer.flush();

    String actual = stringWriter.toString();
    String expected = getExpectedJsonResponse(LOGGED_IN_ACCOUNT);
    Assert.assertEquals(expected, actual);
  }

  /**
   * Converts the expected account object into a JSON string and adds a new line
   * at the end to compare to the actual response.
   */
  private String getExpectedJsonResponse(Account account) {
    return new Gson().toJson(account) + "\n";
  }
}
