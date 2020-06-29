package org.folio.edge.ltiCourses.utils;

import io.vertx.core.Vertx;

public class LtiPlatformClientFactory {

  public final Vertx vertx;
  public final long reqTimeoutMs;

  public LtiPlatformClientFactory(Vertx vertx, long reqTimeoutMs) {
    this.vertx = vertx;
    this.reqTimeoutMs = reqTimeoutMs;
  }

  public LtiPlatformClient getLtiPlatformClient() {
    return new LtiPlatformClient(vertx, reqTimeoutMs);
  }
}