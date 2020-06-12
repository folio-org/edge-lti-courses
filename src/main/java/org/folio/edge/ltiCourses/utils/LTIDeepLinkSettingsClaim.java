package org.folio.edge.ltiCourses.utils;

public class LTIDeepLinkSettingsClaim {
  private String[] accept_types;
  private String accept_media_types;
  private String[] accept_presentation_document_targets;
  private Boolean accept_multiple;
  private Boolean auto_create;
  private String title;
  private String text;
  private String data;
  private String deep_link_return_url;

  public LTIDeepLinkSettingsClaim() {};

  public LTIDeepLinkSettingsClaim(
    String[] accept_types,
    String accept_media_types,
    String[] accept_presentation_document_targets,
    Boolean accept_multiple,
    Boolean auto_create,
    String title,
    String text,
    String data,
    String deep_link_return_url
  ) {
    this.accept_types = accept_types;
    this.accept_media_types = accept_media_types;
    this.accept_presentation_document_targets = accept_presentation_document_targets;
    this.accept_multiple = accept_multiple;
    this.auto_create = auto_create;
    this.title = title;
    this.text = text;
    this.data = data;
    this.deep_link_return_url = deep_link_return_url;
  }

  public String[] getAccept_types() { return accept_types; }
  public String getAccept_media_types() { return accept_media_types; }
  public String[] getAccept_presentation_document_targets() { return accept_presentation_document_targets; }
  public Boolean getAccept_multiple() { return accept_multiple; }
  public Boolean getAuto_create() { return auto_create; }
  public String getTitle() { return title; }
  public String getText() { return text; }
  public String getData() { return data; }
  public String getDeep_link_return_url() { return deep_link_return_url; }

}