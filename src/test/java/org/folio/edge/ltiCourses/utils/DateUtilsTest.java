package org.folio.edge.ltiCourses.utils;

import org.apache.log4j.Logger;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class DateUtilsTest {
  public static final Logger logger = Logger.getLogger(DateUtilsTest.class);

  @Test
  public void testYMDDate() {
    logger.info("=== Test pass-through of YMD-formatted date... ===");
    assertEquals("2020-10-29", DateUtils.normalizeDate("2020-10-29"));
  }

  @Test
  public void testDateTime() {
    logger.info("=== Test stripping of time from datetime strings... ===");
    assertEquals("2020-10-29", DateUtils.normalizeDate("2020-10-29T12:00:00.123Z"));
  }
}
