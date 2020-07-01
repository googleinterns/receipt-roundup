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

/** Fetch receipts from the server and add them to the DOM. */
async function searchReceipts() {
  const label = document.getElementById('search-input').value;
  const response = await fetch(`/search-receipts?label=${label}`);
  const receipts = await response.json();

  // Clear out old receipt display and populate with new queried receipts.
  const receiptsDisplayed = document.getElementById('receipt-list');
  receiptsDisplayed.innerHTML = '';

  receipts.forEach((receipt) => {
    receiptsDisplayed.innerHTML += createReceiptCardElement(receipt);
  });
}

/**
 * Returns an HTML string that represents a receipt card.
 * This card displays transaction date, store name, trasaction total,
 * categories, receipt photo, and view/edit/delete buttons.
 */
function createReceiptCardElement(receipt) {
  const categories = Array.from(receipt.categories);
  const formattedHTML = '<div class="col-md-6">' +
      '<div class="card mb-4 box-shadow">' +
      '<p class="card-text align-self-end">' + receipt.timestamp + '</p>' +
      '<p class="card-text">' + receipt.store + '</p>' +
      '<p class="card-text">Total: $' + receipt.price + '</p>' +
      '<div class="row">' +
      '<div class="col-4 d-flex justify-content-center">' +
      '<h4><span class="badge badge-pill badge-secondary">' + categories[0] +
      '</span></h4>' +
      '</div>' +
      '<div class="col-4 d-flex justify-content-center">' +
      '<h4><span class="badge badge-pill badge-secondary">' + categories[1] +
      '</span></h4>' +
      '</div>' +
      '<div class="col-4 d-flex justify-content-center">' +
      '<h4><span class="badge badge-pill badge-secondary">' + categories[2] +
      '</span></h4>' +
      '</div>' +
      '</div>' +
      '<img src=' + receipt.imageUrl +
      ' alt="Receipt image" class="img-fluid receipt-img" />' +
      '<div class="card-body">' +
      '<div class="d-flex justify-content-between align-items-center">' +
      '<div class="btn-group">' +
      '<button type="button" class="btn btn-sm btn-outline-secondary">' +
      'View</button' +
      '><button type="button" class="btn btn-sm btn-outline-secondary">' +
      'Edit</button' +
      '><button type="button" class="btn btn-sm btn-outline-secondary">' +
      'Delete</button>' +
      '</div>' +
      '</div>' +
      '</div>' +
      '</div>' +
      '</div>';
  return formattedHTML;
}

/* Date range picker handler:
 * https://www.daterangepicker.com/
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
