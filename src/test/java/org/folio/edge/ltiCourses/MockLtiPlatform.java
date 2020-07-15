package org.folio.edge.ltiCourses;

import org.apache.log4j.Logger;

import io.vertx.core.json.JsonObject;

public class MockLtiPlatform {
  public int port;
  public String clientId;
  public String cssUrl;
  public String issuer;
  public String jwksUrl;
  public String noReservesMessage;
  public String oauthTokenUrl;
  public String oidcAuthUrl;
  public String searchUrl;

  public static MockLtiPlatform instance = null;

  private static final Logger logger = Logger.getLogger(MockLtiPlatform.class);

  public static MockLtiPlatform getInstance() {
    if (instance == null) {
      logger.error("MockLtiPlatform not initialized, must call initialize() before getInstance()");
    }

    return instance;
  }

  public static MockLtiPlatform initialize(int port) {
    instance = new MockLtiPlatform(port);
    return instance;
  }

  private MockLtiPlatform(int port) {
    this.port = port;
    this.clientId = "12345";
    this.cssUrl = "http://localhost:" + port + "/styles.css";
    this.issuer = "http://localhost:" + port;
    this.jwksUrl = "http://localhost:" + port + "/jwks.json";
    this.noReservesMessage = "No reserves :(";
    this.oauthTokenUrl = "http://localhost:" + port + "/token";
    this.oidcAuthUrl = "http://localhost:" + port + "/oidc";
    this.searchUrl = "http://localhost:" + port + "/search";
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