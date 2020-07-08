package org.folio.edge.ltiCourses.model;

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
    + "      \"configName\": \"issuer\","
    + "      \"enabled\": true,"
    + "      \"code\": \"https://my-lms.com\","
    + "      \"value\": \" { \\\"clientId\\\": \\\"12345\\\", \\\"issuer\\\": \\\"https://my-lms.com\\\", \\\"jwksUrl\\\": \\\"https://my-lms.com/.well_known/jwks\\\", \\\"oidcAuthUrl\\\": \\\"https://my-lms.com/oidc_auth\\\", \\\"searchUrl\\\": \\\"https://find.mylibrary.edu?q=[BARCODE]\\\" } \","
    + "      \"metadata\": {"
    + "        \"createdDate\": \"2020-07-01T14:33:30.834+0000\","
    + "        \"createdByUserId\": \"609870dc-331d-4554-90b5-27993dedeb29\","
    + "        \"updatedDate\": \"2020-07-01T14:33:30.834+0000\","
    + "        \"updatedByUserId\": \"609870dc-331d-4554-90b5-27993dedeb29\""
    + "      }"
    + "    }"
    + "  ],"
    + "  \"totalRecords\": 1,"
    + "  \"resultInfo\": {"
    + "    \"totalRecords\": 1,"
    + "    \"facets\": [],"
    + "    \"diagnostics\": []"
    + "  }"
    + "}";

    JsonObject conf = new JsonObject(validConfiguration);
    LtiPlatform platform = new LtiPlatform(conf);

    assertEquals("https://my-lms.com", platform.issuer);
    assertEquals("https://my-lms.com/.well_known/jwks", platform.jwksUrl);
    assertEquals("https://my-lms.com/oidc_auth", platform.oidcAuthUrl);
    assertEquals("https://find.mylibrary.edu?q=[BARCODE]", platform.searchUrl);
    assertEquals("12345", platform.clientId);
  }
}