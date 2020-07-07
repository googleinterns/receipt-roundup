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


/** Fetches receipts from the server and adds them to the DOM. */
async function searchReceipts() {
  const label = document.getElementById('search-input').value;
  const response = await fetch(`/search-receipts?label=${label}`);
  const receipts = await response.json();

  clearExistingDisplay();
  displayReceipts(label, receipts);
}

/**
 * Clears out receipts display including old receipts and error messages.
 */
function clearExistingDisplay() {
  const elements = document.querySelectorAll(
      '.col-md-6, .col-md-12.text-center.error-message');
  elements.forEach(function(element) {
    element.parentNode.removeChild(element);
  });
}

/**
 * Populates receipt display with newly queried receipts.
 * @param {string} label User-entered label.
 * @param {JSON Object} receipts Receipts returned from search query.
 */
function displayReceipts(label, receipts) {
  // If no receipts returned, display an error message. Else, display receipts.
  if (Object.keys(receipts).length == 0) {
    createErrorMessageElement(label);
  } else {
    receipts.forEach((receipt) => {
      createReceiptCardElement(receipt);
    });
  }
}

/**
 * Creates receipt card based on existing HTML template.
 * This card displays transaction date, store name, trasaction total,
 * categories, receipt photo, and view/edit/delete buttons.
 * @param {Receipt} receipt A Receipt object.
 */
function createReceiptCardElement(receipt) {
  const categories = Array.from(receipt.categories);

  // Clone receipt card from template.
  const receiptCardClone =
      document.querySelector('#receipt-card-template').content.cloneNode(true);

  // Fill in template fields with correct information.
  receiptCardClone.querySelector('#timestamp').innerText = receipt.timestamp;
  receiptCardClone.querySelector('#store-name').innerText = receipt.store;
  receiptCardClone.querySelector('#total').innerText =
      'Total: $' + receipt.price;
  receiptCardClone.querySelector('#c1').innerText = categories[0];
  receiptCardClone.querySelector('#c2').innerText = categories[1];
  receiptCardClone.querySelector('#c3').innerText = categories[2];
  receiptCardClone.querySelector('img').src = receipt.imageUrl;

  // Attach receipt card clone to parent div.
  document.getElementById('receipts-display').appendChild(receiptCardClone);
}

/**
 * Creates and displays error message when no matching receipts were found.
 * @param {string} label User-entered label.
 */
function createErrorMessageElement(label) {
  const div = document.createElement('div');
  div.setAttribute('class', 'col-md-12 text-center error-message');
  const h3 = document.createElement('h3');
  h3.textContent = 'Sorry, no results found for "' + label +
      '". Please try your search again or try a different query.';
  div.appendChild(h3);
  document.getElementById('receipts-display').appendChild(div);
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
          },

          cb);
  cb(start, end);
});
