package org.folio.edge.ltiCourses.model;

import static org.junit.Assert.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.apache.log4j.Logger;
import org.junit.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class CourseTest {
  public static final JsonObject courseJson = new JsonObject()
    .put("id", "foo")
    .put("courseListingId", "bar")
    .put("courseListingObject", new JsonObject()
      .put("termObject", new JsonObject()
        .put("startDate", "2020-09-01")
        .put("endDate", "2020-11-30")
      )
    );

  public static final Clock augustClock = Clock.fixed(Instant.ofEpochSecond(1596387815), ZoneId.systemDefault()); // August 2, 2020
  public static final Clock octoberClock = Clock.fixed(Instant.ofEpochSecond(1601658215), ZoneId.systemDefault()); // October 2, 2020
  public static final Clock decemberClock = Clock.fixed(Instant.ofEpochSecond(1608051815), ZoneId.systemDefault()); // December 15, 2020

  public static final Logger logger = Logger.getLogger(CourseTest.class);

  @Test
  public void testCourseInit() {
    Course course = new Course(courseJson);
    JsonArray reserves = course.getCurrentReserves(octoberClock);

    assertEquals(0, reserves.size());
  }

  @Test
  public void testCourseWithNoReserves() {
    logger.info("=== Test current reserves calculation with no reserves  - Success ===");

    Course course = new Course(courseJson);

    course.setReserves("{\"reserves\":[],\"totalRecords\":0}");

    JsonArray reserves = course.getCurrentReserves(octoberClock);

    assertEquals(0, reserves.size());
  }

  @Test
  public void testCurrentCourseWithDatelessReserves() {
    logger.info("=== Test current reserves calculation with current course - Success ===");

    Course course = new Course(courseJson);

    String datelessReserve = "{"
    + "  \"reserves\" : [ {"
    + "    \"id\" : \"b9805a3c-d024-4883-9f4d-5059a7da218f\","
    + "    \"courseListingId\" : \"4c2a8ce9-f7d4-4f5e-a6d7-88bc7eb193fc\","
    + "    \"itemId\" : \"foobar\""
    + "  } ],"
    + "  \"totalRecords\" : 1"
    + "}";

    course.setReserves(datelessReserve);

    JsonArray reserves = course.getCurrentReserves(octoberClock);

    assertEquals(1, reserves.size());
    assertEquals("foobar", reserves.getJsonObject(0).getString("itemId"));
  }

  @Test
  public void testUnstartedCourseWithDatelessReserves() {
    logger.info("=== Test current reserves calculation with unstarted course - Success ===");

    Course course = new Course(courseJson);

    String datelessReserve = "{"
    + "  \"reserves\" : [ {"
    + "    \"id\" : \"b9805a3c-d024-4883-9f4d-5059a7da218f\","
    + "    \"courseListingId\" : \"4c2a8ce9-f7d4-4f5e-a6d7-88bc7eb193fc\","
    + "    \"itemId\" : \"foobar\""
    + "  } ],"
    + "  \"totalRecords\" : 1"
    + "}";

    course.setReserves(datelessReserve);

    JsonArray reserves = course.getCurrentReserves(augustClock);

    assertEquals(0, reserves.size());
  }

  @Test
  public void testEndedCourseWithDatelessReserves() {
    logger.info("=== Test current reserves calculation with ended course - Success ===");

    Course course = new Course(courseJson);

    String datelessReserve = "{"
    + "  \"reserves\" : [ {"
    + "    \"id\" : \"b9805a3c-d024-4883-9f4d-5059a7da218f\","
    + "    \"courseListingId\" : \"4c2a8ce9-f7d4-4f5e-a6d7-88bc7eb193fc\","
    + "    \"itemId\" : \"foobar\""
    + "  } ],"
    + "  \"totalRecords\" : 1"
    + "}";

    course.setReserves(datelessReserve);

    JsonArray reserves = course.getCurrentReserves(decemberClock);

    assertEquals(0, reserves.size());
  }

  @Test
  public void testCourseWithExpiredItemReserves() {
    logger.info("=== Test current reserves calculation with a reserve that is expired based on the reserve's overriding date  - Success ===");

    Course course = new Course(courseJson);

    String septemberOnlyReserve = "{"
    + "  \"reserves\" : [ {"
    + "    \"id\" : \"b9805a3c-d024-4883-9f4d-5059a7da218f\","
    + "    \"courseListingId\" : \"4c2a8ce9-f7d4-4f5e-a6d7-88bc7eb193fc\","
    + "    \"startDate\" : \"2020-09-01\","
    + "    \"endDate\" : \"2020-09-30\","
    + "    \"itemId\" : \"foobar\""
    + "  } ],"
    + "  \"totalRecords\" : 1"
    + "}";

    course.setReserves(septemberOnlyReserve);

    JsonArray reserves = course.getCurrentReserves(octoberClock);

    assertEquals(0, reserves.size());
  }

  @Test
  public void testCourseWithUnstartedItemReserves() {
    logger.info("=== Test current reserves calculation with a reserve that hasn't started based on the reserve's overriding date  - Success ===");

    Course course = new Course(courseJson);

    String novemberOnlyReserve = "{"
    + "  \"reserves\" : [ {"
    + "    \"id\" : \"b9805a3c-d024-4883-9f4d-5059a7da218f\","
    + "    \"courseListingId\" : \"4c2a8ce9-f7d4-4f5e-a6d7-88bc7eb193fc\","
    + "    \"startDate\" : \"2020-11-01\","
    + "    \"endDate\" : \"2020-11-30\","
    + "    \"itemId\" : \"foobar\""
    + "  } ],"
    + "  \"totalRecords\" : 1"
    + "}";

    course.setReserves(novemberOnlyReserve);

    JsonArray reserves = course.getCurrentReserves(octoberClock);

    assertEquals(0, reserves.size());
  }

  @Test
  public void testCourseWithCurrentItemReserves() {
    logger.info("=== Test current reserves calculation with a reserve that is valid based on the reserve's overriding date  - Success ===");

    Course course = new Course(courseJson);

    String octoberOnlyReserve = "{"
    + "  \"reserves\" : [ {"
    + "    \"id\" : \"b9805a3c-d024-4883-9f4d-5059a7da218f\","
    + "    \"courseListingId\" : \"4c2a8ce9-f7d4-4f5e-a6d7-88bc7eb193fc\","
    + "    \"startDate\" : \"2020-10-01\","
    + "    \"endDate\" : \"2020-10-30\","
    + "    \"itemId\" : \"foobar\""
    + "  } ],"
    + "  \"totalRecords\" : 1"
    + "}";

    course.setReserves(octoberOnlyReserve);

    JsonArray reserves = course.getCurrentReserves(octoberClock);

    assertEquals(1, reserves.size());
    assertEquals("foobar", reserves.getJsonObject(0).getString("itemId"));
  }

  @Test
  public void testCourseWithFirstDayOfValidityItemReserve() {
    logger.info("=== Test current reserves calculation with a reserve that is on its first day of validity based on the reserve's overriding date  - Success ===");

    Course course = new Course(courseJson);

    String octoberOnlyReserve = "{"
    + "  \"reserves\" : [ {"
    + "    \"id\" : \"b9805a3c-d024-4883-9f4d-5059a7da218f\","
    + "    \"courseListingId\" : \"4c2a8ce9-f7d4-4f5e-a6d7-88bc7eb193fc\","
    + "    \"startDate\" : \"2020-10-02\","
    + "    \"endDate\" : \"2020-10-30\","
    + "    \"itemId\" : \"foobar\""
    + "  } ],"
    + "  \"totalRecords\" : 1"
    + "}";

    course.setReserves(octoberOnlyReserve);

    JsonArray reserves = course.getCurrentReserves(octoberClock);

    assertEquals(1, reserves.size());
    assertEquals("foobar", reserves.getJsonObject(0).getString("itemId"));
  }

  @Test
  public void testCourseWithLastDayOfValidityItemReserve() {
    logger.info("=== Test current reserves calculation with a reserve that is on its last day of validity based on the reserve's overriding date  - Success ===");

    Course course = new Course(courseJson);

    String octoberOnlyReserve = "{"
    + "  \"reserves\" : [ {"
    + "    \"id\" : \"b9805a3c-d024-4883-9f4d-5059a7da218f\","
    + "    \"courseListingId\" : \"4c2a8ce9-f7d4-4f5e-a6d7-88bc7eb193fc\","
    + "    \"startDate\" : \"2020-10-02\","
    + "    \"endDate\" : \"2020-10-30\","
    + "    \"itemId\" : \"foobar\""
    + "  } ],"
    + "  \"totalRecords\" : 1"
    + "}";

    course.setReserves(octoberOnlyReserve);

    JsonArray reserves = course.getCurrentReserves(octoberClock);

    assertEquals(1, reserves.size());
    assertEquals("foobar", reserves.getJsonObject(0).getString("itemId"));
  }

  @Test
  public void testCourseWithLastDayBeforeValidityItemReserve() {
    logger.info("=== Test current reserves calculation with a reserve that is on its last day before it's valid based on the reserve's overriding date  - Success ===");

    Course course = new Course(courseJson);

    String octoberOnlyReserve = "{"
    + "  \"reserves\" : [ {"
    + "    \"id\" : \"b9805a3c-d024-4883-9f4d-5059a7da218f\","
    + "    \"courseListingId\" : \"4c2a8ce9-f7d4-4f5e-a6d7-88bc7eb193fc\","
    + "    \"startDate\" : \"2020-09-05\","
    + "    \"endDate\" : \"2020-10-01\","
    + "    \"itemId\" : \"foobar\""
    + "  } ],"
    + "  \"totalRecords\" : 1"
    + "}";

    course.setReserves(octoberOnlyReserve);

    JsonArray reserves = course.getCurrentReserves(octoberClock);

    assertEquals(0, reserves.size());
  }

  @Test
  public void testCourseWithFirstDayAfterValidityItemReserve() {
    logger.info("=== Test current reserves calculation with a reserve that is on its first day after it was valid based on the reserve's overriding date  - Success ===");

    Course course = new Course(courseJson);

    String octoberOnlyReserve = "{"
    + "  \"reserves\" : [ {"
    + "    \"id\" : \"b9805a3c-d024-4883-9f4d-5059a7da218f\","
    + "    \"courseListingId\" : \"4c2a8ce9-f7d4-4f5e-a6d7-88bc7eb193fc\","
    + "    \"startDate\" : \"2020-10-03\","
    + "    \"endDate\" : \"2020-10-11\","
    + "    \"itemId\" : \"foobar\""
    + "  } ],"
    + "  \"totalRecords\" : 1"
    + "}";

    course.setReserves(octoberOnlyReserve);

    JsonArray reserves = course.getCurrentReserves(octoberClock);

    assertEquals(0, reserves.size());
  }

  @Test
  public void testCourseWithSomeValidItems() {
    logger.info("=== Test current reserves calculation with reserves of mixed validity  - Success ===");

    Course course = new Course(courseJson);

    String mixedReserves = "{"
    + "  \"reserves\" : [ {"
    + "    \"id\" : \"explicitly-validd\","
    + "    \"courseListingId\" : \"4c2a8ce9-f7d4-4f5e-a6d7-88bc7eb193fc\","
    + "    \"startDate\" : \"2020-10-01\","
    + "    \"endDate\" : \"2020-10-30\","
    + "    \"itemId\" : \"12\""
    + "  }, { "
    + "    \"id\" : \"implicitly-valid-from-course\","
    + "    \"courseListingId\" : \"4c2a8ce9-f7d4-4f5e-a6d7-88bc7eb193fc\","
    + "    \"itemId\" : \"34\""
    + "  }, { "
    + "    \"id\" : \"invalid-expired\","
    + "    \"courseListingId\" : \"4c2a8ce9-f7d4-4f5e-a6d7-88bc7eb193fc\","
    + "    \"startDate\" : \"2020-09-01\","
    + "    \"endDate\" : \"2020-09-30\","
    + "    \"itemId\" : \"56\""
    + "  }, { "
    + "    \"id\" : \"invalid-not-yet-started\","
    + "    \"courseListingId\" : \"4c2a8ce9-f7d4-4f5e-a6d7-88bc7eb193fc\","
    + "    \"startDate\" : \"2020-11-01\","
    + "    \"endDate\" : \"2020-11-30\","
    + "    \"itemId\" : \"78\""
    + "  }],"
    + "  \"totalRecords\" : 1"
    + "}";

    course.setReserves(mixedReserves);

    JsonArray reserves = course.getCurrentReserves(octoberClock);

    assertEquals(2, reserves.size());
    assertEquals("12", reserves.getJsonObject(0).getString("itemId"));
    assertEquals("34", reserves.getJsonObject(1).getString("itemId"));
  }

  @Test
  public void testCurrentCourseWithSearchUrlAndBarcode() {
    logger.info("=== Test current reserves calculation with current course - Success ===");

    Course course = new Course(courseJson);

    String datelessReserve = "{"
    + "  \"reserves\" : [ {"
    + "    \"id\" : \"b9805a3c-d024-4883-9f4d-5059a7da218f\","
    + "    \"courseListingId\" : \"4c2a8ce9-f7d4-4f5e-a6d7-88bc7eb193fc\","
    + "    \"itemId\" : \"foobar\","
    + "    \"copiedItem\" : {"
    + "      \"barcode\" : \"raboof\""
    + "    }"
    + "  } ],"
    + "  \"totalRecords\" : 1"
    + "}";

    course.setSearchUrl("https://find.mylib.edu?q=[BARCODE]");
    course.setReserves(datelessReserve);

    JsonArray reserves = course.getCurrentReserves(octoberClock);

    assertEquals(1, reserves.size());
    assertEquals("https://find.mylib.edu?q=raboof", reserves.getJsonObject(0).getString("uri"));
  }
}