package org.folio.edge.ltiCourses.utils;

import java.text.SimpleDateFormat;

public class DateUtils {
  private static SimpleDateFormat sourceFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
  private static SimpleDateFormat storageFormat = new SimpleDateFormat("yyyy-MM-dd");

  public static String normalizeDate(String date) {
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