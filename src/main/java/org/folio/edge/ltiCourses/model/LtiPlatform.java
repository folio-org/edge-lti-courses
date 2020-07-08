package org.folio.edge.ltiCourses.model;

import java.util.Iterator;
import io.vertx.core.json.JsonObject;

public class LtiPlatform {
  public String clientId;
  public String issuer;
  public String jwksUrl;
  public String noReservesMsg;
  public String oauthTokenUrl;
  public String oidcAuthUrl;
  public String searchUrl;

  public LtiPlatform(JsonObject configuration) {
    JsonObject platform = new JsonObject(
      configuration
        .getJsonArray("configs")
        .getJsonObject(0)
        .getString("value")
      );

      this.clientId = platform.getString("clientId");
      this.issuer = platform.getString("issuer");
      this.jwksUrl = platform.getString("jwksUrl");
      this.oidcAuthUrl = platform.getString("oidcAuthUrl");
      this.noReservesMsg = platform.getString("oidcAuthUrl", "This course has no current reserves.");
      this.searchUrl = platform.getString("searchUrl");
  }
}