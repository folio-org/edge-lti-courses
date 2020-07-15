package org.folio.edge.ltiCourses.utils;

import static org.folio.edge.core.Constants.APPLICATION_JSON;
import static org.folio.edge.core.Constants.TEXT_PLAIN;
import static org.folio.edge.core.Constants.X_OKAPI_TOKEN;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.*;

import com.auth0.jwt.algorithms.Algorithm;

import org.apache.log4j.Logger;
import static org.folio.edge.ltiCourses.Constants.JWT_KID;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import static org.junit.Assert.fail;


public class MockLtiPlatformServer {
  public final int port;
  public Algorithm algorithm;
  public Algorithm invalidAlgorithm;

  protected final Vertx vertx;
  protected KeyPair keyPair;

  private static final Logger logger = Logger.getLogger(MockLtiPlatformServer.class);

  public MockLtiPlatformServer(int port) {
    this.port = port;
    this.vertx = Vertx.vertx();
  }

  public void close(TestContext context) {
    final Async async = context.async();
    vertx.close(res -> {
      if (res.failed()) {
        logger.error("Failed to shut down mock OKAPI server", res.cause());
        fail(res.cause().getMessage());
      } else {
        logger.info("Successfully shut down mock OKAPI server");
      }
      async.complete();
    });
  }

  public void start(TestContext context) {
    HttpServer server = vertx.createHttpServer();

    keyPair = generateKeyPair();
    algorithm = Algorithm.RSA256((RSAPublicKey) keyPair.getPublic(), (RSAPrivateKey) keyPair.getPrivate());

    KeyPair invalidKP = generateKeyPair();
    invalidAlgorithm = Algorithm.RSA256((RSAPublicKey) invalidKP.getPublic(), (RSAPrivateKey) invalidKP.getPrivate());

    final Async async = context.async();
    server.requestHandler(defineRoutes()::accept).listen(port, result -> {
      if (result.failed()) {
        logger.warn(result.cause());
      }
      context.assertTrue(result.succeeded());
      async.complete();
    });
  }


  public Router defineRoutes() {
    Router router = Router.router(vertx);

    router.route(HttpMethod.GET, "/jwks.json").handler(this::handleGetJWKS);
    router.route(HttpMethod.GET, "/styles.css").handler(this::respondOK);
    router.route(HttpMethod.GET, "/oidc").handler(this::respondOK);
    router.route(HttpMethod.GET, "/token").handler(this::respondOK);
    router.route(HttpMethod.GET, "/search").handler(this::respondOK);

    return router;
  }

  protected void handleGetJWKS(RoutingContext ctx) {
    RSAPublicKey key = (RSAPublicKey) keyPair.getPublic();

    JsonObject jwk = new JsonObject();
    jwk.put("kty", key.getAlgorithm()); // getAlgorithm() returns kty not algorithm
    jwk.put("kid", JWT_KID);
    jwk.put("n", Base64.getUrlEncoder().encodeToString(key.getModulus().toByteArray()));
    jwk.put("e", Base64.getUrlEncoder().encodeToString(key.getPublicExponent().toByteArray()));
    jwk.put("alg", "RS256");
    jwk.put("use", "sig");

    JsonArray keys = new JsonArray().add(jwk);
    JsonObject jwks = new JsonObject().put("keys", keys);

    ctx.response()
      .setStatusCode(200)
      .putHeader("Content-Type", "application/json")
      .end(jwks.encode());
  }

  protected void respondOK(RoutingContext ctx) {
    ctx.response()
      .setStatusCode(200)
      .putHeader(HttpHeaders.CONTENT_TYPE, TEXT_PLAIN)
      .end("OK");
  }

  protected KeyPair generateKeyPair() {
    try {
      KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
      kpg.initialize(2048);
      return kpg.generateKeyPair();
    } catch (Exception e) {
      return null;
    }
  }
}