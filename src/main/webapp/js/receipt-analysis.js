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
 * Loads the page if the user is logged in. Otherwise, redirects to the
 * login page.
 */
function load() {
  /* global loadPage */
  loadPage(loadReceiptAnalysis);  // From js/common.js.
}

/** Fetches receipt properties from the server and adds them to the page. */
function loadReceiptAnalysis() {
  const parameters = new URLSearchParams(location.search);

  const date = getDateFromTimestamp(parameters.get('timestamp'));
  const storeName = capitalizeFirstLetters(parameters.get('store'));
  const price = parameters.get('price');
  const categories =
      capitalizeFirstLetters(parameters.get('categories').replace(/,/gi, ', '));
  const imageUrl = parameters.get('image-url');

  document.getElementById('date').innerText = `Transaction Date: ${date}`;
  document.getElementById('store-input').value = storeName;
  document.getElementById('price-input').value = `$${price}`;
  document.getElementById('categories-input').value = categories;

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
  const categoryElement =
      document.querySelector('#category-template').content.cloneNode(true);
  categoryElement.querySelector('#category-name').innerText = category;

  return categoryElement;
}

/**
 * Sends a request to update the receipt.
 */
async function updateReceipt(event) {
  // Prevent the default action of reloading the page on form submission.
  event.preventDefault();

  // TODO: Get transaction date.
  const store = document.getElementById('store-input').value;
  const price =
      convertStringToNumber(document.getElementById('price-input').value);
  const categories = document.getElementById('categories-input').value;

  const formData = new FormData();

  formData.append('store', store);
  formData.append('price', price);

  createCategoryList(categories).forEach((category) => {
    formData.append('categories', category);
  });

  // TODO: Send request to servlet.
}

/**
 * Capitalizes the first letter of each word in a string.
 * TODO: Move this function to a shared JS file.
 */
function capitalizeFirstLetters(lowercasedString) {
  return lowercasedString.split(' ')
      .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ');
}

/** Converts the comma-separated categories string into a list of categories. */
function createCategoryList(categories) {
  return categories.split(',').map((category) => category.trim());
}

/**
 * Converts the formatted price back to a number when the user
 * selects the price input.
 */
function convertPricetoValue(event) {
  const value = event.target.value;
  event.target.value = value ? convertStringToNumber(value) : '';
}

/**
 * Converts a string value into a number, removing all non-numeric characters.
 */
function convertStringToNumber(string) {
  return Number(String(string).replace(/[^0-9.]+/g, ''));
}

/**
 * Converts the number inputted by the user to a formatted string when
 * the user unfocuses from the price input.
 */
function formatCurrency(event) {
  const value = event.target.value;

  if (value) {
    event.target.value =
        convertStringToNumber(value).toLocaleString(undefined, {
          maximumFractionDigits: 2,
          currency: 'USD',
          style: 'currency',
          currencyDisplay: 'symbol',
        });
  } else {
    event.target.value = '';
  }
}
