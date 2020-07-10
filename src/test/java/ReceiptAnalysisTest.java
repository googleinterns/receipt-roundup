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

import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

import com.google.cloud.vision.v1.AnnotateImageRequest;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.Image;
import com.google.protobuf.ByteString;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(
    {AnnotateImageRequest.class, ByteString.class, Feature.class, Image.class, URL.class})
public final class ReceiptAnalysisTest {
  private static final ByteString IMAGE_BYTES = ByteString.copyFromUtf8("byte string");

  @Mock private AnnotateImageRequest request;
  @Mock private Feature feature;
  @Mock private Image image;

  @Mock(answer = Answers.RETURNS_SELF) private AnnotateImageRequest.Builder requestBuilder;
  @Mock(answer = Answers.RETURNS_SELF) private Feature.Builder featureBuilder;
  @Mock(answer = Answers.RETURNS_SELF) private Image.Builder imageBuilder;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void serveImageTextUrlReturnsAnalysisResults() throws IOException {
    URL url = mock(URL.class);
    InputStream inputStream = new ByteArrayInputStream(IMAGE_BYTES.toByteArray());
    when(url.openStream()).thenReturn(inputStream);

    mockStatic(ByteString.class);
    when(ByteString.readFrom(inputStream)).thenReturn(IMAGE_BYTES);

    when(imageBuilder.build()).thenReturn(image);
    mockStatic(Image.class);
    when(Image.newBuilder()).thenReturn(imageBuilder);

    when(featureBuilder.build()).thenReturn(feature);
    mockStatic(Feature.class);
    when(Feature.newBuilder()).thenReturn(featureBuilder);

    when(requestBuilder.build()).thenReturn(request);
    mockStatic(AnnotateImageRequest.class);
    when(AnnotateImageRequest.newBuilder()).thenReturn(requestBuilder);
  }
}
