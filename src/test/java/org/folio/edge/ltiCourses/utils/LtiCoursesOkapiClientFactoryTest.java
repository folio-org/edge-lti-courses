package org.folio.edge.ltiCourses.utils;

import static org.folio.edge.core.Constants.SYS_KEYSTORE_PASSWORD;
import static org.folio.edge.core.Constants.SYS_KEYSTORE_PATH;
import static org.folio.edge.core.Constants.SYS_KEYSTORE_PROVIDER;
import static org.folio.edge.core.Constants.SYS_KEYSTORE_TYPE;
import static org.folio.edge.core.Constants.SYS_KEY_ALIAS;
import static org.folio.edge.core.Constants.SYS_KEY_ALIAS_PASSWORD;
import static org.folio.edge.core.Constants.SYS_OKAPI_URL;
import static org.folio.edge.core.Constants.SYS_REQUEST_TIMEOUT_MS;
import static org.folio.edge.core.Constants.SYS_SSL_ENABLED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.KeyStoreOptions;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.folio.edge.core.utils.OkapiClient;
import org.folio.edge.core.utils.OkapiClientFactory;
import org.junit.Test;

public class LtiCoursesOkapiClientFactoryTest {
  private static final String OKAPI_URL = "http://mocked.okapi:9130";
  private static final Integer REQ_TIMEOUT_MS = 5000;
  private static final String KEYSTORE_TYPE = "some_keystore_type";
  private static final String KEYSTORE_PROVIDER = "some_keystore_provider";
  private static final String KEYSTORE_PATH = "some_keystore_path";
  private static final String KEYSTORE_PASSWORD = "some_keystore_password";
  private static final String KEY_ALIAS = "some_key_alias";
  private static final String KEY_ALIAS_PASSWORD = "some_key_alias_password";

  @Test
  public void testGetOkapiClientFactory() throws IllegalAccessException {
    Vertx vertx = Vertx.vertx();
    JsonObject config = new JsonObject()
      .put(SYS_OKAPI_URL, OKAPI_URL)
      .put(SYS_REQUEST_TIMEOUT_MS, REQ_TIMEOUT_MS)
      .put(SYS_SSL_ENABLED, false);
    OkapiClientFactory ocf = LtiCoursesOkapiClientFactory.createInstance(vertx, config);

    String okapiUrl = (String) FieldUtils.readDeclaredField(ocf, "okapiURL");
    Integer reqTimeoutMs = (Integer) FieldUtils.readDeclaredField(ocf, "reqTimeoutMs");
    KeyStoreOptions keyStoreOptions = (KeyStoreOptions) FieldUtils.readDeclaredField(ocf, "keyCertOptions", true);

    assertEquals(OKAPI_URL, okapiUrl);
    assertEquals(REQ_TIMEOUT_MS, reqTimeoutMs);
    assertNull(keyStoreOptions);
    OkapiClient client = ocf.getOkapiClient("tenant");
    assertNotNull(client);
  }

  @Test
  public void testGetSecuredOkapiClientFactory() throws IllegalAccessException {
    Vertx vertx = Vertx.vertx();
    JsonObject config = new JsonObject()
      .put(SYS_OKAPI_URL, OKAPI_URL)
      .put(SYS_REQUEST_TIMEOUT_MS, REQ_TIMEOUT_MS)
      .put(SYS_SSL_ENABLED, true)
      .put(SYS_KEYSTORE_TYPE, KEYSTORE_TYPE)
      .put(SYS_KEYSTORE_PROVIDER, KEYSTORE_PROVIDER)
      .put(SYS_KEYSTORE_PATH, KEYSTORE_PATH)
      .put(SYS_KEYSTORE_PASSWORD, KEYSTORE_PASSWORD)
      .put(SYS_KEY_ALIAS, KEY_ALIAS)
      .put(SYS_KEY_ALIAS_PASSWORD, KEY_ALIAS_PASSWORD);
    OkapiClientFactory ocf = LtiCoursesOkapiClientFactory.createInstance(vertx, config);

    String okapiUrl = (String) FieldUtils.readDeclaredField(ocf, "okapiURL");
    Integer reqTimeoutMs = (Integer) FieldUtils.readDeclaredField(ocf, "reqTimeoutMs");
    KeyStoreOptions keyStoreOptions = (KeyStoreOptions) FieldUtils.readDeclaredField(ocf, "keyCertOptions", true);

    assertEquals(OKAPI_URL, okapiUrl);
    assertEquals(REQ_TIMEOUT_MS, reqTimeoutMs);
    assertEquals(KEYSTORE_TYPE, keyStoreOptions.getType());
    assertEquals(KEYSTORE_PROVIDER, keyStoreOptions.getProvider());
    assertEquals(KEYSTORE_PATH, keyStoreOptions.getPath());
    assertEquals(KEYSTORE_PASSWORD, keyStoreOptions.getPassword());
    assertEquals(KEY_ALIAS, keyStoreOptions.getAlias());
    assertEquals(KEY_ALIAS_PASSWORD, keyStoreOptions.getAliasPassword());
    OkapiClient client = ocf.getOkapiClient("tenant");
    assertNotNull(client);
  }
}