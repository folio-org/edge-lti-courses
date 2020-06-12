package org.folio.edge.ltiCourses;

import org.apache.log4j.Logger;

import static org.folio.edge.ltiCourses.utils.PemUtils.readPrivateKeyFromFile;
import static org.folio.edge.ltiCourses.utils.PemUtils.readPublicKeyFromFile;
import static org.folio.edge.ltiCourses.Constants.LTI_PLATFORM_PUBLIC_KEY_FILE;
import static org.folio.edge.ltiCourses.Constants.LTI_TOOL_PRIVATE_KEY_FILE;
import static org.folio.edge.ltiCourses.Constants.LTI_TOOL_PUBLIC_KEY_FILE;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;

import org.folio.edge.core.ApiKeyHelper;
import org.folio.edge.core.EdgeVerticle;
import org.folio.edge.ltiCourses.utils.LtiCoursesOkapiClientFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

public class MainVerticle extends EdgeVerticle {

  private static final Logger logger = Logger.getLogger(MainVerticle.class);

  public MainVerticle() {
    super();
  }

  @Override
  public Router defineRoutes() {
    // First, set up the JWT algorithm by collecting our keys and initing.
    final String platformPublicKeyFile = System.getProperty(LTI_PLATFORM_PUBLIC_KEY_FILE);
    final String toolPrivateKeyFile = System.getProperty(LTI_TOOL_PRIVATE_KEY_FILE);
    final String toolPublicKeyFile = System.getProperty(LTI_TOOL_PUBLIC_KEY_FILE);

    logger.info("Using LTI Platform Public Key File: " + platformPublicKeyFile);
    logger.info("Using LTI Tool Private Key File: " + toolPrivateKeyFile);
    logger.info("Using LTI Tool Public Key File: " + toolPublicKeyFile);

    final RSAPublicKey platformPublicKey;
    try {
      platformPublicKey = (RSAPublicKey) readPublicKeyFromFile(platformPublicKeyFile, "RSA");
    } catch (Exception e) {
      logger.error("Failed to read platform public key from file.");
      return null;
    }

    final RSAPrivateKey toolPrivateKey;
    try {
      toolPrivateKey = (RSAPrivateKey) readPrivateKeyFromFile(toolPrivateKeyFile, "RSA");
    } catch (Exception e) {
      logger.error("Failed to read tool private key from file.");
      return null;
    }


    final Algorithm algorithm = Algorithm.RSA256(platformPublicKey, toolPrivateKey);

    JWTVerifier jwtVerifier = JWT.require(algorithm)
    // .withIssuer("https://lti-ri.imsglobal.org")
    .build();

    // Also save off the public key so we can send it if requested.
    String toolPublicKey = "";
    try {
      toolPublicKey = new String(Files.readAllBytes(Paths.get(toolPublicKeyFile)));
    } catch (IOException e) {
      logger.error("Failed to read tool public key from file");
    }

    // Next, set up the common Edge module stuff.
    final LtiCoursesOkapiClientFactory ocf = new LtiCoursesOkapiClientFactory(
        vertx,
        okapiURL,
        reqTimeoutMs
    );

    final ApiKeyHelper apiKeyHelper = new ApiKeyHelper("PARAM");
    final LtiCoursesHandler ltiCoursesHandler = new LtiCoursesHandler(
      secureStore,
      ocf,
      apiKeyHelper,
      jwtVerifier,
      toolPublicKey
    );

    final Router router = Router.router(vertx);

    // Finally, define our routes.
    router.route().handler(BodyHandler.create());
    router.route(HttpMethod.GET, "/admin/health").handler(this::handleHealthCheck);

    // Takes an LTI DeepLinkRequest containing a course ID and returns embeddable HTML
    // that contains an endpoint for fetching the reserves for that course.
    router.route(HttpMethod.POST, "/lti-courses/deep-link-request").handler(ltiCoursesHandler::handleDeepLinkRequest);

    // The endpoint returned in the LTI DeepLinkResponse.
    router.route(HttpMethod.GET, "/lti-courses/reserves/:courseId").handler(ltiCoursesHandler::handleGetReservesById);

    router.route(HttpMethod.GET, "/lti-courses/public-key").handler(ltiCoursesHandler::handleGetPublicKey);
    return router;
  }
}
