package org.folio.edge.ltiCourses.cache;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.folio.edge.core.cache.Cache;
import org.folio.edge.core.cache.Cache.Builder;
import org.folio.edge.core.cache.Cache.CacheValue;

public class OidcStateCache {

  private static final Logger logger = LogManager.getLogger(OidcStateCache.class);

  private static OidcStateCache instance = null;

  private Cache<String> cache;

  private OidcStateCache(long ttl, long nullTokenTtl, int capacity) {
    logger.info("Using TTL: " + ttl);
    logger.info("Using null token TTL: " + nullTokenTtl);
    logger.info("Using capacity: " + capacity);
    cache = new Builder<String>()
      .withTTL(ttl)
      .withNullValueTTL(nullTokenTtl)
      .withCapacity(capacity)
      .build();
  }

  /**
   * Get the OidcStateCache singleton. the singleton must be initialized before
   * calling this method.
   *
   * @see {@link #initialize(long, int)}
   *
   * @return the OidcStateCache singleton instance.
   */
  public static synchronized OidcStateCache getInstance() {
    if (instance == null) {
      throw new NotInitializedException(
          "You must call OidcStateCache.initialize(ttl, capacity) before you can get the singleton instance");
    }
    return instance;
  }

  /**
   * Creates a new OidcStateCache instance, replacing the existing one if it
   * already exists; in which case all pre-existing cache entries will be lost.
   *
   * @param ttl
   *          cache entry time to live in ms
   * @param capacity
   *          maximum number of entries this cache will hold before pruning
   * @return the new OidcStateCache singleton instance
   */
  public static synchronized OidcStateCache initialize(long ttl, long nullValueTtl, int capacity) {
    if (instance != null) {
      logger.warn("Reinitializing cache.  All cached entries will be lost");
    }
    instance = new OidcStateCache(ttl, nullValueTtl, capacity);
    return instance;
  }

  public String get(String nonce) {
    return cache.get(nonce);
  }

  public CacheValue<String> put(String nonce, String state) {
    return cache.put(nonce, state);
  }

  public static class NotInitializedException extends RuntimeException {

    private static final long serialVersionUID = 4747532964596334577L;

    public NotInitializedException(String msg) {
      super(msg);
    }
  }

}