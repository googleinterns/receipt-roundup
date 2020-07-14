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

/** Fetches receipt properties from the server and adds them to the page. */
function loadReceiptAnalysis() {
  const timestamp = new URLSearchParams(location.search).get('timestamp');
  const storeName = new URLSearchParams(location.search).get('store');
  const total = new URLSearchParams(location.search).get('price');
  const categories =
      new URLSearchParams(location.search).get('categories').split(',');
  const imageUrl = new URLSearchParams(location.search).get('image-url');

  document.getElementById('timestamp').innerText = `Upload Date: ${timestamp}`;
  document.getElementById('store-name').innerText = `Store Name: ${storeName}`;
  document.getElementById('total').innerText = `Total Price: $${total}`;

  for (let i = 0; i < categories.length && i < 3; i++) {
    document.getElementById('category-' + i).innerText = categories[i];
  }

  document.getElementById('receipt-image').src = imageUrl;
}
