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

  public LtiPlatform(JsonObject configuration) {
    Iterator<Object> i = configuration.getJsonArray("configs").iterator();
    while (i.hasNext()) {
      JsonObject config = ((JsonObject) i.next());
      String value = config.getString("value");

      switch (config.getString("configName")) {
        case "platformClientId":
          this.clientId = value;
          break;
        case "platformIssuer":
          this.issuer = value;
          break;
        case "platformJwksUrl":
          this.jwksUrl = value;
          break;
        case "platformNoReservesMsg":
          this.noReservesMsg = value;
          break;
        case "platformOauthTokenUrl":
          this.oauthTokenUrl = value;
          break;
        case "platformOidcAuthUrl":
          this.oidcAuthUrl = value;
          break;
        default:
          break;
      }
    }
  }
}