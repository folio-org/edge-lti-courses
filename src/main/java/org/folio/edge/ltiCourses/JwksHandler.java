package org.folio.edge.ltiCourses;

import java.security.interfaces.RSAPublicKey;
import java.util.*;

import io.vertx.ext.web.RoutingContext;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static org.folio.edge.ltiCourses.Constants.JWT_KID;


public class JwksHandler {
  protected RSAPublicKey publicKey;

  private static final Logger logger = LogManager.getLogger(JwksHandler.class);

  public JwksHandler(RSAPublicKey publicKey) {
    this.publicKey = publicKey;
  }

  protected void handleGetJWKS(RoutingContext ctx) {
    logger.info("Handling request for JWKS");

    if (publicKey == null) {
      ctx.response().setStatusCode(404).end();
    }

    JsonObject jwk = new JsonObject();
    jwk.put("kty", publicKey.getAlgorithm()); // getAlgorithm() returns kty not algorithm
    jwk.put("kid", JWT_KID);
    jwk.put("n", Base64.getUrlEncoder().encodeToString(publicKey.getModulus().toByteArray()));
    jwk.put("e", Base64.getUrlEncoder().encodeToString(publicKey.getPublicExponent().toByteArray()));
    jwk.put("alg", "RS256");
    jwk.put("use", "sig");

    JsonArray keys = new JsonArray().add(jwk);
    JsonObject jwks = new JsonObject().put("keys", keys);

    ctx.response()
      .setStatusCode(200)
      .putHeader("Content-Type", "application/json")
      .end(jwks.encode());
  }
}