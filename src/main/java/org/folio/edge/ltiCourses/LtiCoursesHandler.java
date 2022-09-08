package org.folio.edge.ltiCourses;

import java.net.URL;
import java.net.URLEncoder;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.folio.edge.core.ApiKeyHelper;
import org.folio.edge.core.security.SecureStore;
import org.folio.edge.ltiCourses.cache.OidcStateCache;
import org.folio.edge.ltiCourses.model.Course;
import org.folio.edge.ltiCourses.model.LtiPlatform;
import org.folio.edge.ltiCourses.utils.LtiContextClaim;
import org.folio.edge.ltiCourses.utils.LtiCoursesOkapiClient;
import org.folio.edge.ltiCourses.utils.LtiCoursesOkapiClientFactory;

import static org.folio.edge.ltiCourses.Constants.LTI_MESSAGE_TYPE_RESOURCE_LINK_REQUEST;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.*;
import com.auth0.jwt.interfaces.DecodedJWT;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.templ.jade.JadeTemplateEngine;

public class LtiCoursesHandler extends org.folio.edge.core.Handler {
  protected RSAPrivateKey privateKey;
  protected JadeTemplateEngine jadeTemplateEngine;
  protected String toolPublicKey;
  protected Boolean ignoreOIDCState;

  private static final Logger logger = LogManager.getLogger(LtiCoursesHandler.class);

  public LtiCoursesHandler(
    SecureStore secureStore,
    LtiCoursesOkapiClientFactory ocf,
    ApiKeyHelper apiKeyHelper,
    RSAPrivateKey privateKey,
    JadeTemplateEngine jadeTemplateEngine,
    Boolean ignoreOIDCState
  ) {
    super(secureStore, ocf, apiKeyHelper);

    this.privateKey = privateKey;
    this.jadeTemplateEngine = jadeTemplateEngine;
    this.ignoreOIDCState = ignoreOIDCState;
  }

