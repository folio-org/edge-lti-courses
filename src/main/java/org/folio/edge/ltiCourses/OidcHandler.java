package org.folio.edge.ltiCourses;

import org.folio.edge.ltiCourses.utils.LtiPlatformClient;
import org.folio.edge.ltiCourses.utils.LtiPlatformClientFactory;

import io.vertx.ext.web.RoutingContext;

public class OidcHandler {

  protected LtiPlatformClientFactory pcf;

  public OidcHandler(LtiPlatformClientFactory pcf) {
    this.pcf = pcf;
  }

  protected void handleOidcLoginInit(RoutingContext ctx) {
    LtiPlatformClient client = pcf.getLtiPlatformClient();
  }
}