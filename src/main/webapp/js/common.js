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

/**
 * Loads the page elements if the user is logged in. Otherwise, redirects to the
 * login page.
 * @param {function(): undefined} loadElements Loads elements on the page if the
 *     user is logged in.
 * @param {?function(object): undefined} loginOperation Uses the account fetched
 *     from the login status servlet to manipulate the DOM.
 */
async function loadPage(loadElements, loginOperation) {
  const loggedIn = await checkAuthentication(loginOperation);

  if (loggedIn) {
    document.body.style.display = 'block';
    loadElements();
  }
}

/**
 * Fetches the login status and redirects to the login page if the user is
 * logged out.
 * @param {?function(object): undefined} loginOperation Uses the account fetched
 *     from the login status servlet to manipulate the DOM.
 * @return {boolean} Whether the user is logged in or not.
 */
async function checkAuthentication(loginOperation) {
  const response = await fetch('/login-status');
  const account = await response.json();

  // Redirect to the login page if the user is not logged in.
  if (!account.loggedIn) {
    window.location.replace('/login.html');
    return false;
  }

  if (loginOperation) {
    loginOperation(account);
  }

  return true;
}

/** Capitalize the first letter of each word in a string. */
function capitalizeFirstLetters(lowercasedString) {
  return lowercasedString.split(' ')
      .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ');
}
