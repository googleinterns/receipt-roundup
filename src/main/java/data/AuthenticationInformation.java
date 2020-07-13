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

package com.google.sps.data;

/** Information about the logged-in user. */
public class AuthenticationInformation {
  // Note: types cannot be Optional due to JSON conversion.
  private final boolean loggedIn;
  private final String loginUrl;
  private final String logoutUrl;
  private final String email;

  /**
   * Creates a logged out user with a URL to log in.
   */
  public AuthenticationInformation(String loginUrl) {
    this.loggedIn = false;
    this.loginUrl = loginUrl;

    this.logoutUrl = this.email = null;
  }

  /**
   * Creates a logged in user with an email and a URL to log out.
   */
  public AuthenticationInformation(String logoutUrl, String email) {
    this.loggedIn = true;
    this.logoutUrl = logoutUrl;
    this.email = email;

    this.loginUrl = null;
  }
}
