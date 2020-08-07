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

/* global capitalizeFirstLetters, loadPage */

let encodedCursor;
let isPageLoad;

/** Checks if user is logged in then loads the logout button and receipts. */
function load() {
  loadPage(getAllReceipts, loadLogoutButton);  // From js/common.js
}

/**
 * Adds a URL to the logout button and displays user's email.
 * @param {object} account
 */
async function loadLogoutButton(account) {
  document.getElementById('logout-button').href = account.logoutUrl;

  document.getElementById('user-display').innerHTML = `${account.email}`;
}

/**
 * Fetches all receipts from the server and adds them to the DOM.
 * This will only be called on page load.
 */
function getAllReceipts() {
  document.body.style.cursor = 'wait';

  const params = new URLSearchParams();
  isPageLoad = true;
  params.append('isPageLoad', isPageLoad);

  searchReceipts(params);
}

/** Fetches matching receipts from the server and adds them to the DOM. */
function getMatchingReceipts() {
  document.body.style.cursor = 'wait';

  const params = new URLSearchParams();
  isPageLoad = false;
  params.append('isPageLoad', isPageLoad);
  appendQueryInformation(params);

  searchReceipts(params);
}

/**
Fetches next receipts page from the server and adds itmvn encodedCursor);
  appendQueryInformation(params);

  searchReceipts(params);
  scrollToTop();
}

/** Fetches previous receipts page from the server and adds it to the DOM.
*/
function getPreviousPageOfReceipts() {
  document.body.style.cursor = 'wait';

  const params = new URLSearchParams();
  params.append('isPageLoad', isPageLoad);
  params.append('getNextPage', 'false');
  params.append('getPreviousPage', 'true');
  params.append('encodedCursor', encodedCursor);
  appendQueryInformation(params);

  searchReceipts(params);
  scrollToTop();
}

/** Attaches search query information to params to be sent to the server. */
function appendQueryInformation(params) {
  params.append('category', document.getElementById('category-input').value);
  params.append(
      'dateRange', document.getElementById('date-range-input').textContent);
  params.append('store', document.getElementById('store-name-input').value);
  params.append('min', document.getElementById('min-price-input').value);
  params.append('max', document.getElementById('max-price-input').value);
  const dateTimeFormat = new Intl.DateTimeFormat();
  params.append('timeZoneId', dateTimeFormat.resolvedOptions().timeZone);
}

/**
 * Searches receipts based on user query.
 * @param {URLSearchParams} params To be used in query string.
 */
async function searchReceipts(params) {
  const response = await fetch(`/search-receipts?${params.toString()}`);
  const info = await response.json();

  const receipts = info.matchingReceipts;
  encodedCursor = info.encodedCursor;

  clearExistingDisplay();
  displayReceipts(receipts);
  document.body.style.cursor = 'default';
}

/** Scrolls to the top of the page. */
function scrollToTop() {
  document.body.scrollTop = 0;             // For Safari
  document.documentElement.scrollTop = 0;  // For Chrome, Firefox, IE and Opera
}

/** Clears out receipts display including old receipts and error messages. */
function clearExistingDisplay() {
  document.getElementById('receipts-display').innerHTML = '';
}

/**
 * Populates receipt display with newly queried receipts.
 * @param {JSON Object} receipts Receipts returned from search query.
 */
function displayReceipts(receipts) {
  // If no receipts returned, display an error message. Else, display receipts.
  if (Object.keys(receipts).length == 0) {
    createErrorMessageElement();
  } else {
    receipts.forEach((receipt) => {
      createReceiptCardElement(receipt);
    });
  }
}

/** Creates error message based on existing HTML template. */
function createErrorMessageElement() {
  // Clone error message from template.
  const errorMessageClone =
      document.querySelector('#error-message-template').content.cloneNode(true);

  // Fill in template fields with correct information.
  errorMessageClone.querySelector('h3').innerText =
      'Sorry, no results found. Please try again or refine your search.';

  // Attach error message clone to parent div.
  document.getElementById('receipts-display').appendChild(errorMessageClone);
}

/**
 * Creates receipt card based on existing HTML template.
 * This card displays transaction date, store name, trasaction total,
 * categories, receipt photo, and view/edit/delete buttons.
 * @param {Receipt} receipt A Receipt datastore object.
 */
function createReceiptCardElement(receipt) {
  // Clone receipt card from template.
  const receiptCardClone =
      document.querySelector('#receipt-card-template').content.cloneNode(true);

  // Fill in template fields with correct information.
  const date = new Date(receipt.timestamp);
  receiptCardClone.querySelector('#timestamp').innerText =
      date.toUTCString().substring(0, 16);
  receiptCardClone.querySelector('#store-name').innerText =
      capitalizeFirstLetters(receipt.store);
  receiptCardClone.querySelector('#total').innerText =
      `Total: $${receipt.price.toFixed(2)}`;

  const categoriesContainer =
      receiptCardClone.getElementById('categories-container');

  const categories = Array.from(receipt.categories);
  for (let i = 0; i < categories.length && i < 3; i++) {
    categoriesContainer.appendChild(createCategoryElement(categories[i]));
  }

  receiptCardClone.querySelector('img').src = receipt.imageUrl;
  receiptCardClone.querySelector('.col-md-6').id = receipt.id;

  // Attach listeners to trigger the deletion and editing of this receipt.
  attachDeleteButtonEventListener(receipt, receiptCardClone);
  attachEditButtonEventListener(receipt, receiptCardClone);

  // Attach receipt card clone to parent div.
  document.getElementById('receipts-display').appendChild(receiptCardClone);
}

