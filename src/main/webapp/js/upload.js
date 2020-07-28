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
 * Verifies that the user is logged in.
 */
async function load() {
  /* global loadPage */
  loadPage();  // From js/common.js
}

/**
 * Redirects the user back to the home page when the cancel button is clicked.
 */
function cancelUpload() {
  window.location.href = 'index.html';
}

/**
 * Sends a request to add a receipt to Blobstore then redirects to the receipt
 * analysis page.
 */
async function uploadReceipt(event) {
  // Prevent the default action of reloading the page on form submission.
  event.preventDefault();

  const fileInput = document.getElementById('receipt-image-input');
  if (fileInput.files.length === 0) {
    alert('A JPEG image is required.');
    return;
  }

  // Change to the loading cursor and disable the submit button.
  document.body.style.cursor = 'wait';
  const submitButton = document.getElementById('submit-receipt');
  submitButton.disabled = true;

  const uploadUrl = await fetchBlobstoreUrl();
  const image = fileInput.files[0];

  const formData = new FormData();
  formData.append('receipt-image', image);

  const response = await fetch(uploadUrl, {method: 'POST', body: formData});

  // Restore the cursor after the upload request has loaded.
  document.body.style.cursor = 'default';

  // Create an alert and re-enable the submit button if there is an error.
  if (response.status !== 200) {
    alert(await response.text());
    submitButton.disabled = false;
    return;
  }

  const json = (await response.json());
  const receipt = json.propertyMap;
  const params = new URLSearchParams();
  params.append('id', json.key.id);
  params.append('categories', receipt.categories);
  params.append('image-url', receipt.imageUrl);
  params.append('price', receipt.price);
  params.append('store', receipt.store);
  params.append('timestamp', receipt.timestamp);

  // Redirect to the receipt analysis page.
  window.location.href = `/receipt-analysis.html?${params.toString()}`;
}

/**
 * Gets a Blobstore upload URL for uploading a receipt image.
 * @return {string} A Blobstore upload URL.
 */
async function fetchBlobstoreUrl() {
  const response = await fetch('/upload-receipt');
  const imageUploadUrl = await response.text();
  return imageUploadUrl;
}

/**
 * Adds the selected file name to the input label and checks that the size of
 * the uploaded file is within the limit.
 */
function displayFileName() {
  const fileLabel = document.getElementById('receipt-filename-label');
  const DEFAULT_FILE_LABEL = 'Choose file';

  if (checkFileSize()) {
    const fileInput = document.getElementById('receipt-image-input');
    const fileName = fileInput.value.split('\\').pop();
    fileLabel.innerText = fileName;
  } else {
    fileLabel.innerText = DEFAULT_FILE_LABEL;
  }
}

/**
 * Displays an error message if the user selects a file larger than 10 MB.
 * @return {boolean} Whether a file is selected and has size less than 10 MB.
 */
function checkFileSize() {
  const fileInput = document.getElementById('receipt-image-input');
  const MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;

  // Return if the user did not select a file.
  if (fileInput.files.length === 0) {
    return false;
  }

  if (fileInput.files[0].size > MAX_FILE_SIZE_BYTES) {
    alert('The selected file exceeds the maximum file size of 10 MB.');
    fileInput.value = '';
    return false;
  }

  return true;
}
