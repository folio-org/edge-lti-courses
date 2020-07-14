package org.folio.edge.ltiCourses;

import io.vertx.core.json.JsonObject;

public class MockLtiPlatform {
  public static String clientId = "12345";
  public static String cssUrl = "https://css.url/styles.css";
  public static String issuer = "https://duki.edu/";
  public static String jwksUrl = "https://duki.edu/jwks.json";
  public static String noReservesMessage = "No reserves :(";
  public static String oauthTokenUrl = "https://duki.edu/token";
  public static String oidcAuthUrl = "https://duki.edu/oidc";
  public static String searchUrl = "https://duki.edu/search";

  public static JsonObject asJsonObject() {
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