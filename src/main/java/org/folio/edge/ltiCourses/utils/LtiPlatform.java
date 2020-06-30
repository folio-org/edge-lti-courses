package org.folio.edge.ltiCourses.utils;

import java.util.Iterator;
import io.vertx.core.json.JsonObject;

public class LtiPlatform {
  public String clientId;
  public String issuer;
  public String jwksUrl;
  public String oauthTokenUrl;
  public String oidcAuthUrl;

  public LtiPlatform(JsonObject configuration) {
    Iterator<Object> i = configuration.getJsonArray("configs").iterator();
    while (i.hasNext()) {
      JsonObject config = ((JsonObject) i.next());

      if (config.getString("configName") == "platformIssuer") {
        issuer = config.getString("value");
      }

      if (config.getString("configName") == "platformJwksUrl") {
        jwksUrl = config.getString("value");
      }

      if (config.getString("configName") == "platformOauthTokenUrl") {
        oauthTokenUrl = config.getString("value");
      }

      if (config.getString("configName") == "platformOidcAuthUrl") {
        oidcAuthUrl = config.getString("value");
      }

      if (config.getString("configName") == "platformClientId") {
        clientId = config.getString("value");
      }
    }
  }
}