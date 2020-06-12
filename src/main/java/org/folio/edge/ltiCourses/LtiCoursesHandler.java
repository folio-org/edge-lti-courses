package org.folio.edge.ltiCourses;

import org.apache.log4j.Logger;
import org.folio.edge.core.ApiKeyHelper;
import org.folio.edge.core.Handler;
import org.folio.edge.core.security.SecureStore;
import org.folio.edge.ltiCourses.utils.LTIContextClaim;
import org.folio.edge.ltiCourses.utils.LtiCoursesOkapiClient;
import org.folio.edge.ltiCourses.utils.LtiCoursesOkapiClientFactory;

import java.security.interfaces.RSAPublicKey;

import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.Claim;

import io.vertx.core.MultiMap;
import io.vertx.ext.web.RoutingContext;

public class LtiCoursesHandler extends Handler {

  protected JWTVerifier jwtVerifier;
  protected String toolPublicKey;

  private static final Logger logger = Logger.getLogger(LtiCoursesHandler.class);

  public LtiCoursesHandler(
    SecureStore secureStore,
    LtiCoursesOkapiClientFactory ocf,
    ApiKeyHelper apiKeyHelper,
    JWTVerifier jwtVerifier,
    String toolPublicKey
  ) {
    super(secureStore, ocf, apiKeyHelper);

    this.jwtVerifier = jwtVerifier;
    this.toolPublicKey = toolPublicKey;
  }

  protected void handleDeepLinkRequest(RoutingContext ctx) {
    handleCommon(ctx,
      new String[] {},
      new String[] {},
      (client, params) -> {
        MultiMap attributes = ctx.request().formAttributes();
        String token = attributes.get("id_token");
        logger.info("id_token=" + token);

        DecodedJWT jwt;
        try {
          jwt = jwtVerifier.verify(token);
        } catch (JWTVerificationException exception) {
          String error = "Error verifying JWT: " + exception.toString();
          logger.error(error);

          ctx.response().setStatusCode(400);
          ctx.response().end(error);
          return;
        }

        Claim claim = jwt.getClaim("https://purl.imsglobal.org/spec/lti/claim/context");
        LTIContextClaim contextClaim = claim.as(LTIContextClaim.class);
        String courseId = contextClaim.getTitle();
        logger.info("Class Title: " + courseId);

        logger.info("calling LtiCoursesOkapiClient::getCourse");
        ((LtiCoursesOkapiClient) client).getCourse(
            "query=(courseNumber=\"" + courseId + "\")",
            // "(courseListing.externalId=\"" + courseId + "\")",
            // "(courseListing.registrarId=\"" + courseId + "\")",
            resp -> handleProxyResponse(ctx, resp),
            t -> handleProxyException(ctx, t)
          );
      });
  }

  protected void handleGetReservesById(RoutingContext ctx) {
    handleCommon(ctx,
      new String[] { "courseId" },
      new String[] {},
      (client, params) -> {
        logger.info("calling LtiCoursesOkapiClient::getCourseReserves");

        ((LtiCoursesOkapiClient) client).getCourseReserves(
          params.get("courseId"),
          resp -> handleProxyResponse(ctx, resp),
          t -> handleProxyException(ctx, t)
        );
      }
    );
  }

  protected void handleGetPublicKey(RoutingContext ctx) {
    handleCommon(ctx,
      new String[] {},
      new String[] {},
      (client, params) -> {
        logger.info("Handling request for public key");

        ctx.response().setStatusCode(200);
        ctx.response().end(toolPublicKey);
      }
    );
  }
}
