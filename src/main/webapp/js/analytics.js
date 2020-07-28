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

/** Sets callbacks for chart instance. */
google.charts.load('current', {'packages': ['corechart']});
google.charts.setOnLoadCallback(drawStoresChart);

/** Intializes and draws map onto DOM. */
function drawStoresChart() {
  const data = new google.visualization.DataTable();
  // TODO: Add real data here.
  data.addColumn('string', 'Store');
  data.addColumn('number', 'Total');
  data.addRows([
    [capitalizeFirstLetters('walmart'), 10],
    [capitalizeFirstLetters('target'), 5],
    [capitalizeFirstLetters('main Street Restaurant'), 15],
    [capitalizeFirstLetters('burger King'), 7],
    [capitalizeFirstLetters('gap'), 13],
    [capitalizeFirstLetters('walgreens'), 4],
    [capitalizeFirstLetters('jewel-Osco'), 11],
    [capitalizeFirstLetters('shell'), 2],
    [capitalizeFirstLetters('starbucks'), 12],
    [capitalizeFirstLetters('h&M'), 20],
  ]);

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
  drawStoresChart();
});
