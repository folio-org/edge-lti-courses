package org.folio.edge.ltiCourses.utils;

import org.apache.log4j.Logger;

import org.folio.edge.core.utils.OkapiClient;

import io.vertx.core.Vertx;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientResponse;

public class LtiCoursesOkapiClient extends OkapiClient {

  public LtiCoursesOkapiClient(OkapiClient client) {
    super(client);
  }

  protected LtiCoursesOkapiClient(Vertx vertx, String okapiURL, String tenant, long timeout) {
    super(vertx, okapiURL, tenant, timeout);
  }

  public void getCourse(
    String query,
    Handler<HttpClientResponse> responseHandler,
    Handler<Throwable> exceptionHandler
  ) {
    get(
      okapiURL + "/coursereserves/courses?" + query,
      tenant,
      responseHandler,
      exceptionHandler
    );
  }

  public void getCourseReserves(
    String courseId,
    Handler<HttpClientResponse> responseHandler,
    Handler<Throwable> exceptionHandler
  ) {
    get(
      okapiURL + "/coursereserves/courselistings/" + courseId + "/reserves?unused=9999&expand=*&limit=500&query=cql.allRecords=1%20sortby%20copiedItem.title",
      tenant,
      responseHandler,
      exceptionHandler
    );
  }
}
