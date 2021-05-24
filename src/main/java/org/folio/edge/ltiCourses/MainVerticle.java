package org.folio.edge.ltiCourses;

import static org.folio.edge.ltiCourses.Constants.BOX_API_APP_TOKEN;
import static org.folio.edge.ltiCourses.Constants.DOWNLOAD_URL_TTL;
import static org.folio.edge.ltiCourses.Constants.LTI_TOOL_PRIVATE_KEY_FILE;
import static org.folio.edge.ltiCourses.Constants.LTI_TOOL_PUBLIC_KEY_FILE;
import static org.folio.edge.ltiCourses.Constants.OIDC_TTL;
import static org.folio.edge.ltiCourses.Constants.IGNORE_OIDC_STATE;
import static org.folio.edge.ltiCourses.utils.PemUtils.readPrivateKeyFromFile;
import static org.folio.edge.ltiCourses.utils.PemUtils.readPublicKeyFromFile;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import org.apache.log4j.Logger;
import org.folio.edge.core.ApiKeyHelper;
import org.folio.edge.core.EdgeVerticle;
import org.folio.edge.ltiCourses.cache.BoxFileCache;
import org.folio.edge.ltiCourses.cache.OidcStateCache;
import org.folio.edge.ltiCourses.utils.LtiCoursesOkapiClientFactory;

import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.templ.jade.JadeTemplateEngine;

public class MainVerticle extends EdgeVerticle {

  private static final Logger logger = Logger.getLogger(MainVerticle.class);

  public MainVerticle() {
    super();
  }

  // We don't currently use the tool keys because this module only supports Resource Links at the moment which
  // are not signed. Future development to add Deep Linking (or other parts of the LTI spec) would require
  // keys so this has been kept in for now.
  final private KeyPair getToolKeyPair() {
    final String toolPrivateKeyFile = System.getProperty(LTI_TOOL_PRIVATE_KEY_FILE);
    final String toolPublicKeyFile = System.getProperty(LTI_TOOL_PUBLIC_KEY_FILE);

    if (toolPrivateKeyFile == null || toolPrivateKeyFile.isEmpty()) {
      // Generate our own keys
      try {
        logger.info("Generating our own LTI Tool RSA key pair");
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        return kpg.generateKeyPair();
      } catch (NoSuchAlgorithmException e) {
        logger.error("Couldn't find RSA algorithm to generate key pair");
        return null;
      }
    } else {
      // Use the keys stored locally.
      logger.info("Using LTI Tool Private Key File: " + toolPrivateKeyFile);

      try {
        // We only _really_ need a private key. The public key just lets us provide a JWKS endpoint.
        if (toolPublicKeyFile == null || toolPublicKeyFile.isEmpty()) {
          return new KeyPair(
            null,
            readPrivateKeyFromFile(toolPrivateKeyFile, "RSA")
          );
        } else {
          logger.info("Using LTI Tool Public Key File: " + toolPublicKeyFile);
          return new KeyPair(
            readPublicKeyFromFile(toolPublicKeyFile, "RSA"),
            readPrivateKeyFromFile(toolPrivateKeyFile, "RSA")
          );
        }
      } catch (Exception e) {
        logger.error("Failed to read tool key from file: " + e.getLocalizedMessage());
        return null;
      }
    }

  }

  @Override
  public Router defineRoutes() {
    OidcStateCache.initialize(
      Integer.valueOf(System.getProperty(OIDC_TTL, "10000")),
      Integer.valueOf(System.getProperty(OIDC_TTL, "10000")),
      10000
    );

    final KeyPair toolKeyPair = getToolKeyPair();

    // Init the Jade templating engine
    JadeTemplateEngine jadeTemplateEngine = JadeTemplateEngine.create(vertx);

    // Next, set up the common Edge module stuff.
    final LtiCoursesOkapiClientFactory ocf = new LtiCoursesOkapiClientFactory(
        vertx,
        okapiURL,
        reqTimeoutMs
    );

    final ApiKeyHelper apiKeyHelper = new ApiKeyHelper("PATH");

    final Boolean ignoreOIDCState = System.getProperty(IGNORE_OIDC_STATE, "false").equals("true");
    if (ignoreOIDCState == true) {
      logger.info("Ignoring OIDC state...this is UNSAFE and only intended for development!");
    }

    // Currently, we only support transparent downloads of files stored in Box.com. However,
    // `useInternalDownloadLinks` could check for other auth/config and then a different
    // handler could be wired up to the /lti-courses/download-file route. That's why
    // LtiCoursesHandler doesn't know about /Box/, it only knows about "internal download links."
    final String boxApiAppToken = System.getProperty(BOX_API_APP_TOKEN, "");
    final Boolean useInternalDownloadLinks = boxApiAppToken.isEmpty() == false;

    final LtiCoursesHandler ltiCoursesHandler = new LtiCoursesHandler(
      secureStore,
      ocf,
      apiKeyHelper,
      (RSAPrivateKey)toolKeyPair.getPrivate(),
      jadeTemplateEngine,
      useInternalDownloadLinks,
      ignoreOIDCState
    );

    final JwksHandler jwksHandler = new JwksHandler((RSAPublicKey)toolKeyPair.getPublic());

    // Finally, define our routes.
    final Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());
    router.route(HttpMethod.GET, "/admin/health").handler(this::handleHealthCheck);
    router.route(HttpMethod.GET, "/lti-courses/.well-known/jwks.json").handler(jwksHandler::handleGetJWKS);

    router.route(HttpMethod.GET, "/lti-courses/oidc-login-init/:apiKeyPath").handler(ltiCoursesHandler::handleOidcLoginInit);
    router.route(HttpMethod.POST, "/lti-courses/launches/:apiKeyPath").handler(ltiCoursesHandler::handleRequest);
    router.route(HttpMethod.POST, "/lti-courses/externalIdLaunches/:apiKeyPath").handler(ltiCoursesHandler::handleRequestCourseExternalId);
    router.route(HttpMethod.POST, "/lti-courses/registrarIdLaunches/:apiKeyPath").handler(ltiCoursesHandler::handleRequestCourseRegistrarId);

    // Set up optional Box API integration
    BoxFileCache.initialize(
      Integer.valueOf(System.getProperty(DOWNLOAD_URL_TTL, "600000")),  // 10 minutes
      Integer.valueOf(System.getProperty(DOWNLOAD_URL_TTL, "600000")),  // 10 minutes
      100000
    );

    final BoxDownloadHandler boxDownloadHandler = new BoxDownloadHandler(boxApiAppToken);
    router.route(HttpMethod.GET, "/lti-courses/download-file/:hash").handler(boxDownloadHandler::handleDownloadRequest);

    return router;
  }
}
