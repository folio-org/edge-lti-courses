package org.folio.edge.ltiCourses.utils;

import static org.folio.edge.core.Constants.APPLICATION_JSON;
import static org.folio.edge.core.Constants.HEADER_API_KEY;
import static org.folio.edge.core.Constants.JSON_OR_TEXT;

import java.util.Map.Entry;

import org.apache.log4j.Logger;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;

public class LtiPlatformClient {
  private static final Logger logger = Logger.getLogger(LtiPlatformClient.class);

  public final HttpClient client;
  public final Vertx vertx;
  public final long reqTimeout;

  protected final MultiMap defaultHeaders = MultiMap.caseInsensitiveMultiMap();

  public LtiPlatformClient(Vertx vertx, long timeout) {
    this.vertx = vertx;
    this.reqTimeout = timeout;
    this.client = vertx
      .createHttpClient(new HttpClientOptions()
        .setKeepAlive(false)
        .setTryUseCompression(true)
      );
  }

  protected void initDefaultHeaders() {
    defaultHeaders.add(HttpHeaders.ACCEPT.toString(), JSON_OR_TEXT);
    defaultHeaders.add(HttpHeaders.CONTENT_TYPE.toString(), APPLICATION_JSON);
  }

  public void post(String url, String payload, Handler<HttpClientResponse> responseHandler,
      Handler<Throwable> exceptionHandler) {
    post(url, payload, null, responseHandler, exceptionHandler);
  }

  public void post(String url, String payload, MultiMap headers,
      Handler<HttpClientResponse> responseHandler, Handler<Throwable> exceptionHandler) {

    final HttpClientRequest request = client.postAbs(url);

    if (headers != null) {
      request.headers().setAll(combineHeadersWithDefaults(headers));
    } else {
      request.headers().setAll(defaultHeaders);
    }

    if (logger.isTraceEnabled()) {
      logger.trace(String.format("POST %s Request: %s", url, payload));
    }

    request.handler(responseHandler)
      .exceptionHandler(exceptionHandler)
      .setTimeout(reqTimeout);

    if (payload != null) {
      request.end(payload);
    } else {
      request.end();
    }
  }

  public void delete(String url, Handler<HttpClientResponse> responseHandler,
      Handler<Throwable> exceptionHandler) {
    delete(url, null, responseHandler, exceptionHandler);
  }

  public void delete(String url, MultiMap headers,
      Handler<HttpClientResponse> responseHandler, Handler<Throwable> exceptionHandler) {

    final HttpClientRequest request = client.deleteAbs(url);

    if (headers != null) {
      request.headers().setAll(combineHeadersWithDefaults(headers));
    } else {
      request.headers().setAll(defaultHeaders);
    }

    logger.info(String.format("DELETE %s", url));

    request.handler(responseHandler)
      .exceptionHandler(exceptionHandler)
      .setTimeout(reqTimeout)
      .end();
  }

  public void put(String url, Handler<HttpClientResponse> responseHandler,
      Handler<Throwable> exceptionHandler) {
    put(url, null, responseHandler, exceptionHandler);
  }

  public void put(String url, MultiMap headers,
      Handler<HttpClientResponse> responseHandler, Handler<Throwable> exceptionHandler) {

    final HttpClientRequest request = client.putAbs(url);

    if (headers != null) {
      request.headers().setAll(combineHeadersWithDefaults(headers));
    } else {
      request.headers().setAll(defaultHeaders);
    }

    logger.info(String.format("PUT %s", url));

    request.handler(responseHandler)
      .exceptionHandler(exceptionHandler)
      .setTimeout(reqTimeout)
      .end();
  }

  public void get(String url, Handler<HttpClientResponse> responseHandler,
      Handler<Throwable> exceptionHandler) {
    get(url, null, responseHandler, exceptionHandler);
  }

  public void get(String url, MultiMap headers,
      Handler<HttpClientResponse> responseHandler, Handler<Throwable> exceptionHandler) {

    final HttpClientRequest request = client.getAbs(url);

    if (headers != null) {
      request.headers().setAll(combineHeadersWithDefaults(headers));
    } else {
      request.headers().setAll(defaultHeaders);
    }

    logger.info(String.format("GET %s", url));

    request.handler(responseHandler)
      .exceptionHandler(exceptionHandler)
      .setTimeout(reqTimeout)
      .end();
  }

  protected MultiMap combineHeadersWithDefaults(MultiMap headers) {
    MultiMap combined = null;

    if (headers != null) {
      headers.remove(HEADER_API_KEY);
      if (headers.size() > 0) {
        combined = MultiMap.caseInsensitiveMultiMap();
        combined.addAll(headers);
        for (Entry<String, String> entry : defaultHeaders.entries()) {
          if (!combined.contains(entry.getKey())) {
            combined.set(entry.getKey(), entry.getValue());
          }
        }
      }
    }
    return combined != null ? combined : defaultHeaders;
  }

  public void close() {
    client.close();
  }
}