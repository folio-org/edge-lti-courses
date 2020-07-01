package org.folio.edge.ltiCourses;

import java.net.URL;
import java.net.URLEncoder;
import java.time.Instant;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;

import org.apache.log4j.Logger;

import org.folio.rest.client.ConfigurationsClient;
import org.folio.edge.core.ApiKeyHelper;
import org.folio.edge.core.Handler;
import org.folio.edge.core.security.SecureStore;
import org.folio.edge.ltiCourses.cache.OidcStateCache;
import org.folio.edge.ltiCourses.utils.LtiContextClaim;
import org.folio.edge.ltiCourses.utils.LtiDeepLinkSettingsClaim;
import org.folio.edge.ltiCourses.utils.LtiCoursesOkapiClient;
import org.folio.edge.ltiCourses.utils.LtiCoursesOkapiClientFactory;
import org.folio.edge.ltiCourses.utils.LtiPlatform;
import org.folio.edge.ltiCourses.utils.LtiPlatformClient;
import org.folio.edge.ltiCourses.utils.LtiPlatformClientFactory;

import static org.folio.edge.ltiCourses.Constants.BASE_URL;
import static org.folio.edge.ltiCourses.Constants.RESERVES_NOT_FOUND_MESSAGE;
import static org.folio.edge.ltiCourses.Constants.LTI_MESSAGE_TYPE_RESOURCE_LINK_REQUEST;
import static org.folio.edge.ltiCourses.Constants.LTI_MESSAGE_TYPE_DEEP_LINK_REQUEST;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

import io.netty.handler.codec.http2.Http2Stream.State;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.templ.jade.JadeTemplateEngine;

public class LtiCoursesHandler extends Handler {
  protected LtiPlatformClientFactory pcf;
  protected RSAPrivateKey privateKey;
  protected JadeTemplateEngine jadeTemplateEngine;
  protected String toolPublicKey;

  protected String baseUrl = System.getProperty(BASE_URL);

  protected String courseNotFound = "The requested course was not found.";
  protected String reservesNotFound = System.getProperty(RESERVES_NOT_FOUND_MESSAGE, "This course has no reserves.");

  private static final Logger logger = Logger.getLogger(LtiCoursesHandler.class);

  public LtiCoursesHandler(
    SecureStore secureStore,
    LtiCoursesOkapiClientFactory ocf,
    ApiKeyHelper apiKeyHelper,
    LtiPlatformClientFactory pcf,
    RSAPrivateKey privateKey,
    JadeTemplateEngine jadeTemplateEngine
  ) {
    super(secureStore, ocf, apiKeyHelper);

    this.pcf = pcf;
    this.privateKey = privateKey;
    this.jadeTemplateEngine = jadeTemplateEngine;

    logger.info("Using base URL: " + baseUrl);
  }

