package org.folio.edge.ltiCourses.utils;

public class LTIContextClaim {
  private String id;
  private String label;
  private String title;
  private String[] type;

  public LTIContextClaim() {};

  public LTIContextClaim(String id, String label, String title, String[] type) {
    this.id = id;
    this.label = label;
    this.title = title;
    this.type = type;
  }

  public String getId() {
    return id;
  }

  public String getLabel() {
    return label;
  }

  public String getTitle() {
    return title;
  }

  public String[] getType() {
    return type;
  }
}