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

  public void get(String url, Handler<HttpClientResponse> responseHandler, Handler<Throwable> exceptionHandler) {
    final HttpClientRequest request = client.getAbs(url);

    logger.info(String.format("GET %s", url));

    request.handler(responseHandler)
      .exceptionHandler(exceptionHandler)
      .setTimeout(reqTimeout)
      .end();
  }

  public void post(String url, String payload, Handler<HttpClientResponse> responseHandler, Handler<Throwable> exceptionHandler) {
    final HttpClientRequest request = client.postAbs(url);

    if (logger.isTraceEnabled()) {
      logger.info(String.format("POST %s Request: %s", url, payload));
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

  public void close() {
    client.close();
  }
}