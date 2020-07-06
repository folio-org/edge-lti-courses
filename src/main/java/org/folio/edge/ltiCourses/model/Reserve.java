package org.folio.edge.ltiCourses.model;

import java.util.Iterator;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import org.folio.edge.ltiCourses.utils.DateUtils;

public class Reserve {
  public String itemId;
  public String barcode;
  public String title;
  public String uri;
  public String primaryContributor;
  public String startDate;
  public String endDate;

  public Reserve(JsonObject json) {
    this.itemId = json.getString("itemId", "");

    // Normalize our dates to just the YMD like is stored on the Reserve.
    // We won't need this when https://issues.folio.org/browse/UICR-94 is resolved.
    this.startDate = DateUtils.normalizeDate(json.getString("startDate", ""));
    this.endDate = DateUtils.normalizeDate(json.getString("endDate", ""));

    JsonObject item = json.getJsonObject("copiedItem", new JsonObject());
    this.barcode = item.getString("barcode", "");
    this.title = item.getString("title", "");
    this.uri = item.getString("uri", "");

    Iterator<Object> i = item
      .getJsonArray("contributors", new JsonArray())
      .iterator();

    if (i.hasNext()) {
      // Initialize to the first contributor in case _none_ are marked as primary.
      this.primaryContributor = item
        .getJsonArray("contributors")
        .getJsonObject(0)
        .getString("name");
    } else {
      this.primaryContributor = "";
    }

    // Save the contributor who's been marked as primary.
    while (i.hasNext()) {
      JsonObject contributor = ((JsonObject) i.next());
      if (contributor.getBoolean("primary", false)) {
        this.primaryContributor = contributor.getString("name");
      }
    }
  }

  public JsonObject asJsonObject() {
    return new JsonObject()
      .put("itemId", itemId)
      .put("barcode", barcode)
      .put("title", title)
      .put("uri", uri)
      .put("startDate", startDate)
      .put("endDate", endDate)
      .put("primaryContributor", primaryContributor);
  }
}