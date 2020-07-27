/**
 * Fetches the login status from the server and creates a login link. If the
 * user is already logged in, the page redirects to the home page.
 */
async function getLoginUrl() {
  const response = await fetch('/login-status');
  const json = await response.json();

  // Redirect to the home page if the user is already logged in.
  if (json.loggedIn) {
    location.href = '/index.html';
    return;
  }

  const loginLink = document.getElementById('login-link');
  loginLink.href = json.loginUrl;

  document.body.style.display = 'block';
}
