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

/** Clears out receipts display including old receipts and error messages. */
function clearExistingDisplay() {
  document.getElementById('receipts-display').innerHTML = '';
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
 * Creates error message based on existing HTML template.
 * @param {string} label User-entered label.
 */
function createErrorMessageElement(label) {
  // Clone error message from template.
  const errorMessageClone =
      document.querySelector('#error-message-template').content.cloneNode(true);

  // Fill in template fields with correct information.
  errorMessageClone.querySelector('h3').innerText =
      `Sorry, no results found for "${label}". ` +
      `Please try your search again or try a different query.`;

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
  receiptCardClone.querySelector('#timestamp').innerText = receipt.timestamp;
  receiptCardClone.querySelector('#store-name').innerText = receipt.store;
  receiptCardClone.querySelector('#total').innerText =
      'Total: $' + receipt.price;

  const categories = Array.from(receipt.categories);
  for (let i = 0; i < categories.length && i < 3; i++) {
    receiptCardClone.querySelector('#c' + i).innerText = categories[i];
  }

  receiptCardClone.querySelector('img').src = receipt.imageUrl;

  // Attach receipt card clone to parent div.
  document.getElementById('receipts-display').appendChild(receiptCardClone);

  // Attach listener to trigger the deletion of this receipt.
  attachDeleteButtonEventListener(receipt);
}

/**
 * Attaches event listener to delete button.
 * @param {Receipt} receipt A Receipt datastore object.
 */
function attachDeleteButtonEventListener(receipt) {
  // Get all delete buttons; most recently attached will be at the end.
  const allDeleteButtons =
      document.querySelector('#receipts-display').querySelectorAll('#delete');
  const mostRecentDeleteButton = allDeleteButtons[allDeleteButtons.length - 1];

  mostRecentDeleteButton.addEventListener('click', () => {
    // Display a pop-up to the user confirming the deletion of the receipt.
    const selection = confirm(
        'Are you sure you want to delete this receipt? This cannot be undone.');
    if (selection) {
      deleteReceipt(receipt);
      // Locate the entire receipt card div and remove from the DOM.
      mostRecentDeleteButton.closest('.col-md-6').remove();
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