/** Creates the div element for a category along with its children. */
function createCategoryElement(category) {
  category = capitalizeFirstLetters(category);
  const categoryElement =
      document.querySelector('#category-template').content.cloneNode(true);
  categoryElement.querySelector('#category-name').innerText = category;

  return categoryElement;
}

/**
 * Attaches event listener to delete button.
 * @param {Receipt} receipt A Receipt datastore object.
 * @param {receiptCardClone} DocumentFragment Receipt card wrapper.
 */
function attachDeleteButtonEventListener(receipt, receiptCardClone) {
  receiptCardClone.querySelector('#delete').addEventListener('click', () => {
    // Display a pop-up to the user confirming the deletion of the receipt.
    const selection = confirm(
        'Are you sure you want to delete this receipt? This cannot be undone.');
    if (selection) {
      deleteReceipt(receipt);
      document.getElementById(receipt.id).remove();
    }
  });
}

/** Tells the server to delete the receipt. */
async function deleteReceipt(receipt) {
  const params = new URLSearchParams();
  params.append('id', receipt.id);
  await fetch('/delete-receipt', {method: 'POST', body: params});
}

/**
 * Attaches event listener to edit button.
 * @param {Receipt} receipt A Receipt datastore object.
 * @param {receiptCardClone} DocumentFragment Receipt card wrapper.
 */
function attachEditButtonEventListener(receipt, receiptCardClone) {
  receiptCardClone.querySelector('#edit').addEventListener('click', () => {
    const params = new URLSearchParams();
    params.append('show-edit-text', true);
    params.append('id', receipt.id);
    params.append('categories', receipt.categories);
    params.append('image-url', receipt.imageUrl);
    params.append('price', receipt.price);
    params.append('store', receipt.store);
    params.append('timestamp', receipt.timestamp);

    // Redirect to the receipt analysis page.
    window.location.href = `/receipt-analysis.html?${params.toString()}`;
  });
}

/**
 * Date range picker handler:
 * https://www.daterangepicker.com/.
 */
$(function() {
  const start = moment().subtract(29, 'days');
  const end = moment();

  function cb(start, end) {
    $('#reportrange span')
        .html(
            start.format('MMMM D, YYYY') + ' - ' + end.format('MMMM D, YYYY'));
  }

  $('#reportrange')
      .daterangepicker(
          {
            startDate: start,
            endDate: end,
            ranges: {
              'Today': [moment(), moment()],
              'Yesterday':
                  [moment().subtract(1, 'days'), moment().subtract(1, 'days')],
              'Last 7 Days': [moment().subtract(6, 'days'), moment()],
              'Last 30 Days': [moment().subtract(29, 'days'), moment()],
              'This Month':
                  [moment().startOf('month'), moment().endOf('month')],
              'Last Month': [
                moment().subtract(1, 'month').startOf('month'),
                moment().subtract(1, 'month').endOf('month'),
              ],
            },
            showDropdowns: true,
            linkedCalendars: false,
            applyButtonClasses: 'btn btn-info',
          },

          cb);
  cb(start, end);
});

/**
 * Price range slider setup:
 * https://refreshless.com/nouislider/examples/#section-steps-api.
 */
$(document).ready(function() {
  const keypressSlider = document.querySelector('.slider-keypress');
  const input0 = document.querySelector('.input-with-keypress-0');
  const input1 = document.querySelector('.input-with-keypress-1');
  const inputs = [input0, input1];

  noUiSlider.create(
      keypressSlider,
      {start: [0, 80], connect: true, step: 1, range: {min: [0], max: [250]}});

  keypressSlider.noUiSlider.on('update', function(values, handle) {
    inputs[handle].value = values[handle];

    /* Begins listening to keypress on the input. */
    function setSliderHandle(which, value) {
      const handle = [null, null];
      handle[which] = value;
      keypressSlider.noUiSlider.set(handle);
    }

    // Listen to keydown events on the input field.
    inputs.forEach(function(input, handle) {
      input.addEventListener('change', function() {
        setSliderHandle(handle, this.value);
      });

      input.addEventListener('keydown', function(event) {
        const values = keypressSlider.noUiSlider.get();
        const value = Number(values[handle]);
        const steps = keypressSlider.noUiSlider.steps();
        const step = steps[handle];
        let position;

        const ENTER = 13;
        const UP = 38;
        const DOWN = 40;

        switch (event.which) {
          case ENTER:
            setSliderHandle(handle, this.value);
            break;
          case UP:
            // Get step to go increase slider value (up).
            position = step[1];

            // false = no step is set.
            if (position === false) {
              position = 1;
            }

            // null = edge of slider.
            if (position !== null) {
              setSliderHandle(handle, value + position);
            }
            break;
          case DOWN:
            position = step[0];

            if (position === false) {
              position = 1;
            }

            if (position !== null) {
              setSliderHandle(handle, value - position);
            }
            break;
        }
      });
    });
  });
});
