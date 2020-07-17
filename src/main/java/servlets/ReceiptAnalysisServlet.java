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
import com.google.sps.servlets.ReceiptAnalysis.ReceiptAnalysisException;
import java.io.IOException;
import java.net.URL;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet with a GET handler that serves the text of the specified image using the Cloud Vision
 * API.
 */
@WebServlet("/receipt-analysis")
public class ReceiptAnalysisServlet extends HttpServlet {
  /** Serves the text of the image at the requested URL. */
  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    String url = request.getParameter("url");
    AnalysisResults results = null;

    // Ignore requests that don't specify a URL
    if (url == null) {
      return;
    }

    try {
      results = ReceiptAnalysis.serveImageText(new URL(url));
    } catch (ReceiptAnalysisException e) {
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      response.getWriter().println(e.toString());
      return;
    }

    Gson gson = new Gson();
    response.setContentType("application/json;");
    response.getWriter().println(gson.toJson(results));
  }
}