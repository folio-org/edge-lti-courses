package org.folio.edge.ltiCourses.utils;

import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import org.apache.http.client.ResponseHandler;

import org.folio.edge.core.utils.OkapiClient;

import io.vertx.core.Vertx;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientResponse;

public class LtiCoursesOkapiClient extends OkapiClient {

  public LtiCoursesOkapiClient(OkapiClient client) {
    super(client);
  }

  protected LtiCoursesOkapiClient(Vertx vertx, String okapiURL, String tenant, int timeout) {
    super(vertx, okapiURL, tenant, timeout);
  }

  public void getPlatform(
    String issuer,
    Handler<HttpResponse<Buffer>> responseHandler,
    Handler<Throwable> exceptionHandler
  ) {
    // mod-configuration can't run queries with slashes in them (even if they're url-encoded),
    // so we search for the protocol-less version of the issuer if we think it contains one.
    String issuerQuery = issuer;
    if (issuerQuery.contains("://")) {
      issuerQuery = issuerQuery.substring(issuerQuery.indexOf("://") + 3);
    }

    get(
      okapiURL + "/configurations/entries?limit=100&query=(module=EDGELTICOURSES+and+configName=platform+and+code=" + issuerQuery + ")",
      tenant,
      responseHandler,
      exceptionHandler
    );
  }

  public void getCourse(
    String query,
    Handler<HttpResponse<Buffer>> responseHandler,
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
    Handler<HttpResponse<Buffer>> responseHandler,
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