  protected void handleCommonLTI(RoutingContext ctx, String courseIdType, Boolean isDeepLinkingRoute) {
    handleCommon(ctx,
      new String[] {},
      new String[] {},
      (client, params) -> {
        MultiMap attributes = ctx.request().formAttributes();

        String id_token = attributes.get("id_token");
        logger.info("id_token=" + id_token);
        DecodedJWT jwt = JWT.decode(id_token);

        // Look up the Platform's JWKS url.
        ((LtiCoursesOkapiClient) client).getConfigurations(
          confResp -> {
            if (confResp.statusCode() != 200) {
              logger.error(confResp.statusCode() + ": " + confResp.statusMessage());
              ctx.response().setStatusCode(confResp.statusCode()).end(confResp.statusMessage());
              return;
            }

            confResp.bodyHandler(confBody -> {
              LtiPlatform platform = new LtiPlatform(new JsonObject(confBody.toString()));

              // Fetch the JWK
              RSAPublicKey platformPublicKey;
              try {
                JwkProvider jwkProvider = new UrlJwkProvider(new URL(platform.jwksUrl));
                Jwk jwk = jwkProvider.get(jwt.getKeyId());
                platformPublicKey = (RSAPublicKey) jwk.getPublicKey();
              } catch (Exception e) {
                logger.error(e.getLocalizedMessage());
                badRequest(ctx, e.getLocalizedMessage());
                return;
              }

              // Validate the JWT
              Algorithm algorithm = Algorithm.RSA256(platformPublicKey, privateKey);
              algorithm.verify(jwt);

              if (!jwt.getIssuer().equals(platform.issuer)) {
                badRequest(ctx, "JWT 'iss' doesn't match the configured Platform Issuer");
                return;
              }

              if (jwt.getAudience().contains(platform.clientId) == false) {
                badRequest(ctx, "JWT 'aud' doesn't contain the configured client ID");
                return;
              }

              String nonce = jwt.getClaim("nonce").asString();
              if (nonce.isEmpty()) {
                badRequest(ctx, "Nonce is missing from request");
                return;
              }

              String memorizedState = OidcStateCache.getInstance().get(nonce);
              String state = attributes.get("state");
              if (!memorizedState.equals(state)) {
                logger.error("Got new state of: " + state + " but expected: " + memorizedState);
                badRequest(ctx, "Nonce is invalid, states do not match");
                return;
              }

              String message_type = jwt.getClaim("https://purl.imsglobal.org/spec/lti/claim/message_type").asString();

              LtiContextClaim contextClaim = jwt.getClaim("https://purl.imsglobal.org/spec/lti/claim/context").as(LtiContextClaim.class);

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
                  courseResp -> {
                    if (courseResp.statusCode() != 200) {
                      badRequest(ctx, courseResp.statusMessage());
                      return;
                    }

                    courseResp.bodyHandler(courseBody -> {
                      JsonObject course;

                      try {
                        course = new JsonObject(courseBody.toString())
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

                      if (message_type.equals(LTI_MESSAGE_TYPE_RESOURCE_LINK_REQUEST)) {
                        ((LtiCoursesOkapiClient) client).getCourseReserves(
                          courseListingId,
                          reservesResp -> {
                            if (reservesResp.statusCode() != 200) {
                              badRequest(ctx, reservesResp.statusMessage());
                              return;
                            }

                            reservesResp.bodyHandler(reservesBody -> {
                              JsonArray reserves = new JsonObject(reservesBody.toString()).getJsonArray("reserves");

                              ctx.response().setStatusCode(200).end(reserves.encodePrettily());
                            });
                          },
                          t -> handleProxyException(ctx, t)
                        );
                      } else if (message_type.equals(LTI_MESSAGE_TYPE_DEEP_LINK_REQUEST)) {
                        jadeTemplateEngine.render(deepLinkVars, "templates/HTMLDeepLink", deepLink -> {
                          LtiDeepLinkSettingsClaim deepLinkSettingsClaim = jwt.getClaim("https://purl.imsglobal.org/spec/lti-dl/claim/deep_linking_settings").as(LtiDeepLinkSettingsClaim.class);
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
                      } else {
                        badRequest(ctx, "Invalid message_type claim: " + message_type);
                      }
                    });
                  },
                  t -> handleProxyException(ctx, t)
                );
            });
          },
          t -> handleProxyException(ctx, t)
        );
      }
    );
  }

  protected void handleOidcLoginInit(RoutingContext ctx) {
    handleCommon(ctx,
      new String[] {
        "iss",
        "login_hint",
        "target_link_uri"
      },
      new String[] {
        "lti_message_hint"
      },
      (client, params) -> {
        String iss = params.get("iss");
        String login_hint = params.get("login_hint");
        String lti_message_hint = params.get("lti_message_hint");
        String target_link_uri = params.get("target_link_uri");

        // look up the redirect url via the iss -> authURL mapping we'll store in mod-configuration
        // for now, hardcode to the Duke Sakai instance at https://https://sakai-test.duke.edu/
        ((LtiCoursesOkapiClient) client).getConfigurations(
          resp -> {
            if (resp.statusCode() != 200) {
              logger.error(resp.statusCode() + ": " + resp.statusMessage());
              ctx.response().setStatusCode(resp.statusCode());
              ctx.response().end(resp.statusMessage());
              return;
            }

            resp.bodyHandler(response -> {
              LtiPlatform platform = new LtiPlatform(new JsonObject(response.toString()));

              if (!platform.issuer.equals(iss)) {
                logger.error("Got iss=" + iss + " and expected iss=" + platform.issuer);
                badRequest(ctx, "Configured Issuer does not matched the OIDC 'iss'");
                return;
              }

              String redirectUri = target_link_uri;
              try {
                redirectUri = URLEncoder.encode(target_link_uri, "UTF-8");
              } catch (Exception e) {
                logger.error("Can't encode to UTF-8???");
              }

              String nonce = generateRandomString();
              String state = generateRandomString();
              OidcStateCache.getInstance().put(nonce, state);

              String authRequestUrl = platform.oidcAuthUrl + "?";
              authRequestUrl += "client_id=" + platform.clientId;
              authRequestUrl += "&login_hint=" + login_hint;
              authRequestUrl += "&nonce=" + nonce;
              authRequestUrl += "&prompt=none";
              authRequestUrl += "&redirect_uri=" + redirectUri;
              authRequestUrl += "&response_mode=form_post";
              authRequestUrl += "&response_type=id_token";
              authRequestUrl += "&scope=openid";
              authRequestUrl += "&state=" + state;

              if (lti_message_hint != null && !lti_message_hint.isEmpty()) {
                authRequestUrl += "&lti_message_hint=" + lti_message_hint;
              }

              ctx.response()
                .setStatusCode(302)
                .putHeader("location", authRequestUrl)
                .end();
            });
          },
          t -> handleProxyException(ctx, t)
        );
      }
    );
  }

  protected void handleRequest(RoutingContext ctx) {
    handleCommonLTI(ctx, "courseNumber", false);
  }

  protected void handleDeepLinkRequestCourseNumber(RoutingContext ctx) {
    handleCommonLTI(ctx, "courseNumber", true);
  }

  protected void handleDeepLinkRequestCourseExternalId(RoutingContext ctx) {
    handleCommonLTI(ctx, "courseListing.externalId", true);
  }

  protected void handleDeepLinkRequestCourseRegistrarId(RoutingContext ctx) {
    handleCommonLTI(ctx, "courseListing.registrarId", true);
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

  protected void handleGetJWKS(RoutingContext ctx) {
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

  private String generateRandomString() {
    int leftLimit = 97; // 'a'
    int rightLimit = 122; // 'z'

    return new Random().ints(leftLimit, rightLimit + 1)
      .limit(20)
      .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
      .toString();
  }
}
