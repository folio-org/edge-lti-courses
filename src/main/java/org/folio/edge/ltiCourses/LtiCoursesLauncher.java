package org.folio.edge.ltiCourses;

import io.vertx.core.Launcher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;

import java.security.Security;

public class LtiCoursesLauncher extends Launcher {
  private static final Logger logger = LogManager.getLogger(LtiCoursesLauncher.class);

  public static void main(String[] args) {
    Security.addProvider(new BouncyCastleFipsProvider());
    logger.info("BouncyCastleFipsProvider has been added");
    new LtiCoursesLauncher().dispatch(args);
  }
}
