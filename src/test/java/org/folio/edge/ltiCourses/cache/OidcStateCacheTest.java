package org.folio.edge.ltiCourses.cache;

import org.apache.log4j.Logger;

import static org.junit.Assert.assertEquals;
import org.junit.Test;


public class OidcStateCacheTest {

  public static final Logger logger = Logger.getLogger(OidcStateCacheTest.class);

  @Test
  public void testGetInstance() {
    OidcStateCache.initialize(5000, 5000, 10);

    OidcStateCache cache = OidcStateCache.getInstance();
    assertEquals(cache.getClass(), OidcStateCache.class);
  }

  @Test
  public void testPutGet() {
    OidcStateCache.initialize(5000, 5000, 10);

    OidcStateCache.getInstance().put("foo", "bar");
    assertEquals("bar", OidcStateCache.getInstance().get("foo"));
  }
}