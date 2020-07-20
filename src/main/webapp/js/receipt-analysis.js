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
  const parameters = new URLSearchParams(location.search);

  const date = getDateFromTimestamp(parameters.get('timestamp'));
  const storeName = parameters.get('store');
  const total = parameters.get('price');
  const categories = parameters.get('categories').split(',');
  const imageUrl = parameters.get('image-url');

  document.getElementById('date').innerText = `Transaction Date: ${date}`;
  document.getElementById('store-name').innerText = `Store Name: ${storeName}`;
  document.getElementById('total').innerText = `Total Price: $${total}`;

  const categoriesContainer = document.getElementById('categories-container');
  categoriesContainer.innerHTML = '';

  for (let i = 0; i < categories.length && i < 3; i++) {
    categoriesContainer.appendChild(buildCategoryElement(categories[i]));
  }

  document.getElementById('receipt-image').src = imageUrl;
}

/** Converts a timestamp string into the equivalent date string. */
function getDateFromTimestamp(timestamp) {
  const time = parseInt(timestamp);

  // Only return the year, month, and day
  return new Date(time).toISOString().substring(0, 10);
}

/** Builds the div element for a category along with its children. */
function buildCategoryElement(category) {
  const categoryElement = document.createElement('div');
  categoryElement.className = 'col d-flex justify-content-center';
  categoryElement.innerHTML =
      '<h4><span class="badge badge-pill badge-secondary">' +
      `${category}</span></h4>`;

  return categoryElement;
}
