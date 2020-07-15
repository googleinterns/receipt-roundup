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
  const params = new URLSearchParams();
  params.append('categories', document.getElementById('category-input').value);
  params.append('dateRange', document.getElementById('date-range-input').textContent);
  params.append('store', document.getElementById('store-name-input').value);
  params.append('min', document.getElementById('min-price-input').value);
  params.append('max', document.getElementById('max-price-input').value)
  const dateTimeFormat = new Intl.DateTimeFormat();
  params.append('timeZoneId', dateTimeFormat.resolvedOptions().timeZone)

  const response = await fetch('/search-receipts?' + params.toString());
  const receipts = await response.json();

  clearExistingDisplay();
  displayReceipts(receipts);
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

/**
 * Creates error message based on existing HTML template.
 */
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
 * @param {Receipt} receipt A Receipt object.
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
      {start: [20, 80], connect: true, step: 1, range: {min: [0], max: [250]}});

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

        const enter = 13;
        const up = 38;
        const down = 40;

        switch (event.which) {
          case enter:
            setSliderHandle(handle, this.value);
            break;
          case up:
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
          case down:
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
