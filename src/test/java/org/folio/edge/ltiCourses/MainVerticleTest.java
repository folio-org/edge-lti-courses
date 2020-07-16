package org.folio.edge.ltiCourses;

import static org.folio.edge.core.Constants.SYS_LOG_LEVEL;
import static org.folio.edge.core.Constants.SYS_OKAPI_URL;
import static org.folio.edge.core.Constants.SYS_PORT;
import static org.folio.edge.core.Constants.SYS_REQUEST_TIMEOUT_MS;
import static org.folio.edge.core.Constants.SYS_SECURE_STORE_PROP_FILE;
import static org.folio.edge.core.Constants.TEXT_PLAIN;
import static org.folio.edge.core.Constants.APPLICATION_JSON;
import static org.folio.edge.ltiCourses.Constants.JWT_KID;
import static org.folio.edge.ltiCourses.Constants.OIDC_TTL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.spy;
import static org.hamcrest.CoreMatchers.containsString;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileReader;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.auth0.jwk.JwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.response.Response;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import org.apache.http.HttpHeaders;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.log4j.Logger;

import org.folio.edge.core.utils.ApiKeyUtils;
import org.folio.edge.core.utils.test.TestUtils;
import org.folio.edge.ltiCourses.model.LtiPlatform;
import org.folio.edge.ltiCourses.utils.LtiCoursesMockOkapi;
import org.folio.edge.ltiCourses.utils.MockLtiPlatformServer;

@RunWith(VertxUnitRunner.class)
public class MainVerticleTest {

  private static final Logger logger = Logger.getLogger(MainVerticleTest.class);

  private static final String titleId = "0c8e8ac5-6bcc-461e-a8d3-4b55a96addc8";
  private static final String apiKey = ApiKeyUtils.generateApiKey(10, "tester", "tester");
  private static final String badApiKey = apiKey + "0000";
  private static final String unknownTenantApiKey = ApiKeyUtils.generateApiKey(10, "bogus", "tester");;

  private static final long requestTimeoutMs = 3000L;

  private static int serverPort;

  private static Vertx vertx;
  private static LtiCoursesMockOkapi mockOkapi;
  private static MockLtiPlatformServer mockLtiPlatformServer;

  private static MockLtiPlatform platform;

  @BeforeClass
  public static void setUpOnce(TestContext context) throws Exception {
    serverPort = TestUtils.getPort();

    int okapiPort = TestUtils.getPort();
    int platformPort = TestUtils.getPort();

    List<String> knownTenants = new ArrayList<>();
    knownTenants.add(ApiKeyUtils.parseApiKey(apiKey).tenantId);

    mockOkapi = spy(new LtiCoursesMockOkapi(okapiPort, knownTenants));
    mockOkapi.start(context);

    mockLtiPlatformServer = spy(new MockLtiPlatformServer(platformPort));
    mockLtiPlatformServer.start(context);

    platform = MockLtiPlatform.initialize(platformPort);

    vertx = Vertx.vertx();

    System.setProperty(SYS_PORT, String.valueOf(serverPort));
    System.setProperty(SYS_OKAPI_URL, "http://localhost:" + okapiPort);
    System.setProperty(SYS_SECURE_STORE_PROP_FILE, "src/main/resources/ephemeral.properties");
    System.setProperty(SYS_REQUEST_TIMEOUT_MS, String.valueOf(requestTimeoutMs));
    System.setProperty(OIDC_TTL, "500");

    final DeploymentOptions opt = new DeploymentOptions();
    vertx.deployVerticle(MainVerticle.class.getName(), opt, context.asyncAssertSuccess());

    RestAssured.baseURI = "http://localhost:" + serverPort;
    RestAssured.port = serverPort;
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
  }

  @AfterClass
  public static void tearDownOnce(TestContext context) {
    logger.info("Shutting down server");
    final Async async = context.async();
    vertx.close(res -> {
      if (res.failed()) {
        logger.error("Failed to shut down edge-lti-courses server", res.cause());
        fail(res.cause().getMessage());
      } else {
        logger.info("Successfully shut down edge-lti-courses server");
      }

      logger.info("Shutting down mock Okapi");
      mockOkapi.close(context);
      async.complete();
    });
  }

