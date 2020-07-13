package org.folio.edge.ltiCourses.model;

import static org.junit.Assert.assertEquals;

import org.apache.log4j.Logger;
import org.junit.Test;

import io.vertx.core.json.JsonObject;

public class ReserveTest {
  public static final Logger logger = Logger.getLogger(ReserveTest.class);

  @Test
  public void testBarcode() {
    logger.info("=== Test parsing of Reserve with Barcode... ===");

    String reserveWithBarcode = "{"
    + "    \"itemId\" : \"100d10bf-2f06-4aa0-be15-0b95b2d9f9e3\","
    + "    \"copiedItem\" : {"
    + "      \"barcode\" : \"foobar\""
    + "    }"
    + "  }";

    Reserve reserve = new Reserve(new JsonObject(reserveWithBarcode));

    JsonObject reserveJson = reserve.asJsonObject();

    assertEquals("100d10bf-2f06-4aa0-be15-0b95b2d9f9e3", reserveJson.getString("itemId"));
    assertEquals("foobar", reserveJson.getString("barcode"));
  }

  @Test
  public void testScannedItem() {
    logger.info("=== Test parsing of Reserve with scanned item... ===");

    String reserveWithScannedItem = "{"
    + "    \"itemId\" : \"100d10bf-2f06-4aa0-be15-0b95b2d9f9e3\","
    + "    \"copiedItem\" : {"
    + "      \"barcode\" : \"90000\","
    + "      \"title\" : \"A semantic web primer\","
    + "      \"uri\" : \"http://www.loc.gov/catdir/toc/ecip0718/2007020429.html\""
    + "    }"
    + "  }";

    Reserve reserve = new Reserve(new JsonObject(reserveWithScannedItem));

    JsonObject reserveJson = reserve.asJsonObject();

    assertEquals("100d10bf-2f06-4aa0-be15-0b95b2d9f9e3", reserveJson.getString("itemId"));
    assertEquals("90000", reserveJson.getString("barcode"));
    assertEquals("A semantic web primer", reserveJson.getString("title"));
    assertEquals("http://www.loc.gov/catdir/toc/ecip0718/2007020429.html", reserveJson.getString("uri"));
  }

  @Test
  public void testPrimaryContributor() {
    logger.info("=== Test parsing of Reserve with Primary Contributor... ===");

    String reserveWithPrimaryContributor = "{"
    + "    \"itemId\" : \"100d10bf-2f06-4aa0-be15-0b95b2d9f9e3\","
    + "    \"copiedItem\" : {"
    + "      \"contributors\" : [ {"
    + "        \"name\" : \"Antoniou, Grigoris\","
    + "        \"contributorTypeId\" : \"6e09d47d-95e2-4d8a-831b-f777b8ef6d81\","
    + "        \"contributorTypeText\" : \"\","
    + "        \"contributorNameTypeId\" : \"2b94c631-fca9-4892-a730-03ee529ffe2a\","
    + "        \"primary\" : true"
    + "      }, {"
    + "        \"name\" : \"Van Harmelen, Frank\","
    + "        \"contributorTypeId\" : \"6e09d47d-95e2-4d8a-831b-f777b8ef6d81\","
    + "        \"contributorTypeText\" : \"\","
    + "        \"contributorNameTypeId\" : \"2b94c631-fca9-4892-a730-03ee529ffe2a\""
    + "      } ]"
    + "    }"
    + "  }";

    Reserve reserve = new Reserve(new JsonObject(reserveWithPrimaryContributor));

    JsonObject reserveJson = reserve.asJsonObject();

    assertEquals("Antoniou, Grigoris", reserveJson.getString("primaryContributor"));
  }

  @Test
  public void testPrimaryContributorFallback() {
    logger.info("=== Test parsing of Reserve with Primary Contributor Fallback... ===");

    String reserveWithPrimaryContributor = "{"
    + "    \"itemId\" : \"100d10bf-2f06-4aa0-be15-0b95b2d9f9e3\","
    + "    \"copiedItem\" : {"
    + "      \"contributors\" : [ {"
    + "        \"name\" : \"Van Harmelen, Frank\","
    + "        \"contributorTypeId\" : \"6e09d47d-95e2-4d8a-831b-f777b8ef6d81\","
    + "        \"contributorTypeText\" : \"\","
    + "        \"contributorNameTypeId\" : \"2b94c631-fca9-4892-a730-03ee529ffe2a\""
    + "      }, {"
    + "        \"name\" : \"Antoniou, Grigoris\","
    + "        \"contributorTypeId\" : \"6e09d47d-95e2-4d8a-831b-f777b8ef6d81\","
    + "        \"contributorTypeText\" : \"\","
    + "        \"contributorNameTypeId\" : \"2b94c631-fca9-4892-a730-03ee529ffe2a\""
    + "      } ]"
    + "    }"
    + "  }";

    Reserve reserve = new Reserve(new JsonObject(reserveWithPrimaryContributor));

    JsonObject reserveJson = reserve.asJsonObject();

    assertEquals("Van Harmelen, Frank", reserveJson.getString("primaryContributor"));
  }

  @Test
  public void testSkeleton() {
    logger.info("=== Test parsing of Reserve with just itemId... ===");

    String reserveWithDates = "{"
    + "    \"itemId\" : \"some-id\""
    + "  }";

    Reserve reserve = new Reserve(new JsonObject(reserveWithDates));

    JsonObject reserveJson = reserve.asJsonObject();

    assertEquals("some-id", reserveJson.getString("itemId"));
    assertEquals("", reserveJson.getString("barcode"));
    assertEquals("", reserveJson.getString("title"));
    assertEquals("", reserveJson.getString("uri"));
    assertEquals("", reserveJson.getString("startDate"));
    assertEquals("", reserveJson.getString("endDate"));
    assertEquals("", reserveJson.getString("primaryContributor"));
  }

  @Test
  public void testDates() {
    logger.info("=== Test parsing of Reserve with dates... ===");

    String reserveWithDates = "{"
    + "    \"itemId\" : \"some-id\","
    + "    \"startDate\" : \"2020-09-02\","
    + "    \"endDate\" : \"2020-12-30\""
    + "  }";

    Reserve reserve = new Reserve(new JsonObject(reserveWithDates));

    JsonObject reserveJson = reserve.asJsonObject();

    assertEquals("2020-09-02", reserveJson.getString("startDate"));
    assertEquals("2020-12-30", reserveJson.getString("endDate"));
  }
}
