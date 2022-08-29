package org.folio.edge.ltiCourses.utils;

import org.folio.edge.core.utils.OkapiClientFactory;

import io.vertx.core.Vertx;

public class LtiCoursesOkapiClientFactory extends OkapiClientFactory {

  public LtiCoursesOkapiClientFactory(Vertx vertx, String okapiURL, int reqTimeoutMs) {
    super(vertx, okapiURL, reqTimeoutMs);
  }

  public LtiCoursesOkapiClient getOkapiClient(String tenant) {
    return new LtiCoursesOkapiClient(vertx, okapiURL, tenant, reqTimeoutMs);
  }
}
