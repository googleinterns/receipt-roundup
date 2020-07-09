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

import static org.mockito.Mockito.when;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.sps.servlets.DeleteReceiptServlet;
import java.io.IOException;
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
  
  // Uses local Datastore.
  private final LocalServiceTestHelper helper =
      new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig())
          .setEnvIsAdmin(true)
          .setEnvIsLoggedIn(true);

  @Mock private DatastoreService datastore;
  @Mock private DeleteReceiptServlet servlet;
  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;

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

    when(request.getParameter("id")).thenReturn(String.valueOf(id));

    // Check that receipt is added
    Query query = new Query("Receipt");
    PreparedQuery results = datastore.prepare(query);
    Assert.assertEquals(results.countEntities(FetchOptions.Builder.withDefaults()), 1);

    // Perform doPost - this should delete the receipt.
    servlet.doPost(request, response);

    // Make sure receipt is deleted.
    results = datastore.prepare(query);
    Assert.assertEquals(results.countEntities(FetchOptions.Builder.withDefaults()), 0);
  }
}