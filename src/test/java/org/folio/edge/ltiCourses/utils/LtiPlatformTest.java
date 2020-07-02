package org.folio.edge.ltiCourses.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Base64;

import org.apache.log4j.Logger;
import org.junit.Test;

import io.vertx.core.json.JsonObject;

public class LtiPlatformTest {
  public static final Logger logger = Logger.getLogger(LtiPlatformTest.class);

  @Test
  public void testConfigurationParsing() {
    logger.info("=== Test Parsing of Configuration - Success ===");

    String validConfiguration = "{"
    + "  \"configs\": ["
    + "    {"
    + "      \"id\": \"ffa404ff-7ed9-48ac-9c41-d02733e21ea6\","
    + "      \"module\": \"EDGELTICOURSES\","
    + "      \"configName\": \"platformIssuer\","
    + "      \"enabled\": true,"
    + "      \"value\": \"https://my-lms.com\","
    + "      \"metadata\": {"
    + "        \"createdDate\": \"2020-07-01T14:33:30.834+0000\","
    + "        \"createdByUserId\": \"609870dc-331d-4554-90b5-27993dedeb29\","
    + "        \"updatedDate\": \"2020-07-01T14:33:30.834+0000\","
    + "        \"updatedByUserId\": \"609870dc-331d-4554-90b5-27993dedeb29\""
    + "      }"
    + "    },"
    + "    {"
    + "      \"id\": \"a5a9c188-389a-409b-b039-991071de06e0\","
    + "      \"module\": \"EDGELTICOURSES\","
    + "      \"configName\": \"platformJwksUrl\","
    + "      \"enabled\": true,"
    + "      \"value\": \"https://my-lms.com/.well_known/jwks\","
    + "      \"metadata\": {"
    + "        \"createdDate\": \"2020-07-01T14:37:42.316+0000\","
    + "        \"createdByUserId\": \"609870dc-331d-4554-90b5-27993dedeb29\","
    + "        \"updatedDate\": \"2020-07-01T14:37:42.316+0000\","
    + "        \"updatedByUserId\": \"609870dc-331d-4554-90b5-27993dedeb29\""
    + "      }"
    + "    },"
    + "    {"
    + "      \"id\": \"6ca38571-b7ae-47e4-b8d7-104099aef1f8\","
    + "      \"module\": \"EDGELTICOURSES\","
    + "      \"configName\": \"platformOauthTokenUrl\","
    + "      \"enabled\": true,"
    + "      \"value\": \"https://my-lms.com/token\","
    + "      \"metadata\": {"
    + "        \"createdDate\": \"2020-07-01T14:38:03.280+0000\","
    + "        \"createdByUserId\": \"609870dc-331d-4554-90b5-27993dedeb29\","
    + "        \"updatedDate\": \"2020-07-01T14:38:03.280+0000\","
    + "        \"updatedByUserId\": \"609870dc-331d-4554-90b5-27993dedeb29\""
    + "      }"
    + "    },"
    + "    {"
    + "      \"id\": \"fc1f71af-d587-442b-9aef-5e62267e051d\","
    + "      \"module\": \"EDGELTICOURSES\","
    + "      \"configName\": \"platformOidcAuthUrl\","
    + "      \"enabled\": true,"
    + "      \"value\": \"https://my-lms.com/oidc_auth\","
    + "      \"metadata\": {"
    + "        \"createdDate\": \"2020-07-01T14:38:04.978+0000\","
    + "        \"createdByUserId\": \"609870dc-331d-4554-90b5-27993dedeb29\","
    + "        \"updatedDate\": \"2020-07-01T14:38:04.978+0000\","
    + "        \"updatedByUserId\": \"609870dc-331d-4554-90b5-27993dedeb29\""
    + "      }"
    + "    },"
    + "    {"
    + "      \"id\": \"5dfdbf7a-f66f-4e30-b20d-2656fdfde845\","
    + "      \"module\": \"EDGELTICOURSES\","
    + "      \"configName\": \"platformClientId\","
    + "      \"enabled\": true,"
    + "      \"value\": \"12345\","
    + "      \"metadata\": {"
    + "        \"createdDate\": \"2020-07-01T14:38:06.009+0000\","
    + "        \"createdByUserId\": \"609870dc-331d-4554-90b5-27993dedeb29\","
    + "        \"updatedDate\": \"2020-07-01T14:38:06.009+0000\","
    + "        \"updatedByUserId\": \"609870dc-331d-4554-90b5-27993dedeb29\""
    + "      }"
    + "    }"
    + "  ],"
    + "  \"totalRecords\": 5,"
    + "  \"resultInfo\": {"
    + "    \"totalRecords\": 5,"
    + "    \"facets\": [],"
    + "    \"diagnostics\": []"
    + "  }"
    + "}";

    JsonObject conf = new JsonObject(validConfiguration);
    LtiPlatform platform = new LtiPlatform(conf);

    assertEquals("https://my-lms.com", platform.issuer);
    assertEquals("https://my-lms.com/.well_known/jwks", platform.jwksUrl);
    assertEquals("https://my-lms.com/token", platform.oauthTokenUrl);
    assertEquals("https://my-lms.com/oidc_auth", platform.oidcAuthUrl);
    assertEquals("12345", platform.clientId);
  }
}