package org.folio.edge.ltiCourses.cache;

import org.apache.log4j.Logger;
import org.folio.edge.core.cache.Cache;
import org.folio.edge.core.cache.Cache.Builder;

import java.util.UUID;

public class BoxFileCache {

  private static final Logger logger = Logger.getLogger(BoxFileCache.class);

  private static BoxFileCache instance = null;

  private Cache<String> cache;

  private BoxFileCache(long ttl, long nullTokenTtl, int capacity) {
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
   * Get the BoxDownloadUrlCache singleton. the singleton must be initialized before
   * calling this method.
   *
   * @see {@link #initialize(long, int)}
   *
   * @return the BoxDownloadUrlCache singleton instance.
   */
  public static synchronized BoxFileCache getInstance() {
    if (instance == null) {
      throw new NotInitializedException(
          "You must call BoxDownloadUrlCache.initialize(ttl, capacity) before you can get the singleton instance");
    }
    return instance;
  }

  /**
   * Creates a new BoxDownloadUrlCache instance, replacing the existing one if it
   * already exists; in which case all pre-existing cache entries will be lost.
   *
   * @param ttl
   *          cache entry time to live in ms
   * @param capacity
   *          maximum number of entries this cache will hold before pruning
   * @return the new BoxDownloadUrlCache singleton instance
   */
  public static synchronized BoxFileCache initialize(long ttl, long nullValueTtl, int capacity) {
    if (instance != null) {
      logger.warn("Reinitializing cache.  All cached entries will be lost");
    }
    instance = new BoxFileCache(ttl, nullValueTtl, capacity);
    return instance;
  }

  public String get(String hash) {
    return cache.get(hash);
  }

  public String put(String fileId) {
    final String hash = UUID.randomUUID().toString();
    cache.put(hash, fileId);
    return hash;
  }

  public static class NotInitializedException extends RuntimeException {

    private static final long serialVersionUID = 4747532964596334577L;

    public NotInitializedException(String msg) {
      super(msg);
    }
  }

}