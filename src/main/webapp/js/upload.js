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
 * Sends a request to add a receipt to Blobstore.
 */
async function uploadReceipt(event) {
  // Prevent the default action of reloading the page on form submission.
  event.preventDefault();

  const uploadUrl = await fetchBlobstoreUrl();
  const label = document.getElementById('label-input').value;
  const image = document.getElementById('receipt-image-input').files[0];

  const formData = new FormData();
  formData.append('label', label);
  formData.append('receipt-image', image);

  const response = await fetch(uploadUrl, {method: 'POST', body: formData});

  if (response.status === 413) {
    alert('The maximum file size is 5 MB.');
  } else if (response.status === 400) {
    alert('A JPEG file was not uploaded.');
  } else {
    // TODO: Redirect to receipt analysis page for MVP
    window.location.href = '/';
  }
}

/**
 * Gets a Blobstore upload URL for uploading a receipt image.
 */
async function fetchBlobstoreUrl() {
  const response = await fetch('/upload-receipt');
  const imageUploadUrl = await response.text();
  return imageUploadUrl;
}

/**
 * Displays an error message if the user selects a file larger than 5 MB.
 */
function checkFileSize() {
  const fileInput = document.getElementById('receipt-image-input');
  const MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;

  if (fileInput.files.length > 0 &&
      fileInput.files[0].size > MAX_FILE_SIZE_BYTES) {
    alert('The selected file exceeds the maximum file size of 5 MB.');
    fileInput.value = '';
  }
}
