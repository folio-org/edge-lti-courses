package org.folio.edge.ltiCourses.utils;

import static org.folio.edge.core.Constants.APPLICATION_JSON;

import java.util.List;

import org.apache.log4j.Logger;
import org.folio.edge.core.utils.test.MockOkapi;

import org.folio.edge.ltiCourses.MockLtiPlatform;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class LtiCoursesMockOkapi extends MockOkapi {

  private static final Logger logger = Logger.getLogger(LtiCoursesMockOkapi.class);

  public final String courseWithReserves = "COURSE101";
  public final String courseWithoutReserves = "COURSE201";

  public LtiCoursesMockOkapi(int port, List<String> knownTenants) {
    super(port, knownTenants);
  }

  @Override
  public Router defineRoutes() {
    Router router = super.defineRoutes();

    router.route(HttpMethod.GET, "/configurations/entries").handler(this::handleGetConfigurations);
    router.route(HttpMethod.GET, "/coursereserves/courses").handler(this::handleGetCourses);
    router.route(HttpMethod.GET, "/coursereserves/courselistings/:courseId/reserves").handler(this::handleGetCourseReserves);

    return router;
  }

  protected void handleGetConfigurations(RoutingContext ctx) {
    String query = ctx.request().getParam("query");
    Boolean isFetchingPlatform = query.contains("configName=platform");

    if (isFetchingPlatform) {
      JsonObject config = new JsonObject().put("value", MockLtiPlatform.getInstance().asJsonObject().encode());
      JsonObject configs = new JsonObject().put("configs", new JsonArray().add(config));

      ctx.response()
        .setStatusCode(200)
        .putHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
        .end(configs.encodePrettily());
    }
  }

  protected void handleGetCourses(RoutingContext ctx) {
    JsonArray courses = new JsonArray();

    if (ctx.request().getParam("query").contains(courseWithReserves)) {
      courses.add(new JsonObject()
        .put("id", courseWithReserves)
        .put("courseListingId", courseWithReserves)
        .put("courseListingObject", new JsonObject()
          .put("termObject", new JsonObject()
            .put("startDate", "2020-06-01")
            .put("endDate", "2020-12-31")
          )
        )
      );
    }

    ctx.response()
      .setStatusCode(200)
      .putHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
      .end(new JsonObject().put("courses", courses).encode());
  }

  protected void handleGetCourseReserves(RoutingContext ctx) {
    JsonArray reserves = new JsonArray();

    if (courseWithReserves.equals(ctx.request().getParam("courseId"))) {
      reserves.add(new JsonObject()
        .put("itemId", "foo")
        .put("barcode", "123")
      );

      reserves.add(new JsonObject()
        .put("itemId", "bar")
        .put("barcode", "456")
      );
    }

    ctx.response()
      .setStatusCode(200)
      .putHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
      .end(new JsonObject().put("reserves", reserves).encode());
  }
}