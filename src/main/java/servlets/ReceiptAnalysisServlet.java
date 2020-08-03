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

package com.google.sps.servlets;

import com.google.gson.Gson;
import com.google.sps.data.AnalysisResults;
import java.io.IOException;
import java.net.URL;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet with a GET handler that serves the text of a specified image using the Cloud Vision API,
 * as well as some categories the text falls into using the Cloud Natural Language API.
 */
@WebServlet("/receipt-analysis")
public class ReceiptAnalysisServlet extends HttpServlet {
  /** Serves the text and categorization of the image at the requested URL. */
  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    String url = request.getParameter("url");
    AnalysisResults results = null;

    // Ignore requests that don't specify a URL
    if (url == null) {
      return;
    }

    results = ReceiptAnalysis.analyzeImageAt(new URL(url));

    Gson gson = new Gson();
    response.setContentType("application/json;");
    response.getWriter().println(gson.toJson(results));
  }
}