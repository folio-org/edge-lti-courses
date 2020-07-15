package org.folio.edge.ltiCourses;

public class Constants {
  private Constants() {}

  public static final String LTI_TOOL_PRIVATE_KEY_FILE = "lti_tool_private_key_file";
  public static final String LTI_TOOL_PUBLIC_KEY_FILE = "lti_tool_public_key_file";

  public static final String LTI_PLATFORM_PUBLIC_KEY = "lti_platform_public_key";

  public static final String JWT_KID = "folio_lti_courses";
  public static final String OIDC_TTL = "oidc_ttl";

  public static final String DEFAULT_RESERVES_NOT_FOUND_MESSAGE = "No course reserve materials are currently available. If you believe this is an error, please contact your librarian for assistance.";

  public static final String LTI_MESSAGE_TYPE_DEEP_LINK_REQUEST = "LtiDeepLinkingRequest";
  public static final String LTI_MESSAGE_TYPE_RESOURCE_LINK_REQUEST = "LtiResourceLinkRequest";
}