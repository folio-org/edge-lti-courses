package org.folio.edge.ltiCourses;

import java.net.URLEncoder;
import java.time.Instant;
import java.util.Date;

import org.apache.log4j.Logger;

import org.folio.edge.core.ApiKeyHelper;
import org.folio.edge.core.Handler;
import org.folio.edge.core.security.SecureStore;
import org.folio.edge.ltiCourses.utils.LTIContextClaim;
import org.folio.edge.ltiCourses.utils.LTIDeepLinkSettingsClaim;
import org.folio.edge.ltiCourses.utils.LtiCoursesOkapiClient;
import org.folio.edge.ltiCourses.utils.LtiCoursesOkapiClientFactory;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class LtiCoursesHandler extends Handler {

  protected Algorithm algorithm;
  protected JWTVerifier jwtVerifier;
  protected String toolPublicKey;

  private static final Logger logger = Logger.getLogger(LtiCoursesHandler.class);

  public LtiCoursesHandler(
    SecureStore secureStore,
    LtiCoursesOkapiClientFactory ocf,
    ApiKeyHelper apiKeyHelper,
    Algorithm algorithm,
    JWTVerifier jwtVerifier,
    String toolPublicKey
  ) {
    super(secureStore, ocf, apiKeyHelper);

    this.algorithm = algorithm;
    this.jwtVerifier = jwtVerifier;
    this.toolPublicKey = toolPublicKey;
  }

  protected void handleCommonLTI(RoutingContext ctx, String courseIdType) {
    handleCommon(ctx,
      new String[] {},
      new String[] {},
      (client, params) -> {
        MultiMap attributes = ctx.request().formAttributes();
        String token = attributes.get("id_token");
        logger.info("id_token=" + token);

        logger.info("absoluteUri=" + ctx.request().absoluteURI());
        String baseUrl = ctx.request().absoluteURI().split("/lti-courses/deep-link-request")[0];
        logger.info("baseUrl=" + baseUrl);

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

        LTIDeepLinkSettingsClaim deepLinkSettingsClaim = jwt.getClaim("https://purl.imsglobal.org/spec/lti-dl/claim/deep_linking_settings").as(LTIDeepLinkSettingsClaim.class);
        LTIContextClaim contextClaim = jwt.getClaim("https://purl.imsglobal.org/spec/lti/claim/context").as(LTIContextClaim.class);

        String courseTitle = contextClaim.getTitle();
        logger.info("Class Title: " + courseTitle);

        String query = "";
        try {
          query = "query=(" + courseIdType + "=\"" + URLEncoder.encode(courseTitle, "UTF-8") + "\")";
        } catch (Exception exception) {
          logger.error(exception.toString());
          ctx.response().setStatusCode(400);
          ctx.response().end(exception.toString());
          return;
        }

        logger.info("calling LtiCoursesOkapiClient::getCourse");
        ((LtiCoursesOkapiClient) client).getCourse(
            query,
            resp -> {
              if (resp.statusCode() != 200) {
                logger.error(resp.statusCode() + ": " + resp.statusMessage());
                ctx.response().setStatusCode(resp.statusCode());
                ctx.response().end(resp.statusMessage());
                return;
              }

              resp.bodyHandler(response -> {
                String courseListingId;
                String courseName;

                try {
                  JsonObject modCoursesResponseJson = new JsonObject(response.toString());
                  JsonArray courses = modCoursesResponseJson.getJsonArray("courses");
                  JsonObject course = courses.getJsonObject(0);
                  courseListingId = course.getString("courseListingId");
                  courseName = course.getString("name");
                } catch (Exception exception) {
                  logger.error(exception.toString());
                  ctx.response().setStatusCode(400);
                  ctx.response().end(exception.toString());
                  return;
                }

                String reservesUrl = baseUrl + "/lti-courses/reserves/" + courseListingId + "?apiKey=" + keyHelper.getApiKey(ctx);

                JsonObject htmlLink = new JsonObject();
                htmlLink.put("type", "html");
                htmlLink.put("title", deepLinkSettingsClaim.getTitle());
                htmlLink.put("html", "<javascript>fetch(" + reservesUrl + ")</javascript>");

                String deepLinkResponse = JWT.create()
                  .withIssuer(jwt.getAudience().get(0))
                  .withAudience(jwt.getIssuer())
                  .withExpiresAt(Date.from(Instant.now().plusSeconds(5 * 60)))
                  .withIssuedAt(new Date())
                  .withClaim("nonce", jwt.getClaim("nonce").asString())
                  .withClaim("https://purl.imsglobal.org/spec/lti/claim/message_type", "LtiDeepLinkingResponse")
                  .withClaim("https://purl.imsglobal.org/spec/lti/claim/version", "1.3.0")
                  .withClaim("https://purl.imsglobal.org/spec/lti/claim/deployment_id", jwt.getClaim("https://purl.imsglobal.org/spec/lti/claim/deployment_id").asString())
                  .withClaim("https://purl.imsglobal.org/spec/lti-dl/claim/data", deepLinkSettingsClaim.getData())
                  .withArrayClaim("https://purl.imsglobal.org/spec/lti-dl/claim/content_items", new String[]{ htmlLink.encode() })
                  .sign(algorithm);

                logger.info("Found " + courseListingId + ": " + courseName);
                ctx.response().setStatusCode(200);
                ctx.response().end("\nFound " + courseListingId + ": " + courseName + "\n\nJWT DeepLinkResponse: " + deepLinkResponse);
              });
            },
            t -> handleProxyException(ctx, t)
          );
      }
    );
  }

  protected void handleDeepLinkRequestCourseNumber(RoutingContext ctx) {
    handleCommonLTI(ctx, "courseNumber");
  }

  protected void handleDeepLinkRequestCourseExternalId(RoutingContext ctx) {
    handleCommonLTI(ctx, "courseListing.externalId");
  }

  protected void handleDeepLinkRequestCourseRegistrarId(RoutingContext ctx) {
    handleCommonLTI(ctx, "courseListing.registrarId");
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
