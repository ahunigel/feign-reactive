/**
 * Copyright 2018 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package reactivefeign.publisher.retry;

import feign.MethodMetadata;
import org.reactivestreams.Publisher;
import reactivefeign.client.ReactiveHttpRequest;
import reactivefeign.publisher.PublisherHttpClient;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

/**
 * Wraps {@link PublisherHttpClient} with retry logic provided by retryFunction
 *
 * @author Sergii Karpenko
 */
public class FluxRetryPublisherHttpClient extends RetryPublisherHttpClient {

  public FluxRetryPublisherHttpClient(
          PublisherHttpClient publisherClient,
          MethodMetadata methodMetadata,
          Retry retry) {
    super(publisherClient, methodMetadata, retry);
  }

  @Override
  public Publisher<Object> executeRequest(ReactiveHttpRequest request) {
    return Flux.from(publisherClient.executeRequest(request))
            .retryWhen(getRetry(request));
  }
}
