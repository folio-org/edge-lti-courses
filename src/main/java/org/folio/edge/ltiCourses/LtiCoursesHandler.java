package org.folio.edge.ltiCourses;

import java.net.URLEncoder;
import java.time.Instant;
import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.log4j.Logger;

import org.folio.edge.core.ApiKeyHelper;
import org.folio.edge.core.Handler;
import org.folio.edge.core.security.SecureStore;
import org.folio.edge.ltiCourses.utils.LTIContextClaim;
import org.folio.edge.ltiCourses.utils.LTIDeepLinkSettingsClaim;
import org.folio.edge.ltiCourses.utils.LtiCoursesOkapiClient;
import org.folio.edge.ltiCourses.utils.LtiCoursesOkapiClientFactory;

import static org.folio.edge.ltiCourses.Constants.BASE_URL;
import static org.folio.edge.ltiCourses.Constants.RESERVES_NOT_FOUND_MESSAGE;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.templ.jade.JadeTemplateEngine;

public class LtiCoursesHandler extends Handler {

  protected Algorithm algorithm;
  protected JadeTemplateEngine jadeTemplateEngine;
  protected JWTVerifier jwtVerifier;
  protected String toolPublicKey;

  protected String baseUrl = System.getProperty(BASE_URL);

  protected String courseNotFound = "The requested course was not found.";
  protected String reservesNotFound = System.getProperty(RESERVES_NOT_FOUND_MESSAGE, "This course has no reserves.");

  private static final Logger logger = Logger.getLogger(LtiCoursesHandler.class);

  public LtiCoursesHandler(
    SecureStore secureStore,
    LtiCoursesOkapiClientFactory ocf,
    ApiKeyHelper apiKeyHelper,
    Algorithm algorithm,
    JWTVerifier jwtVerifier,
    String toolPublicKey,
    JadeTemplateEngine jadeTemplateEngine
  ) {
    super(secureStore, ocf, apiKeyHelper);

    this.algorithm = algorithm;
    this.jwtVerifier = jwtVerifier;
    this.toolPublicKey = toolPublicKey;
    this.jadeTemplateEngine = jadeTemplateEngine;

    logger.info("Using base URL: " + baseUrl);
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
        logger.info("baseUrl=" + baseUrl);

        DecodedJWT jwt;
        try {
          jwt = jwtVerifier.verify(token);
        } catch (JWTVerificationException exception) {
          String error = "Error verifying JWT: " + exception.toString();
          logger.error(error);
          badRequest(ctx, error);
          return;
        }

        LTIDeepLinkSettingsClaim deepLinkSettingsClaim = jwt.getClaim("https://purl.imsglobal.org/spec/lti-dl/claim/deep_linking_settings").as(LTIDeepLinkSettingsClaim.class);
        LTIContextClaim contextClaim = jwt.getClaim("https://purl.imsglobal.org/spec/lti/claim/context").as(LTIContextClaim.class);

        String courseTitle = contextClaim.title;
        logger.info("Class Requested in LTI Context Claim: " + courseTitle);

        String query = "";
        try {
          query = "query=(" + courseIdType + "=\"" + URLEncoder.encode(courseTitle, "UTF-8") + "\")";
        } catch (Exception exception) {
          badRequest(ctx, "Failed to encode requested course title of " + courseTitle);
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
                JsonObject course;

                try {
                  course = new JsonObject(response.toString())
                    .getJsonArray("courses")
                    .getJsonObject(0);
                } catch (Exception exception) {
                  logger.error(courseNotFound);
                  notFound(ctx, courseNotFound);
                  return;
                }

                String courseListingId = course.getString("courseListingId");

                JsonObject term = course
                  .getJsonObject("courseListingObject", new JsonObject())
                  .getJsonObject("termObject", new JsonObject());

                JsonObject deepLinkVars = new JsonObject()
                  .put("id", courseListingId)
                  .put("startDate", term.getString("startDate", "1970-01-01"))
                  .put("endDate", term.getString("endDate", "3000-01-01"))
                  .put("reservesUrl", baseUrl + "/lti-courses/reserves/" + courseListingId + "?apiKey=" + keyHelper.getApiKey(ctx));

                logger.info("Course listing ID: " + courseListingId);
                logger.info("Reserves URL: " + baseUrl + "/lti-courses/reserves/" + courseListingId + "?apiKey=" + keyHelper.getApiKey(ctx));

                jadeTemplateEngine.render(deepLinkVars, "templates/HTMLDeepLink", deepLink -> {
                  logger.info("DeepLinkHTML: " + deepLink.result());

                  HashMap<String,String> link = new HashMap<String,String>();
                  link.put("type", "html");
                  link.put("title", deepLinkSettingsClaim.title);
                  link.put("html", deepLink.result().toString());

                  ArrayList<HashMap<String, String>> links = new ArrayList<HashMap<String, String>>();
                  links.add(link);

                  String deepLinkResponse = JWT.create()
                    .withIssuer(jwt.getAudience().get(0))
                    .withAudience(jwt.getIssuer())
                    .withExpiresAt(Date.from(Instant.now().plusSeconds(5 * 60)))
                    .withIssuedAt(new Date())
                    .withClaim("nonce", jwt.getClaim("nonce").asString())
                    .withClaim("https://purl.imsglobal.org/spec/lti/claim/message_type", "LtiDeepLinkingResponse")
                    .withClaim("https://purl.imsglobal.org/spec/lti/claim/version", "1.3.0")
                    .withClaim("https://purl.imsglobal.org/spec/lti/claim/deployment_id", jwt.getClaim("https://purl.imsglobal.org/spec/lti/claim/deployment_id").asString())
                    .withClaim("https://purl.imsglobal.org/spec/lti-dl/claim/data", deepLinkSettingsClaim.data)
                    .withClaim("https://purl.imsglobal.org/spec/lti-dl/claim/content_items", links)
                    .sign(algorithm);

                  JsonObject responseFormObject = new JsonObject()
                    .put("deepLinkReturnUrl", deepLinkSettingsClaim.deep_link_return_url)
                    .put("jwt", deepLinkResponse);

                  jadeTemplateEngine.render(responseFormObject, "templates/DeepLinkResponseForm", deepLinkResponseForm -> {
                    if (!deepLinkResponseForm.succeeded()) {
                      String error = "Failed to render DeepLinkResponseForm template: " + deepLinkResponseForm.cause();
                      logger.error(error);
                      internalServerError(ctx, error);
                      return;
                    }

                    ctx.response().setStatusCode(200);
                    ctx.response().end(deepLinkResponseForm.result());
                  });
                });
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
