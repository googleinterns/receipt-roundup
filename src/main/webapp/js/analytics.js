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

/** Sets callbacks for Google Charts instance. */
google.charts.load('current', {'packages': ['corechart']});
google.charts.setOnLoadCallback(computeChartAnalytics);

let storeData;
let categoryData;

/** Computes and populates DataTable with analytics for both charts. */
async function computeChartAnalytics() {
  const response = await fetch('/compute-analytics');
  const analytics = await response.json();

  handleStoreChart(analytics);
  handleCategoryChart(analytics);
}

/**
 * Populates DataTable with store data and passes that to draw method.
 * @param {Array} analytics First entry is store data, second is category.
 */
function handleStoreChart(analytics) {
  storeData = new google.visualization.DataTable();

  storeData.addColumn('string', 'Store');
  storeData.addColumn('number', 'Total');

  for (const [store, total] of Object.entries(analytics.storeAnalytics)) {
    storeData.addRow([capitalizeFirstLetters(store), total]);
  }

  drawStoreChart(storeData);
}

/**
 * Populates DataTable with category data and passes that to draw method.
 * @param {JSON} analytics
 */
function handleCategoryChart(analytics) {
  categoryData = new google.visualization.DataTable();

  categoryData.addColumn('string', 'Category');
  categoryData.addColumn('number', 'Total');

  for (const [category, total] of Object.entries(analytics.categoryAnalytics)) {
    categoryData.addRow([capitalizeFirstLetters(category), total]);
  }

  drawCategoryChart(categoryData);
}

/**
 * Intializes and draws store chart onto DOM.
 * @param {DataTable} data Table with stores and their totals.
 */
function drawStoreChart(data) {
  // Get parent div dimensions to make chart fit the appropriate space.
  const chartWidth =
      document.getElementById('store-chart').getBoundingClientRect().width;
  const chartHeight =
      document.getElementById('store-chart').getBoundingClientRect().height;

  const options = {
    title: 'Spending habits by store',
    pieHole: 0.3,
    width: chartWidth,
    legend: {alignment: 'center'},
    chartArea: {width: chartWidth, height: chartHeight * 0.6},
    sliceVisibilityThreshold: .03,
  };

  data.sort({column: 1, desc: true});
  const chart =
      new google.visualization.PieChart(document.getElementById('store-chart'));
  chart.draw(data, options);
}

/**
 * Intializes and draws category chart onto DOM.
 * @param {DataTable} data Table with categories and their totals.
 */
function drawCategoryChart(data) {
  // Get parent div dimensions to make chart fit the appropriate space.
  const chartWidth =
      document.getElementById('category-chart').getBoundingClientRect().width;

  const options = {
    title: 'Spending habits by category (top 10)',
    vAxis: {title: 'Total ($)'},
    legend: {position: 'none'},
    width: chartWidth,
    hAxis: {
      title: 'Category',
      showTextEvery: 1,
      slantedText: true,
      slantedTextAngle: 60,
      viewWindow: {max: 10},
    },
    chartArea: {width: chartWidth * 0.75},
  };

  // Sort by price so we can easily find the top 10 categories.
  data.sort({column: 1, desc: true});

  const chart = new google.visualization.ColumnChart(
      document.getElementById('category-chart'));
  chart.draw(data, options);
}

/** Handles sizing of chart to be responsive on different screen sizes. */
$(window).resize(function() {
  drawStoreChart(storeData);
  drawCategoryChart(categoryData);
});
