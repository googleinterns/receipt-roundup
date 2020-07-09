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

import com.google.appengine.api.blobstore.BlobKey;
import com.google.common.collect.ImmutableSet;

/** Information about the logged-in user. */
public class AuthenticationInformation {
  // Note: types cannot be Optional due to JSON conversion.
  private boolean loggedIn;
  private String loginUrl;
  private String logoutUrl;
  private String email;

  // Logged out user.
  public AuthenticationInformation(String loginUrl) {
    this.loginUrl = loginUrl;
  }

  // Logged in user.
  public AuthenticationInformation(String logoutUrl, String email) {
    this.loggedIn = true;
    this.logoutUrl = logoutUrl;
    this.email = email;
  }
}
