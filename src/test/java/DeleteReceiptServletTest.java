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

package com.google.sps;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.sps.servlets.DeleteReceiptServlet;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;

@PowerMockIgnore("jdk.internal.reflect.*")
@RunWith(PowerMockRunner.class)
public final class DeleteReceiptServletTest {
  private static final String INVALID_ID_MESSAGE =
      "Invalid ID: Receipt unable to be deleted at this time, please try again.";

  // Local Datastore
  private final LocalServiceTestHelper helper =
      new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig())
          .setEnvIsAdmin(true)
          .setEnvIsLoggedIn(true);

  @Mock private DeleteReceiptServlet servlet;
  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;

  private DatastoreService datastore;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    helper.setUp();
    datastore = DatastoreServiceFactory.getDatastoreService();

    servlet = new DeleteReceiptServlet(datastore);
  }

  @After
  public void tearDown() {
    helper.tearDown();
  }

  @Test
  public void doPostDeletesReceiptFromDatastore() throws IOException {
    // Add mock receipt to datastore.
    long id = TestUtils.addReceiptToMockDatastore(datastore);

    // Make sure receipt is added by checking if there is one entity returned.
    Query query = new Query("Receipt");
    PreparedQuery results = datastore.prepare(query);
    Assert.assertEquals(1, results.countEntities(FetchOptions.Builder.withDefaults()));

    // Perform doPost - this should delete the receipt.
    when(request.getParameter("id")).thenReturn(String.valueOf(id));
    servlet.doPost(request, response);

    // Make sure receipt is deleted by checking if there are no entities returned.
    Key desiredKey = KeyFactory.createKey("Receipt", id);
    Filter matchingKeys = new FilterPredicate("__key__", FilterOperator.EQUAL, desiredKey);
    query.setFilter(matchingKeys);
    results = datastore.prepare(query);
    Assert.assertEquals(0, results.countEntities(FetchOptions.Builder.withDefaults()));
  }

  @Test
  public void checkNumberFormatExceptionIsThrown() throws IOException {
    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(writer);

    // Add mock receipt to datastore.
    long id = TestUtils.addReceiptToMockDatastore(datastore);

    // Pass in an String id instead of a long.
    when(request.getParameter("id")).thenReturn(String.valueOf(id) + "this should fail");
    servlet.doPost(request, response);
    writer.flush();

    Assert.assertTrue(stringWriter.toString().contains(INVALID_ID_MESSAGE));
    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);

    // Check that our original receipt is still in datastore.
    Key desiredKey = KeyFactory.createKey("Receipt", id);
    Query query = new Query("Receipt");
    Filter matchingKeys = new FilterPredicate("__key__", FilterOperator.EQUAL, desiredKey);
    query.setFilter(matchingKeys);
    PreparedQuery results = datastore.prepare(query);
    Assert.assertEquals(1, results.countEntities(FetchOptions.Builder.withDefaults()));
  }
}