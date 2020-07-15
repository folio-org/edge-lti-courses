package org.folio.edge.ltiCourses;

import static org.folio.edge.core.Constants.SYS_LOG_LEVEL;
import static org.folio.edge.core.Constants.SYS_OKAPI_URL;
import static org.folio.edge.core.Constants.SYS_PORT;
import static org.folio.edge.core.Constants.SYS_REQUEST_TIMEOUT_MS;
import static org.folio.edge.core.Constants.SYS_SECURE_STORE_PROP_FILE;
import static org.folio.edge.core.Constants.TEXT_PLAIN;
import static org.folio.edge.core.Constants.APPLICATION_JSON;
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

import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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
import org.folio.edge.ltiCourses.utils.LtiCoursesMockOkapi;

@RunWith(VertxUnitRunner.class)
public class MainVerticleTest {

  private static final Logger logger = Logger.getLogger(MainVerticleTest.class);

  private static final String titleId = "0c8e8ac5-6bcc-461e-a8d3-4b55a96addc8";
  private static final String apiKey = ApiKeyUtils.generateApiKey(10, "tester", "tester");
  private static final String badApiKey = apiKey + "0000";
  private static final String unknownTenantApiKey = ApiKeyUtils.generateApiKey(10, "bogus", "tester");;

  private static final long requestTimeoutMs = 3000L;

  private static Vertx vertx;
  private static LtiCoursesMockOkapi mockOkapi;

  @BeforeClass
  public static void setUpOnce(TestContext context) throws Exception {
    int okapiPort = TestUtils.getPort();
    int serverPort = TestUtils.getPort();

    List<String> knownTenants = new ArrayList<>();
    knownTenants.add(ApiKeyUtils.parseApiKey(apiKey).tenantId);

    mockOkapi = spy(new LtiCoursesMockOkapi(okapiPort, knownTenants));
    mockOkapi.start(context);

    vertx = Vertx.vertx();

    System.setProperty(SYS_PORT, String.valueOf(serverPort));
    System.setProperty(SYS_OKAPI_URL, "http://localhost:" + okapiPort);
    System.setProperty(SYS_SECURE_STORE_PROP_FILE, "src/main/resources/ephemeral.properties");
    System.setProperty(SYS_REQUEST_TIMEOUT_MS, String.valueOf(requestTimeoutMs));

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
        logger.error("Failed to shut down edge-rtac server", res.cause());
        fail(res.cause().getMessage());
      } else {
        logger.info("Successfully shut down edge-rtac server");
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
    logger.info("=== Test OIDC Login Initiation via GET with a bad api key ===");

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
  public void testOidcLoginInitGoodAPIKey(TestContext context) {
    logger.info("=== Test OIDC Login Initiation via GET ===");

    final Response response = RestAssured
      .given()
        .redirects().follow(false)
        .param("iss", MockLtiPlatform.issuer)
        .param("login_hint", "foobar")
        .param("target_link_uri", "http://redirect.edu/to-here")
      .when()
        .get("/lti-courses/oidc-login-init/" + apiKey)
        .then()
        .statusCode(302)
        .extract()
        .response();


    assertThat(response.header("location"), containsString(MockLtiPlatform.oidcAuthUrl));
    URI uri;
    try {
      uri = new URI(response.header("location"));
    } catch (Exception e) {
      logger.error("Failed to parse \"location\" header in response");
      return;
    }

    List<NameValuePair> paramList = URLEncodedUtils.parse(uri.getQuery(), Charset.forName("UTF-8"));
    HashMap<String, String> params = new HashMap<String, String>();
    paramList.forEach(param -> { params.put(param.getName(), param.getValue()); });

    assertEquals(MockLtiPlatform.clientId, params.get("client_id"));
    assertEquals("foobar", params.get("login_hint"));
    assertEquals("none", params.get("prompt"));
    assertEquals("http://redirect.edu/to-here", params.get("redirect_uri"));
    assertEquals("form_post", params.get("response_mode"));
    assertEquals("id_token", params.get("response_type"));
    assertEquals("openid", params.get("scope"));
  }

}