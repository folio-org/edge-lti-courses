package org.folio.edge.ltiCourses;

import java.net.URL;
import java.net.URLEncoder;
import java.time.Instant;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.apache.log4j.Logger;

import org.folio.edge.core.ApiKeyHelper;
import org.folio.edge.core.security.SecureStore;
import org.folio.edge.ltiCourses.cache.OidcStateCache;
import org.folio.edge.ltiCourses.model.Course;
import org.folio.edge.ltiCourses.model.LtiPlatform;
import org.folio.edge.ltiCourses.utils.LtiContextClaim;
import org.folio.edge.ltiCourses.utils.LtiDeepLinkSettingsClaim;
import org.folio.edge.ltiCourses.utils.LtiCoursesOkapiClient;
import org.folio.edge.ltiCourses.utils.LtiCoursesOkapiClientFactory;
import org.folio.edge.ltiCourses.utils.LtiPlatformClientFactory;

import static org.folio.edge.ltiCourses.Constants.BASE_URL;
import static org.folio.edge.ltiCourses.Constants.RESERVES_NOT_FOUND_MESSAGE;
import static org.folio.edge.ltiCourses.Constants.LTI_MESSAGE_TYPE_RESOURCE_LINK_REQUEST;
import static org.folio.edge.ltiCourses.Constants.LTI_MESSAGE_TYPE_DEEP_LINK_REQUEST;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.templ.jade.JadeTemplateEngine;

public class LtiCoursesHandler extends org.folio.edge.core.Handler {
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

  protected void handleCommonLTI(
    RoutingContext ctx,
    String[] requiredParams,
    String[] optionalParams,
    ThreeParamVoidFunction<LtiCoursesOkapiClient, Map<String, String>, LtiPlatform> action
  ) {
    handleCommon(
      ctx,
      requiredParams,
      optionalParams,
      (client, params) -> {
        LtiCoursesOkapiClient coursesOkapiClient = (LtiCoursesOkapiClient) client;
        coursesOkapiClient.getConfigurations(
          response -> {
            if (response.statusCode() != 200) {
              logger.error(response.statusCode() + ": " + response.statusMessage());
              ctx.response().setStatusCode(response.statusCode()).end(response.statusMessage());
              return;
            }

            response.bodyHandler(body -> {
              LtiPlatform platform = new LtiPlatform(new JsonObject(body.toString()));
              action.apply(
                coursesOkapiClient,
                params,
                platform
              );
            });
          },
          t -> handleProxyException(ctx, t)
        );
      }
    );
  }

  protected void getCourse(
    RoutingContext ctx,
    LtiCoursesOkapiClient client,
    DecodedJWT jwt,
    String courseIdType,
    OneParamVoidFunction<Course> action
  ) {
    LtiContextClaim contextClaim = jwt.getClaim("https://purl.imsglobal.org/spec/lti/claim/context").as(LtiContextClaim.class);
    String courseTitle = contextClaim.title;
    logger.info("Class Requested in LTI Context Claim: " + courseTitle);

    String query = "";
    try {
      // We're constructing a query string to look like: query=(courseNumber="CAL101")
      query = "query=%28" + courseIdType + "%3D%22" + URLEncoder.encode(courseTitle, "UTF-8") + "%22%29";
    } catch (Exception exception) {
      loggedBadRequest(ctx, "Failed to encode requested course title of " + courseTitle);
      return;
    }

    logger.info("calling LtiCoursesOkapiClient::getCourse");
    client.getCourse(query, courseResp -> {
      if (courseResp.statusCode() != 200) {
        internalServerError(ctx, "Folio had an internal server error");
        return;
      }

      courseResp.bodyHandler(courseBody -> {
        JsonObject courseJson;

        try {
          courseJson = new JsonObject(courseBody.toString())
            .getJsonArray("courses")
            .getJsonObject(0);
        } catch (Exception exception) {
          notFound(ctx, courseNotFound);
          return;
        }

        Course course;
        try {
          course = new Course(courseJson);
        } catch (Exception exception) {
          logger.error("Failed to parse course from JsonObject: " + courseJson.encode());
          notFound(ctx, courseNotFound);
          return;
        }

        client.getCourseReserves(
          course.courseListingId,
          reservesResp -> {
            if (reservesResp.statusCode() != 200) {
              loggedBadRequest(ctx, reservesResp.statusMessage());
              return;
            }

            reservesResp.bodyHandler(reservesBody -> {
              course.setReserves(reservesBody.toString());
              action.apply(course);
            });
          },
          t -> handleProxyException(ctx, t)
        );
      });
    }, t -> handleProxyException(ctx, t));
  }

