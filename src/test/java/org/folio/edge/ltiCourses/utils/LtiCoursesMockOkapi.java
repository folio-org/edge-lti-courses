package org.folio.edge.ltiCourses.utils;

import static org.folio.edge.core.Constants.APPLICATION_JSON;
import static org.folio.edge.core.Constants.TEXT_PLAIN;
import static org.folio.edge.core.Constants.X_OKAPI_TOKEN;

import java.util.List;

import org.apache.log4j.Logger;
import org.folio.edge.core.utils.test.MockOkapi;

import org.folio.edge.ltiCourses.LtiCoursesHandler;
import org.folio.edge.ltiCourses.MockLtiPlatform;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class LtiCoursesMockOkapi extends MockOkapi {

  private static final Logger logger = Logger.getLogger(LtiCoursesMockOkapi.class);

  public static final String titleId_notFound = "0c8e8ac5-6bcc-461e-a8d3-4b55a96addc9";

  public LtiCoursesMockOkapi(int port, List<String> knownTenants) {
    super(port, knownTenants);
  }

  @Override
  public Router defineRoutes() {
    Router router = super.defineRoutes();

    router.route(HttpMethod.GET, "/configurations/entries").handler(this::handleGetConfigurations);

    return router;
  }

  protected void handleGetConfigurations(RoutingContext ctx) {
    String query = ctx.request().getParam("query");
    Boolean isFetchingPlatform = query.contains("configName=platform");

    logger.info("isFetchingPlatform: " + isFetchingPlatform.toString());

    if (isFetchingPlatform) {
      JsonObject config = new JsonObject().put("value", MockLtiPlatform.asJsonObject().encode());
      JsonObject configs = new JsonObject().put("configs", new JsonArray().add(config));

      ctx.response()
        .setStatusCode(200)
        .putHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
        .end(configs.encodePrettily());
    }
  }
}