  @Test
  public void testAdminHealth(TestContext context) {
    logger.info("=== Test the health check endpoint... ===");

    final Response resp = RestAssured
      .get("/admin/health")
    .then()
      .contentType(TEXT_PLAIN)
      .statusCode(200)
      .header(HttpHeaders.CONTENT_TYPE, TEXT_PLAIN)
      .extract()
      .response();

    assertEquals("\"OK\"", resp.body().asString());
  }

  @Test
  public void testJWKSEndpoint(TestContext context) {
    logger.info("=== Test the JWKS endpoint... ===");

    final Response resp = RestAssured
      .get("/lti-courses/.well-known/jwks.json")
    .then()
      .contentType(APPLICATION_JSON)
      .statusCode(200)
      .header(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
      .extract()
      .response();

    JsonObject jwks = new JsonObject(resp.asString());
    JsonArray keys = jwks.getJsonArray("keys");
    assertNotNull(keys);

    JsonObject key = keys.getJsonObject(0);
    assertNotNull(key);

    assertNotNull(key.getString("kid"));
    assertNotNull(key.getString("kty"));
    assertNotNull(key.getString("n"));
    assertNotNull(key.getString("e"));
    assertNotNull(key.getString("alg"));
    assertEquals("sig", key.getString("use"));
  }

  @Test
  public void testOidcLoginInitBadAPIKey(TestContext context) {
    logger.info("=== Test GET OIDC Login Initiation with a bad api key... ===");

    RestAssured
      .given()
        .param("iss", "http://test-iss.edu")
        .param("login_hint", "foobar")
        .param("target_link_uri", "http://redirect.edu/to-here")
      .when()
        .get("/lti-courses/oidc-login-init/" + badApiKey)
      .then()
        .contentType(TEXT_PLAIN)
        .statusCode(401)
        .header(HttpHeaders.CONTENT_TYPE, TEXT_PLAIN)
        .extract()
        .response();
  }

  @Test
  public void testOidcLoginInitUnknownPlatform(TestContext context) {
    logger.info("=== Test GET OIDC Login Initiation with unknown LTI Platform... ===");

    RestAssured
      .given()
        .redirects().follow(false)
        .param("iss", "http://made-up-unsecure-issuer.biz/")
        .param("login_hint", "foobar")
        .param("target_link_uri", "http://redirect.edu/to-here")
      .when()
        .get("/lti-courses/oidc-login-init/" + apiKey)
      .then()
        .statusCode(400)
        .extract()
        .response();
  }

  @Test
  public void testOidcLoginInitGoodAPIKey(TestContext context) {
    logger.info("=== Test GET OIDC Login Initiation... ===");

    final Response response = RestAssured
      .given()
        .redirects().follow(false)
        .param("iss", platform.issuer)
        .param("login_hint", "foobar")
        .param("target_link_uri", "http://redirect.edu/to-here")
      .when()
        .get("/lti-courses/oidc-login-init/" + apiKey)
      .then()
        .statusCode(302)
        .extract()
        .response();


    assertThat(response.header("location"), containsString(platform.oidcAuthUrl));

    HashMap<String, String> params = getOIDCLoginQueryParams(response.header("location"));

    assertEquals(platform.clientId, params.get("client_id"));
    assertEquals("foobar", params.get("login_hint"));
    assertEquals("none", params.get("prompt"));
    assertEquals("http://redirect.edu/to-here", params.get("redirect_uri"));
    assertEquals("form_post", params.get("response_mode"));
    assertEquals("id_token", params.get("response_type"));
    assertEquals("openid", params.get("scope"));
  }

  @Test
  public void testMockPlatformReturnsJWKS(TestContext context) {
    logger.info("=== Test mock platform server handles requests for JWKS... ===");

    Response resp = RestAssured
      .given()
        .baseUri(platform.issuer)
      .when()
        .get("/jwks.json")
      .then()
        .statusCode(200)
        .contentType(APPLICATION_JSON)
        .extract()
        .response();

    JsonObject jwks = new JsonObject(resp.asString());
    JsonArray keys = jwks.getJsonArray("keys");
    assertNotNull(keys);

    JsonObject key = keys.getJsonObject(0);
    assertNotNull(key);

    assertNotNull(key.getString("kid"));
    assertNotNull(key.getString("kty"));
    assertNotNull(key.getString("n"));
    assertNotNull(key.getString("e"));
    assertNotNull(key.getString("alg"));
    assertEquals("sig", key.getString("use"));
  }

  @Test
  public void testResourceLinkRequestForCourseWithReserves(TestContext context) {
    logger.info("=== Test Resource Link requests for course with reserves... ===");

    final Response oidcResponse = performOIDCLoginInit();
    HashMap<String, String> oidcResponseParams = getOIDCLoginQueryParams(oidcResponse.header("location"));

    String nonce = oidcResponseParams.get("nonce");
    String state = oidcResponseParams.get("state");

    String id_token = getResourceLinkJWT(mockOkapi.existingCourseId, nonce);

    Response response = RestAssured
      .given()
        .formParam("id_token", id_token)
        .formParam("state", state)
      .when()
        .post("/lti-courses/launches/" + apiKey)
      .then()
        .statusCode(200)
        .contentType("text/html;charset=UTF-8")
        .extract()
        .response();

    String body = response.getBody().asString();
    assertEquals(true, body.contains("lti-course-reserves-list"));
    assertEquals(true, body.contains("</script>"));
    assertEquals(false, body.contains(platform.noReservesMessage));
  }

  @Test
  public void testResourceLinkRequestForCourseNotFound(TestContext context) {
    logger.info("=== Test Resource Link requests for nonexisting courses... ===");

    final Response oidcResponse = performOIDCLoginInit();
    HashMap<String, String> oidcResponseParams = getOIDCLoginQueryParams(oidcResponse.header("location"));

    String nonce = oidcResponseParams.get("nonce");
    String state = oidcResponseParams.get("state");

    String id_token = getResourceLinkJWT("XYZ101", nonce);

    Response response = RestAssured
      .given()
        .formParam("id_token", id_token)
        .formParam("state", state)
      .when()
        .post("/lti-courses/launches/" + apiKey)
      .then()
        .statusCode(200)
        .contentType("text/html;charset=UTF-8")
        .extract()
        .response();

    String body = response.getBody().asString();
    assertEquals(false, body.contains("lti-course-reserves-list"));
    assertEquals(false, body.contains("</script>"));
    assertEquals(true, body.contains(platform.noReservesMessage));
  }

  @Test
  public void testJWTSignedWithInvalidKey(TestContext context) {
    logger.info("=== Test Resource Link requests for that are signed with a different key than the configured JWKS... ===");

    final Response oidcResponse = performOIDCLoginInit();
    HashMap<String, String> oidcResponseParams = getOIDCLoginQueryParams(oidcResponse.header("location"));

    String nonce = oidcResponseParams.get("nonce");
    String state = oidcResponseParams.get("state");

    String id_token = getIncorrectlySignedResourceLinkJWT(mockOkapi.existingCourseId, nonce);

    RestAssured
      .given()
        .formParam("id_token", id_token)
        .formParam("state", state)
      .when()
        .post("/lti-courses/launches/" + apiKey)
      .then()
        .statusCode(400)
        .extract()
        .response();
  }

  @Test
  public void testResourceLinkRequestWithBadNonce(TestContext context) {
    logger.info("=== Test Resource Link requests with an incorrect nonce... ===");

    final Response oidcResponse = performOIDCLoginInit();
    HashMap<String, String> oidcResponseParams = getOIDCLoginQueryParams(oidcResponse.header("location"));

    String nonce = oidcResponseParams.get("nonce");
    String state = oidcResponseParams.get("state");

    String id_token = getResourceLinkJWT("XYZ101", nonce + "foo");

    RestAssured
      .given()
        .formParam("id_token", id_token)
        .formParam("state", state)
      .when()
        .post("/lti-courses/launches/" + apiKey)
      .then()
        .statusCode(400)
        .extract()
        .response();
  }

  @Test
  public void testResourceLinkRequestWithReplayedRequest(TestContext context) {
    logger.info("=== Test Resource Link requests that have an expired nonce... ===");

    final Response oidcResponse = performOIDCLoginInit();
    HashMap<String, String> oidcResponseParams = getOIDCLoginQueryParams(oidcResponse.header("location"));

    String nonce = oidcResponseParams.get("nonce");
    String state = oidcResponseParams.get("state");
    String id_token = getResourceLinkJWT("XYZ101", nonce);

    try {
      TimeUnit.SECONDS.sleep(1);
    } catch (Exception e) {
      logger.error("Was interrupted while sleeping!");
      return;
    }

    RestAssured
      .given()
        .formParam("id_token", id_token)
        .formParam("state", state)
      .when()
        .post("/lti-courses/launches/" + apiKey)
      .then()
        .statusCode(400)
        .extract()
        .response();
  }

  @Test
  public void testResourceLinkRequestWithExpiredJWT(TestContext context) {
    logger.info("=== Test Resource Link requests that have an expired JWT... ===");

    final Response oidcResponse = performOIDCLoginInit();
    HashMap<String, String> oidcResponseParams = getOIDCLoginQueryParams(oidcResponse.header("location"));

    String nonce = oidcResponseParams.get("nonce");
    String state = oidcResponseParams.get("state");

    String id_token = getUnsignedResourceLinkJWT("XYZ101", nonce)
      .withIssuedAt(Date.from(Instant.now().minusSeconds(120)))
      .withExpiresAt(Date.from(Instant.now().minusSeconds(60)))
      .sign(mockLtiPlatformServer.algorithm);

    logger.info(id_token);

    RestAssured
      .given()
        .formParam("id_token", id_token)
        .formParam("state", state)
      .when()
        .post("/lti-courses/launches/" + apiKey)
      .then()
        .statusCode(400)
        .extract()
        .response();
  }

  @Test
  public void testResourceLinkRequestWithIncorrectState(TestContext context) {
    logger.info("=== Test Resource Link requests with an incorrect state... ===");

    final Response oidcResponse = performOIDCLoginInit();
    HashMap<String, String> oidcResponseParams = getOIDCLoginQueryParams(oidcResponse.header("location"));

    String nonce = oidcResponseParams.get("nonce");
    String state = oidcResponseParams.get("state") + "foo";
    String id_token = getResourceLinkJWT("XYZ101", nonce);

    RestAssured
      .given()
        .formParam("id_token", id_token)
        .formParam("state", state)
      .when()
        .post("/lti-courses/launches/" + apiKey)
      .then()
        .statusCode(400)
        .extract()
        .response();
  }

  private Response performOIDCLoginInit() {
    return RestAssured
      .given()
        .redirects().follow(false)
        .param("iss", platform.issuer)
        .param("login_hint", "foobar")
        .param("target_link_uri", "/lti-courses/launches/" + apiKey)
      .when()
        .get("/lti-courses/oidc-login-init/" + apiKey)
      .then()
        .statusCode(302)
        .extract()
        .response();
  }

  private HashMap<String, String> getOIDCLoginQueryParams(String uriString) {
    URI uri;
    try {
      uri = new URI(uriString);
    } catch (Exception e) {
      logger.error("Failed to parse \"location\" header in response");
      return null;
    }

    List<NameValuePair> paramList = URLEncodedUtils.parse(uri.getQuery(), Charset.forName("UTF-8"));
    HashMap<String, String> params = new HashMap<String, String>();
    paramList.forEach(param -> { params.put(param.getName(), param.getValue()); });

    return params;
  }

  private static String getResourceLinkJWT(String courseTitle, String nonce) {
    return getUnsignedResourceLinkJWT(courseTitle, nonce)
      .sign(mockLtiPlatformServer.algorithm);
  }

  private static String getIncorrectlySignedResourceLinkJWT(String courseTitle, String nonce) {
    return getUnsignedResourceLinkJWT(courseTitle, nonce)
      .sign(mockLtiPlatformServer.invalidAlgorithm);
  }

  private static JWTCreator.Builder getUnsignedResourceLinkJWT(String courseTitle, String nonce) {
    HashMap<String,String> contextClaim = new HashMap<String,String>();
    contextClaim.put("title", courseTitle);

    return JWT.create()
      .withIssuer(platform.issuer)
      .withKeyId(JWT_KID)

      .withAudience(platform.clientId)
      .withIssuedAt(Date.from(Instant.now()))
      .withExpiresAt(Date.from(Instant.now().plusSeconds(300)))

      .withClaim("nonce", nonce)
      .withClaim("https://purl.imsglobal.org/spec/lti/claim/message_type", "LtiResourceLinkRequest")
      .withClaim("https://purl.imsglobal.org/spec/lti/claim/version", "1.3.0")
      .withClaim("https://purl.imsglobal.org/spec/lti/claim/context", contextClaim);
  }

}