  protected void handleLaunch(RoutingContext ctx, String courseIdType, Boolean isDeepLinkingRoute) {
    handleCommonLTI(
      ctx,
      new String[] {},
      new String[] {},
      (client, params, platform) -> {
        MultiMap attributes = ctx.request().formAttributes();

        String id_token = attributes.get("id_token");
        logger.info("id_token=" + id_token);
        if (id_token == null || id_token.isEmpty()) {
          loggedBadRequest(ctx, "id_token is required and was not found");
          return;
        }

        DecodedJWT jwt = JWT.decode(id_token);

        // Fetch the JWK so we can validate it.
        RSAPublicKey platformPublicKey;
        try {
          JwkProvider jwkProvider = new UrlJwkProvider(new URL(platform.jwksUrl));
          Jwk jwk = jwkProvider.get(jwt.getKeyId());
          platformPublicKey = (RSAPublicKey) jwk.getPublicKey();
        } catch (Exception e) {
          loggedBadRequest(ctx, "Failed to fetch Platform's JWKS: " + e.getLocalizedMessage());
          return;
        }

        // Validate the JWT
        Algorithm algorithm = Algorithm.RSA256(platformPublicKey, privateKey);
        algorithm.verify(jwt);

        if (!jwt.getIssuer().equals(platform.issuer)) {
          loggedBadRequest(ctx, "JWT 'iss' doesn't match the configured Platform Issuer");
          return;
        }

        if (jwt.getAudience().contains(platform.clientId) == false) {
          loggedBadRequest(ctx, "JWT 'aud' doesn't contain the configured client ID");
          return;
        }

        String nonce = jwt.getClaim("nonce").asString();
        if (nonce.isEmpty()) {
          loggedBadRequest(ctx, "Nonce is missing from request");
          return;
        }

        String memorizedState = OidcStateCache.getInstance().get(nonce);
        OidcStateCache.getInstance().put(nonce, null);
        String state = attributes.get("state");
        if (!memorizedState.equals(state)) {
          logger.error("Got new state of: " + state + " but expected: " + memorizedState);
          badRequest(ctx, "Nonce is invalid, states do not match");
          return;
        }

        getCourse(ctx, client, jwt, courseIdType,
          course -> {
            String message_type = jwt.getClaim("https://purl.imsglobal.org/spec/lti/claim/message_type").asString();
            if (message_type.equals(LTI_MESSAGE_TYPE_RESOURCE_LINK_REQUEST)) {
              renderResourceLink(ctx, jwt, course);
            } else if (message_type.equals(LTI_MESSAGE_TYPE_DEEP_LINK_REQUEST)) {
              renderDeepLink(ctx, jwt, course, algorithm);
            } else {
              loggedBadRequest(ctx, "Invalid message_type claim: " + message_type);
            }
          }
        );
      }
    );
  }

  protected void handleOidcLoginInit(RoutingContext ctx) {
    handleCommonLTI(
      ctx,
      new String[] {
        "iss",
        "login_hint",
        "target_link_uri"
      },
      new String[] {
        "lti_message_hint"
      },
      (client, params, platform) -> {
        String iss = params.get("iss");
        String login_hint = params.get("login_hint");
        String lti_message_hint = params.get("lti_message_hint");
        String target_link_uri = params.get("target_link_uri");

        if (!platform.issuer.equals(iss)) {
          loggedBadRequest(ctx, "Configured Issuer does not matched the OIDC request's issuer");
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
      }
    );
  }

  protected void handleRequest(RoutingContext ctx) {
    handleLaunch(ctx, "courseNumber", false);
  }

  protected void handleDeepLinkRequestCourseNumber(RoutingContext ctx) {
    handleLaunch(ctx, "courseNumber", true);
  }

  protected void handleDeepLinkRequestCourseExternalId(RoutingContext ctx) {
    handleLaunch(ctx, "courseListing.externalId", true);
  }

  protected void handleDeepLinkRequestCourseRegistrarId(RoutingContext ctx) {
    handleLaunch(ctx, "courseListing.registrarId", true);
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

  protected void renderDeepLink(RoutingContext ctx, DecodedJWT jwt, Course course, Algorithm algorithm) {
    JsonObject deepLinkVars = new JsonObject()
      .put("id", course.courseListingId);
    // .put("startDate", course.startDate)
    // .put("endDate", course.endDate)
    // .put("reservesUrl", baseUrl + "/lti-courses/reserves/" + courseListingId + "?apiKey=" + keyHelper.getApiKey(ctx));

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
  }

  protected void renderResourceLink(RoutingContext ctx, DecodedJWT jwt, Course course) {
    JsonObject model = new JsonObject()
      .put("reserves", course.getCurrentReserves());

    jadeTemplateEngine.render(model, "templates/ResourceLinkResponse", html -> {
      if (!html.succeeded()) {
        loggedInternalServerError(ctx, "Failed to render resource link: " + html.cause());
        return;
      }

      ctx.response().setStatusCode(200).end(html.result().toString());
    });
  }

  protected void loggedBadRequest(RoutingContext ctx, String msg) {
    logger.error(msg);
    badRequest(ctx, msg);
  }

  protected void loggedInternalServerError(RoutingContext ctx, String msg) {
    logger.error(msg);
    internalServerError(ctx, msg);
  }

  private String generateRandomString() {
    int leftLimit = 97; // 'a'
    int rightLimit = 122; // 'z'

    return new Random().ints(leftLimit, rightLimit + 1)
      .limit(20)
      .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
      .toString();
  }

  @FunctionalInterface
  public interface OneParamVoidFunction<A> {
    public void apply(A a);
  }

  @FunctionalInterface
  public interface ThreeParamVoidFunction<A, B, C> {
    public void apply(A a, B b, C c);
  }

}
