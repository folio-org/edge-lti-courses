package org.folio.edge.ltiCourses.store;

import java.util.LinkedHashMap;

import org.apache.log4j.Logger;
import org.folio.edge.core.cache.Cache;
import org.folio.edge.core.cache.Cache.Builder;
import org.folio.edge.core.cache.Cache.CacheValue;
import org.folio.edge.ltiCourses.utils.LtiCoursesOkapiClientFactory;
import org.folio.edge.ltiCourses.utils.LtiPlatform;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;

public class LtiPlatformStore {

  private static final Logger logger = Logger.getLogger(LtiPlatformStore.class);


  private static LtiPlatformStore instance = null;

  private HttpClient httpClient;
  private LtiPlatform platform;

  private LtiPlatformStore(LtiCoursesOkapiClientFactory ocf) {
    // ocf.getOkapiClient(tenant);
  }

  /**
   * Get the LtiPlatform singleton. the singleton must be initialized before
   * calling this method.
   *
   * @see {@link #initialize(long, int)}
   *
   * @return the LtiPlatformStore singleton instance.
   */
  public static LtiPlatform getPlatform() {
    if (instance == null) {
      throw new NotInitializedException(
          "You must call LtiPlatformStore.initialize(ocf) before you can get the instance");
    }
    return instance.platform;
  }

  /**
   * Creates a new LtiPlatformStore instance, replacing the existing one if it
   * already exists; in which case all pre-existing cache entries will be lost.
   *
   * @return the new LtiPlatformStore singleton instance
   */
  public static LtiPlatformStore initialize(LtiCoursesOkapiClientFactory ocf) {
    if (instance != null) {
      logger.warn("Reinitializing store.  All stored entries will be lost");
    }
    instance = new LtiPlatformStore(ocf);
    return instance;
  }


  public static class NotInitializedException extends RuntimeException {

    private static final long serialVersionUID = 4747532964596334577L;

    public NotInitializedException(String msg) {
      super(msg);
    }
  }

}