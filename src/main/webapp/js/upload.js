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
 * Redirects the user back to the home page when the cancel button is clicked.
 */
function cancelUpload() {
  window.location.href = 'index.html';
}

/**
 * Loads the form to upload a new receipt and displays an error message
 * if the upload failed.
 */
function loadForm() {
  fetchBlobstoreUrlAndShowForm();
  checkFileUpload();
}

/**
 * Gets a Blobstore upload URL and connects it to the form for uploading a
 * receipt image, then reveals the form on the page.
 */
async function fetchBlobstoreUrlAndShowForm() {
  const response = await fetch('/upload-receipt');
  const imageUploadUrl = await response.text();

  const uploadForm = document.getElementById('upload-form');
  uploadForm.action = imageUploadUrl;

  // The form is hidden by default.
  uploadForm.classList.remove('hidden');
}

/**
 * Displays an error message if the user did not upload a JPEG file.
 */
function checkFileUpload() {
  const queryString = window.location.search;
  const urlParameters = new URLSearchParams(queryString);

  if (urlParameters.get('upload-error') === 'true') {
    alert('A JPEG file was not uploaded.');
  }
}
