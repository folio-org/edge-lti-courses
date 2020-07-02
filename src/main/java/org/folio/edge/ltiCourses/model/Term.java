package org.folio.edge.ltiCourses.model;

import java.text.SimpleDateFormat;

import org.apache.log4j.Logger;

import io.vertx.core.json.JsonObject;

public class Term {
  public String startDate;
  public String endDate;

  private static SimpleDateFormat sourceFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
  private static SimpleDateFormat storageFormat = new SimpleDateFormat("yyyy-MM-dd");

  public static final Logger logger = Logger.getLogger(Term.class);

  Term(JsonObject json) {
    // Normalize our dates to just the YMD like is stored on the Reserve.
    // We won't need this when https://issues.folio.org/browse/UICR-94 is resolved.
    this.startDate = normalizeDate(json.getString("startDate", ""));
    this.endDate = normalizeDate(json.getString("endDate", ""));
  }

  public JsonObject asJsonObject() {
    return new JsonObject()
      .put("startDate", startDate)
      .put("endDate", endDate);
  }

  private String normalizeDate(String date) {
    if (date.isEmpty()) {
      return date;
    }

    if (date.contains("T")) {
      try {
        return storageFormat.format(sourceFormat.parse(date));
      } catch (Exception e) {
        return "";
      }
    }

    return date;
  }
}