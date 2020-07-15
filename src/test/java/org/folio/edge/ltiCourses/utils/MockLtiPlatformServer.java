package org.folio.edge.ltiCourses.utils;

import static org.folio.edge.core.Constants.APPLICATION_JSON;
import static org.folio.edge.core.Constants.TEXT_PLAIN;
import static org.folio.edge.core.Constants.X_OKAPI_TOKEN;

import java.util.List;

import org.apache.log4j.Logger;
import org.folio.edge.core.utils.test.MockOkapi;

import org.folio.edge.ltiCourses.LtiCoursesHandler;
import org.folio.edge.ltiCourses.MockLtiPlatform;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import static org.junit.Assert.fail;


public class MockLtiPlatformServer {
  public final int port;
  protected final Vertx vertx;

  private static final Logger logger = Logger.getLogger(MockLtiPlatformServer.class);

  public static final String titleId_notFound = "0c8e8ac5-6bcc-461e-a8d3-4b55a96addc9";

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

  }

  protected void respondOK(RoutingContext ctx) {
    ctx.response()
      .setStatusCode(200)
      .putHeader(HttpHeaders.CONTENT_TYPE, TEXT_PLAIN)
      .end("OK");
  }
}