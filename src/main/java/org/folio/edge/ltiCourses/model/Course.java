package org.folio.edge.ltiCourses.model;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.time.Instant;
import java.time.Period;
import java.util.ArrayDeque;
import java.util.Iterator;

import org.apache.log4j.Logger;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class Course {
  public String courseListingId;

  protected String id;
  protected String searchUrl;
  protected Term term;
  protected ArrayDeque<Reserve> reserves;

  private static final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");

  private static final Logger logger = Logger.getLogger(Course.class);

  public Course (JsonObject json) {
    this.id = json.getString("id", "");
    this.courseListingId = json.getString("courseListingId", "");

    this.term = new Term(json
      .getJsonObject("courseListingObject", new JsonObject())
      .getJsonObject("termObject", new JsonObject())
    );
    this.reserves = new ArrayDeque<Reserve>();
  }

  public void setSearchUrl(String searchUrl) {
    this.searchUrl = searchUrl;
  }

  public void setReserves(String reservesString) {
    Iterator<Object> i;

    try {
      i = new JsonObject(reservesString).getJsonArray("reserves").iterator();
    } catch (Exception e) {
      logger.error("Failed to parse reserves string: " + reservesString);
      return;
    }

    while (i.hasNext()) {
      Reserve reserve = new Reserve(((JsonObject) i.next()));
      reserves.add(reserve);
    }
  }

  public JsonArray getCurrentReserves(Clock clock) {
    Instant now = Instant.now(clock);
    Instant termStart;
    Instant termEnd;

    JsonArray json = new JsonArray();

    try {
      termStart = format.parse(term.startDate).toInstant();
      termEnd = format.parse(term.endDate).toInstant();
    } catch (ParseException e) {
      logger.error("Failed to parse term dates: " + term.startDate + " & " + term.endDate);
      return json;
    }

    Iterator<Reserve> i = reserves.iterator();
    while (i.hasNext()) {
      Reserve reserve = i.next();

      String startDateString = term.startDate;
      String endDateString = term.endDate;
      Instant startDate = termStart;
      Instant endDate = termEnd;

      if (!(reserve.startDate.isEmpty())) {
        try {
          startDateString = reserve.startDate;
          startDate = format.parse(reserve.startDate).toInstant();
        } catch (ParseException e) {
          logger.error("Failed to parse reserve start date: " + reserve.startDate);
        }
      }

      if (!(reserve.endDate.isEmpty())) {
        try {
          endDateString = reserve.endDate;
          endDate = format.parse(reserve.endDate).toInstant();
        } catch (ParseException e) {
          logger.error("Failed to parse reserve end date: " + reserve.endDate);
        }
      }

      if (now.isAfter(startDate) && now.isBefore(endDate.plus(Period.ofDays(1)))) {
        JsonObject reserveJson = reserve.asJsonObject();

        reserveJson.put("startDate", startDateString);
        reserveJson.put("endDate", endDateString);

        if (reserve.uri.isEmpty()) {
          if (searchUrl != null && searchUrl.contains("[BARCODE]")) {
            reserveJson.put("uri", searchUrl.replace("[BARCODE]", reserve.barcode));
          } else if (searchUrl != null && searchUrl.contains("[INSTANCE_HRID]")) {
            reserveJson.put("uri", searchUrl.replace("[INSTANCE_HRID]", reserve.instanceHrid));
          } else {
            reserveJson.put("uri", searchUrl);
          }
        }

        json.add(reserveJson);
      }
    }

    return json;
  }

  public JsonArray getCurrentReserves() {
    // A term date is stored as the localised datetime, eg, 2020-09-01T04:00:00.000Z.
    // An item reserve date is stored as the date, eg, 2020-09-04.
    return getCurrentReserves(Clock.systemUTC());
  }
}