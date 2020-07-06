package org.folio.edge.ltiCourses.model;

import org.apache.log4j.Logger;
import org.folio.edge.ltiCourses.utils.DateUtils;

import io.vertx.core.json.JsonObject;

public class Term {
  public String startDate;
  public String endDate;

  public static final Logger logger = Logger.getLogger(Term.class);

  Term(JsonObject json) {
    // Normalize our dates to just the YMD like is stored on the Reserve.
    // We won't need this when https://issues.folio.org/browse/UICR-94 is resolved.
    this.startDate = DateUtils.normalizeDate(json.getString("startDate", ""));
    this.endDate = DateUtils.normalizeDate(json.getString("endDate", ""));
  }

  public JsonObject asJsonObject() {
    return new JsonObject()
      .put("startDate", startDate)
      .put("endDate", endDate);
  }
}