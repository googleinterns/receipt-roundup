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

/* global capitalizeFirstLetters */

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
  const receipt = getReceiptFromQueryString();

  // Set the value and max value of the date input field.
  const dateInput = document.getElementById('date-input');
  const today = formatDate(new Date());
  dateInput.value = receipt.date || today;
  dateInput.max = today;

  if (receipt.storeName) {
    document.getElementById('store-input').value = receipt.storeName;
  }
  if (receipt.price) {
    document.getElementById('price-input').value =
        Number(receipt.price).toLocaleString(undefined, {
          maximumFractionDigits: 2,
          currency: 'USD',
          style: 'currency',
          currencyDisplay: 'symbol',
        });
  }
  if (receipt.categories) {
    document.getElementById('categories-input').value = receipt.categories;
  }

  document.getElementById('receipt-image').src = receipt.imageUrl;
}

/**
 * Extracts the receipt properties from the query string.
 * @return {object} The extracted receipt.
 */
function getReceiptFromQueryString() {
  const parameters = new URLSearchParams(location.search);

  const date = formatReceiptProperty('timestamp', getDateFromTimestamp);
  const storeName = formatReceiptProperty('store', capitalizeFirstLetters);
  const price = parameters.get('price');
  const categories = formatReceiptProperty('categories', formatCategories);
  const imageUrl = parameters.get('image-url');

  return {date, storeName, price, categories, imageUrl};
}

/**
 * Extracts the given property from the query string and formats it.
 * @param {string} propertyName The name of the query string property.
 * @param {function(string): *} format A function to format the specified
 *     property.
 * @return {*} The formatted property, or null if the property is not in the
 *     query string.
 */
function formatReceiptProperty(propertyName, format) {
  const parameters = new URLSearchParams(location.search);
  return parameters.has(propertyName) ? format(parameters.get(propertyName)) :
                                        null;
}

/** Converts a timestamp string into the equivalent date string. */
function getDateFromTimestamp(timestamp) {
  const time = parseInt(timestamp);

  // Only return the year, month, and day
  return new Date(time).toISOString().substring(0, 10);
}

/**
 * Capitalizes and adds spaces between the categories.
 * @param {string} categories A comma-separated list of categories
 * @return {string} The formatted list of categories.
 */
function formatCategories(categories) {
  return capitalizeFirstLetters(categories.replace(/,/gi, ', '));
}

/**
 * Converts a date to 'YYYY-MM-DD' format, corresponding to the value attribute
 * of the date input.
 * @param {Date} date The date to convert.
 * @return {string} The formatted date.
 */
function formatDate(date) {
  const day = String(date.getDate()).padStart(2, '0');
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const year = date.getFullYear();
  return `${year}-${month}-${day}`;
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

  document.body.style.cursor = 'wait';
  const saveButton = document.getElementById('submit-receipt');
  const homeButton = document.getElementById('return-home');
  saveButton.disabled = true;
  homeButton.disabled = true;

  const receipt = getReceiptFromForm();
  const editRequest = new URLSearchParams();

  editRequest.append('id', receipt.id);
  editRequest.append('date', receipt.date);
  editRequest.append('store', receipt.store);
  editRequest.append('price', receipt.price);

  createCategoryList(receipt.categories).forEach((category) => {
    editRequest.append('categories', category);
  });

  const response =
      await fetch(`/edit-receipt?${editRequest.toString()}`, {method: 'POST'});

  if (response.status !== 200) {
    const error = await response.text();
    document.body.style.cursor = 'default';
    alert(error);
    saveButton.disabled = false;
    homeButton.disabled = false;
    return;
  }

  const json = await response.json();
  const params = setUrlParameters(json);

  // Restore the cursor after the edit request has loaded.
  document.body.style.cursor = 'default';

  // Remove warning for unsaved changes.
  window.onbeforeunload = null;

  // Update query string with edited fields.
  window.location.replace(`/receipt-analysis.html?${params.toString()}`);
}

/**
 * Gets the receipt properties from the form data.
 * @return {object} The extracted receipt.
 */
function getReceiptFromForm() {
  const id = new URLSearchParams(location.search).get('id');
  const date = document.getElementById('date-input').valueAsNumber;
  const store = document.getElementById('store-input').value;
  const price =
      convertStringToNumber(document.getElementById('price-input').value);
  const categories = document.getElementById('categories-input').value;

  return {id, date, store, price, categories};
}

/** Converts the comma-separated categories string into a list of categories. */
function createCategoryList(categories) {
  return categories.split(',').map((category) => category.trim());
}

/**
 * Creates URL parameters using the properties of the receipt in the given JSON
 * response.
 * @param {object} json The JSON response from the edit servlet.
 * @return {URLSearchParams} The updated URL parameters containing the edited
 *     fields.
 */
function setUrlParameters(json) {
  const receipt = json.propertyMap;
  const params = new URLSearchParams();

  params.append('id', json.key.id);
  params.append('image-url', receipt.imageUrl.value);
  params.append('categories', receipt.categories);
  params.append('price', receipt.price);
  params.append('store', receipt.store);
  params.append('timestamp', receipt.timestamp);
  return params;
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

/**
 * Redirects the user to the home page when the "Return to Home" button is
 * clicked.
 */
function redirectHome() {
  if (isFormSaved()) {
    // Remove warning for unsaved changes.
    window.onbeforeunload = null;
  } else {
    // Warn user of losing unsaved changes.
    window.onbeforeunload = () => true;
  }

  if (!isReceiptComplete()) {
    alert('Please ensure that all fields are set and have been saved.');
    return;
  }

  window.location.href = '/';
}

/**
 * Checks if there are any unsaved changes.
 * @return {boolean} Whether the form data matches the stored receipt.
 */
function isFormSaved() {
  const savedReceipt = getReceiptFromQueryString();
  const formReceipt = getReceiptFromForm();

  return savedReceipt.date === getDateFromTimestamp(formReceipt.date) &&
      savedReceipt.storeName === formReceipt.store &&
      convertStringToNumber(savedReceipt.price) === formReceipt.price &&
      savedReceipt.categories === formReceipt.categories;
}

/**
 * Checks all receipt fields are saved.
 * @return {boolean} Whether all fields have been set.
 */
function isReceiptComplete() {
  const savedReceipt = getReceiptFromQueryString();

  return savedReceipt.date && savedReceipt.storeName && savedReceipt.price &&
      savedReceipt.categories;
}
