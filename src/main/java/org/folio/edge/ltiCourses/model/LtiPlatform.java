package org.folio.edge.ltiCourses.model;

import io.vertx.core.json.JsonObject;

import static org.folio.edge.ltiCourses.Constants.DEFAULT_RESERVES_NOT_FOUND_MESSAGE;

public class LtiPlatform {
  public String clientId;
  public String cssUrl;
  public String issuer;
  public String jwksUrl;
  public String noReservesMessage;
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
      this.cssUrl = platform.getString("cssUrl");
      this.issuer = platform.getString("issuer");
      this.jwksUrl = platform.getString("jwksUrl");
      this.oidcAuthUrl = platform.getString("oidcAuthUrl");
      this.noReservesMessage = platform.getString("noReservesMessage", DEFAULT_RESERVES_NOT_FOUND_MESSAGE);
      this.searchUrl = platform.getString("searchUrl");
  }

  public JsonObject asJsonObject() {
    return new JsonObject()
      .put("clientId", clientId)
      .put("cssUrl", cssUrl)
      .put("issuer", issuer)
      .put("jwksUrl", jwksUrl)
      .put("oidcAuthUrl", oidcAuthUrl)
      .put("noReservesMessage", noReservesMessage)
      .put("searchUrl", searchUrl);
  }
}