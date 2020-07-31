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

  const loadingIntervalId = startLoading();

  const uploadUrl = await fetchBlobstoreUrl();
  const image = fileInput.files[0];
  const formData = new FormData();
  formData.append('receipt-image', image);
  // TODO: Remove price and date from form data
  formData.append('price', 9.99);
  formData.append('date', new Date().getTime());

  const response = await fetch(uploadUrl, {method: 'POST', body: formData});

  // Restore the cursor after the upload request has loaded.
  document.body.style.cursor = 'default';

  // Create an alert and re-enable the submit button and file input if there is
  // an error.
  if (response.status !== 200) {
    const submitButton = document.getElementById('submit-receipt');
    submitButton.innerText = 'Error!';

    // Stop the loading animation.
    document.getElementById('loading').classList.add('hidden');
    clearInterval(loadingIntervalId);

    // Delay the alert so the above changes can render first.
    const error = await response.text();
    setTimeout(() => {
      alert(error);

      // Restore the file input and submit button.
      fileInput.disabled = false;
      submitButton.disabled = false;
      submitButton.innerText = 'Add Receipt';
    }, 10);
    return;
  }

  const json = (await response.json());
  const params = setUrlParameters(json);

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
 * Displays the loading animation and disables the submit button and file input.
 * @return {number} The ID value of the setInterval() timer.
 */
function startLoading() {
  document.body.style.cursor = 'wait';

  const submitButton = document.getElementById('submit-receipt');
  submitButton.disabled = true;
  submitButton.innerText = 'Analyzing...';

  const fileInput = document.getElementById('receipt-image-input');
  fileInput.disabled = true;

  // Display the loading image, which is hidden by default.
  const loadingBar = document.getElementsByClassName('loading-bar')[0].ldBar;
  loadingBar.set(1);
  document.getElementById('loading').classList.remove('hidden');

  // Start the loading animation loop.
  let increment = 1;
  return setInterval(() => {
    const value = loadingBar.value;

    // Flip directions when the bar is filled and empty.
    if (value >= 100 || value <= 0) {
      increment *= -1;
    }

    loadingBar.set(value + increment);
  }, 20);
}

/**
 * Creates URL parameters using the properties of the receipt in the given JSON
 * response.
 */
function setUrlParameters(json) {
  const receipt = json.propertyMap;
  const params = new URLSearchParams();
  params.append('id', json.key.id);
  params.append('image-url', receipt.imageUrl.value);

  // Add fields that were successfully generated.
  if (receipt.categories.length > 0) {
    params.append('categories', receipt.categories);
  }
  if (receipt.price) {
    params.append('price', receipt.price);
  }
  if (receipt.store) {
    params.append('store', receipt.store);
  }
  if (receipt.timestamp) {
    params.append('timestamp', receipt.timestamp);
  }
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
