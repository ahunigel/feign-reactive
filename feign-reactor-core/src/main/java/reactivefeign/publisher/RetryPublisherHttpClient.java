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
package reactivefeign.publisher;

import feign.MethodMetadata;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactivefeign.client.ReactiveHttpRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Type;
import java.util.function.Function;

import static reactivefeign.utils.FeignUtils.methodTag;

/**
 * Wraps {@link PublisherHttpClient} with retry logic provided by retryFunction
 *
 * @author Sergii Karpenko
 */
public class RetryPublisherHttpClient implements PublisherHttpClient {

  private static final Logger logger = LoggerFactory.getLogger(RetryPublisherHttpClient.class);

  private final String feignMethodTag;
  private final PublisherHttpClient publisherClient;
  private final Function<Flux<Throwable>, Flux<?>> retryFunction;

  public static PublisherHttpClient retry(
          PublisherHttpClient publisherClient,
          MethodMetadata methodMetadata,
          Function<Flux<Throwable>, Flux<Throwable>> retryFunction) {
    return new RetryPublisherHttpClient(publisherClient, methodMetadata, retryFunction);
  }

  private RetryPublisherHttpClient(PublisherHttpClient publisherClient,
                                   MethodMetadata methodMetadata,
                                   Function<Flux<Throwable>, Flux<Throwable>> retryFunction) {
    this.publisherClient = publisherClient;
    this.feignMethodTag = methodTag(methodMetadata);
    this.retryFunction = wrapWithLog(retryFunction, feignMethodTag);
  }

  @Override
  public Publisher<Object> executeRequest(ReactiveHttpRequest request, Type publisherType) {
    Publisher<Object> response = publisherClient.executeRequest(request, publisherType);
    if (publisherType == Mono.class) {
      return ((Mono<Object>) response).retryWhen(retryFunction).onErrorMap(outOfRetries());
    } else {
      return ((Flux<Object>) response).retryWhen(retryFunction).onErrorMap(outOfRetries());
    }
  }

  private Function<Throwable, Throwable> outOfRetries() {
    return throwable -> {
      logger.debug("[{}]---> USED ALL RETRIES", feignMethodTag, throwable);
      return new OutOfRetriesException(throwable, feignMethodTag);
    };
  }

  private static Function<Flux<Throwable>, Flux<?>> wrapWithLog(
          Function<Flux<Throwable>, Flux<Throwable>> retryFunction,
          String feignMethodTag) {
    return throwableFlux -> retryFunction.apply(throwableFlux)
            .doOnNext(throwable -> {
              if (logger.isDebugEnabled()) {
                logger.debug("[{}]---> RETRYING on error", feignMethodTag, throwable);
              }
            });
  }

  public static class OutOfRetriesException extends Exception {
    OutOfRetriesException(Throwable cause, String feignMethodTag) {
      super("All retries used for: " + feignMethodTag, cause);
    }
  }
}