  protected void handleCommonLTI(
    RoutingContext ctx,
    String[] requiredParams,
    String[] optionalParams,
    String issuer,
    ThreeParamVoidFunction<LtiCoursesOkapiClient, Map<String, String>, LtiPlatform> action
  ) {
    handleCommon(
      ctx,
      requiredParams,
      optionalParams,
      (client, params) -> {
        if (issuer == null || issuer.isEmpty()) {
          renderBadRequest(ctx, "Issuer not provided");
          return;
        }

        LtiCoursesOkapiClient coursesOkapiClient = (LtiCoursesOkapiClient) client;
        coursesOkapiClient.getPlatform(
          issuer,
          response -> {
            if (response.statusCode() != 200) {
              logger.error(response.statusCode() + ": " + response.statusMessage());
              ctx.response().setStatusCode(response.statusCode()).end(response.statusMessage());
              return;
            }

            LtiPlatform platform = new LtiPlatform(response.bodyAsJsonObject());
            if (!platform.issuer.equals(issuer)) {
              renderBadRequest(ctx, "No LTI Platform is known for this request's issuer.");
              return;
            }

            ctx.put("platform", platform);

            action.apply(
              coursesOkapiClient,
              params,
              platform
            );
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
    LtiPlatform platform,
    String courseIdType,
    OneParamVoidFunction<Course> action
  ) {
    LtiContextClaim contextClaim = jwt.getClaim("https://purl.imsglobal.org/spec/lti/claim/context").as(LtiContextClaim.class);
    String courseTitle = contextClaim.title;
    logger.info("Class: " + contextClaim.title);

    String query = "";
    try {
      // We're constructing a query string to look like: query=(courseNumber="CAL101")
      query = "query=%28" + courseIdType + "%3D%22" + URLEncoder.encode(courseTitle, "UTF-8") + "%22%29";
    } catch (Exception exception) {
      renderBadRequest(ctx, "Failed to encode requested course title of " + courseTitle);
      return;
    }

    client.getCourse(query, courseResp -> {
      if (courseResp.statusCode() != 200) {
        internalServerError(ctx, "Folio had an internal server error: " + courseResp.statusCode());
        return;
      }

      JsonObject courseJson;
      try {
        courseJson = courseResp.bodyAsJsonObject()
          .getJsonArray("courses")
          .getJsonObject(0);
      } catch (Exception exception) {
        renderNoReserves(ctx);
        return;
      }

      Course course;
      try {
        course = new Course(courseJson);
      } catch (Exception exception) {
        logger.error("Failed to parse course from JsonObject: " + courseJson.encode());
        renderNoReserves(ctx);
        return;
      }

      course.setSearchUrl(platform.searchUrl);
      if (platform.boxDirectDownload) {
        course.enableBoxDirectDownload();
      }

      client.getCourseReserves(
        course.courseListingId,
        reservesResp -> {
          if (reservesResp.statusCode() != 200) {
            renderBadRequest(ctx, reservesResp.statusMessage());
            return;
          }

          course.setReserves(reservesResp.bodyAsString());
          action.apply(course);
        },
        t -> handleProxyException(ctx, t)
      );
    }, t -> handleProxyException(ctx, t));
  }

  protected void handleLaunch(RoutingContext ctx, String courseIdType) {
    String id_token = ctx.request().formAttributes().get("id_token");
    if (id_token == null || id_token.isEmpty()) {
      renderBadRequest(ctx, "id_token is required and was not found");
      return;
    }

    final DecodedJWT jwt = JWT.decode(id_token);

    handleCommonLTI(
      ctx,
      new String[] {},
      new String[] {},
      jwt.getIssuer(),
      (client, params, platform) -> {
        // Fetch the JWK so we can validate it.
        RSAPublicKey platformPublicKey;
        try {
          JwkProvider jwkProvider = new UrlJwkProvider(new URL(platform.jwksUrl));
          Jwk jwk = jwkProvider.get(jwt.getKeyId());
          platformPublicKey = (RSAPublicKey) jwk.getPublicKey();
        } catch (Exception e) {
          renderBadRequest(ctx, "Failed to fetch Platform's JWKS: " + e.getLocalizedMessage());
          return;
        }

        // Validate the JWT
        Algorithm algorithm = Algorithm.RSA256(platformPublicKey, null);
        try {
          JWTVerifier verifier = JWT.require(algorithm)
            .withIssuer(platform.issuer)
            .withAudience(platform.clientId)
            .build();

          verifier.verify(jwt);
        } catch (AlgorithmMismatchException e) {
          renderBadRequest(ctx, "The JWT was signed with an invalid algorithm");
          return;
        } catch (SignatureVerificationException e) {
          renderBadRequest(ctx, "The JWT was signed with a key that doesn't correspond to the LTI Platform's public key");
          return;
        } catch (TokenExpiredException e) {
          renderBadRequest(ctx, "The JWT has expired");
          return;
        } catch (InvalidClaimException e) {
          renderBadRequest(ctx, "The JWT contains invalid claims");
          return;
        } catch (JWTVerificationException e) {
          renderBadRequest(ctx, "The JWT failed verification");
          return;
        }

        if (ignoreOIDCState != true) {
          String nonce = jwt.getClaim("nonce").asString();
          if (nonce == null || nonce.isEmpty()) {
            renderBadRequest(ctx, "Nonce is missing from request");
            return;
          }

          String memorizedState = OidcStateCache.getInstance().get(nonce);
          String state = ctx.request().formAttributes().get("state");
          if (memorizedState == null || !memorizedState.equals(state)) {
            logger.error("Got new state of: " + state + " but expected: " + memorizedState);
            renderBadRequest(ctx, "Nonce is invalid, states do not match");
            return;
          }
        }

        getCourse(ctx, client, jwt, platform, courseIdType,
          course -> {
            String message_type = jwt.getClaim("https://purl.imsglobal.org/spec/lti/claim/message_type").asString();
            if (message_type.equals(LTI_MESSAGE_TYPE_RESOURCE_LINK_REQUEST)) {
              renderResourceLink(ctx, jwt, course);
            } else {
              renderBadRequest(ctx, "Invalid message_type claim: " + message_type);
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
      ctx.request().params().get("iss"),
      (client, params, platform) -> {
        String login_hint = params.get("login_hint");
        String lti_message_hint = params.get("lti_message_hint");
        String target_link_uri = params.get("target_link_uri");

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
    handleLaunch(ctx, "courseNumber");
  }

  protected void handleRequestCourseExternalId(RoutingContext ctx) {
    handleLaunch(ctx, "courseListing.externalId");
  }

  protected void handleRequestCourseRegistrarId(RoutingContext ctx) {
    handleLaunch(ctx, "courseListing.registrarId");
  }

  protected void renderResourceLink(RoutingContext ctx, DecodedJWT jwt, Course course) {
    JsonArray reserves = course.getCurrentReserves();
    if (reserves.size() == 0) {
      renderNoReserves(ctx);
      return;
    }

    JsonObject model = new JsonObject()
      .put("reserves", reserves)
      .put("platform", ((LtiPlatform) ctx.get("platform")).asJsonObject());

    jadeTemplateEngine.render(model, "templates/ResourceLinkResponse", html -> {
      if (!html.succeeded()) {
        loggedInternalServerError(ctx, "Failed to render resource link: " + html.cause());
        return;
      }

      htmlResponse(ctx, html.result().toString());
    });
  }

  protected void renderNoReserves(RoutingContext ctx) {
    JsonObject model = new JsonObject()
      .put("platform", ((LtiPlatform) ctx.get("platform")).asJsonObject());

    jadeTemplateEngine.render(model, "templates/NoReserves", html -> {
      if (!html.succeeded()) {
        loggedInternalServerError(ctx, "Failed to render resource link: " + html.cause());
        return;
      }

      htmlResponse(ctx, html.result().toString());
    });
  }

  protected void renderBadRequest(RoutingContext ctx, String msg) {
    JsonObject model = new JsonObject().put("error", msg);

    LtiPlatform platform = ctx.get("platform");
    if (platform != null) {
      model.put("platform", platform.asJsonObject());
    }

    logger.error(msg);

    jadeTemplateEngine.render(model, "templates/Error", html -> {
      if (html.failed()) {
        logger.error("Failed to render Error template: " + html.cause());
        badRequest(ctx, msg);
        return;
      }

      htmlResponse(ctx, html.result().toString(), 400);
    });
  }

  protected void loggedInternalServerError(RoutingContext ctx, String msg) {
    logger.error(msg);
    internalServerError(ctx, msg);
  }

  protected void htmlResponse(RoutingContext ctx, String html) {
    htmlResponse(ctx, html, 200);
  }

  protected void htmlResponse(RoutingContext ctx, String html, int statusCode) {
    ctx.response()
      .setStatusCode(statusCode)
      .putHeader("content-type", "text/html;charset=UTF-8")
      .end(html);
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
