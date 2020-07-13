package org.folio.edge.ltiCourses.model;

import static org.junit.Assert.assertEquals;

import org.apache.log4j.Logger;
import org.junit.Test;

import io.vertx.core.json.JsonObject;

public class TermTest {
  public static final Logger logger = Logger.getLogger(TermTest.class);

  @Test
  public void testDatetimes() {

    logger.info("=== Test parsing of Term with datetime strings... ===");

    JsonObject json = new JsonObject()
      .put("startDate", "2020-09-02T04:00:00.000Z")
      .put("endDate", "2020-12-30T-04:00:00.000Z");

    Term reserve = new Term(json);

    JsonObject reserveJson = reserve.asJsonObject();

    assertEquals("2020-09-02", reserveJson.getString("startDate"));
    assertEquals("2020-12-29", reserveJson.getString("endDate"));
  }

  @Test
  public void testDates() {

    logger.info("=== Test parsing of Term with date strings... ===");

    JsonObject json = new JsonObject()
      .put("startDate", "2020-09-02")
      .put("endDate", "2020-12-30");

    Term reserve = new Term(json);

    JsonObject reserveJson = reserve.asJsonObject();

    assertEquals("2020-09-02", reserveJson.getString("startDate"));
    assertEquals("2020-12-30", reserveJson.getString("endDate"));
  }

  @Test
  public void testInvalidDate() {

    logger.info("=== Test parsing of Term with unknown datetime formats... ===");

    JsonObject json = new JsonObject()
      .put("startDate", "2020-09-02Txzyfoobar")
      .put("endDate", "2020-12-30Txzyfoobar");

    Term reserve = new Term(json);

    JsonObject reserveJson = reserve.asJsonObject();

    assertEquals("", reserveJson.getString("startDate"));
    assertEquals("", reserveJson.getString("endDate"));
  }

  @Test
  public void testEmptyDate() {

    logger.info("=== Test parsing of Term String with empty end date... ===");

    JsonObject json = new JsonObject()
      .put("startDate", "2020-09-02T04:00:00.000Z")
      .put("endDate", "");

    Term reserve = new Term(json);

    JsonObject reserveJson = reserve.asJsonObject();

    assertEquals("2020-09-02", reserveJson.getString("startDate"));
    assertEquals("", reserveJson.getString("endDate"));
  }
}