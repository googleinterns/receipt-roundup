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

/** Sets callbacks for Google Charts instance. */
google.charts.load('current', {'packages': ['corechart']});
google.charts.setOnLoadCallback(computeStoreChartsAnalytics);

let storeData;

/** Computes and populates DataTable with analytics for stores chart. */
async function computeStoreChartsAnalytics() {
  const response = await fetch('/compute-analytics');
  const analytics = await response.json();

  storeData = new google.visualization.DataTable();

  storeData.addColumn('string', 'Store');
  storeData.addColumn('number', 'Total');

  for (const [store, total] of Object.entries(analytics)) {
    storeData.addRow([store, total]);
  }

  drawStoresChart(storeData);
}

/**
 * Intializes and draws stores chart onto DOM.
 * @param {DataTable} data Table with stores and their totals.
 */
function drawStoresChart(data) {
  const chartWidth =
      document.getElementById('stores-chart').getBoundingClientRect().width;
  const chartHeight =
      document.getElementById('stores-chart').getBoundingClientRect().height;

  const options = {
    title: 'Spending habits by store',
    pieHole: 0.3,
    width: chartWidth,
    legend: {alignment: 'center'},
    chartArea: {width: chartWidth, height: chartHeight * 0.6},
  };

  const chart = new google.visualization.PieChart(
      document.getElementById('stores-chart'));
  chart.draw(data, options);
}

/** Handles sizing of chart to be responsive on different screen sizes. */
$(window).resize(function() {
  drawStoresChart(storeData);
});
