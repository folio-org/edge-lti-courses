# edge-lti-courses

Copyright (C) 2018-2023 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Overview

The purpose of this edge module is to connect LMS such as Sakai and Blackboard (LTI Platforms) to Folio via the [LTI Advantage](https://www.imsglobal.org/lti-advantage-overview) protocol for the purposes of sharing the course reserves stored in Folio.

To accomplish this, this module acts as an LTI Tool Provider. It adheres to two parts of the LTI Advantage spec to accomplish this. First, security is enabled via a [third-party-initiated OIDC Authentication flow](https://www.imsglobal.org/spec/security/v1p0/#platform-originating-messages) (this is a generic ). Second, the actual course reserves are requested and shown via messages using [the Resource Link spec](https://www.imsglobal.org/spec/lti/v1p3/#resource-link-launch-request-message).

The general use flow is as follows:

1. Upon navigation to the tool's page on an LMS such as Sakai (the LTI platform), the platform sends an OIDC login initiation request to the tool at a predefined URL.
1. This request is handled in `LtiCoursesHandler::handleOidcLoginInit`. The request is parsed and validated.
1. If the request is valid, it responds with an HTTP 302 Redirect to a preconfigured URL for that platform.
1. The platform handles that redirect and bounces a request back to the tool to the tool launch endpoint with the LTI request itself.
1. The tool parses the LTI request which contains info about which course is being viewed in the LMS. Based on that, it fetches the course and its reserves from mod-courses, renders them in HTML, and sends back the HTML.
1. The platform takes the response and displays it in an iframe.

## Configuration

Configuration is done in two ways: system properties/args/secure stores, and LTI Platform configuration.

### Arguments / System Properties

Values common to Folio Edge modules are configured using the methods made available in [edge-common](https://github.com/folio-org/edge-common). These handle things like the `port`, `okapi_url`, or `secure_store_props`. E.g., you can run the module as `java -jar edge-lti-courses-fat.jar -Dokapi_url=https://okapi.folio.my-university.com ...`. Besides the common values defined in `edge-common`, this module also uses the following system properties.

| Name                        | Description                                                                                                                                                                                                                                                                                                                                            | Default Value |
|-----------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------|
| `lti_tool_private_key_file` | File path to a PEM file containing an RSA-encoded (PKCS8) private key. The key will be used by the Edge module when signing responses. This is needed if your LTI platform creates a private key for each tool. This edge module will generate its own keys if this property is not defined.                                                           |               |
| `lti_tool_public_key_file`  | File path to a PEM file containing an RSA-encoded (X509) private key. The key will be used by the Edge module when signing responses. This is needed if your LTI platform creates a private key for each tool. This edge module will generate its own keys if this property is not defined.                                                            |               |
| `oidc_ttl`                  | TTL in ms of the OIDC `state` cache. I.e., how long the OIDC auth handshake can take.                                                                                                                                                                                                                                                                  | `10000`       |
| `ignore_oidc_state`         | Never use this in production!!! This allows you to debug requests by sending requests to the `/launches` endpoints directly rather than negotiating an OIDC handshake. This is inherently unsafe.                                                                                                                                                      | `false`       |
| `box_api_app_token`         | A [box.com API App Token](https://developer.box.com/guides/applications/custom-apps/app-token-setup/) that allows the edge module to rewrite links to Box.com files (eg, https://my-uni.box.com/file/12345678) to an URL that is handled by the edge module (eg, `/lti-courses/download-file/f00b4r-h4sh`) that are downloaded directly by the client. |               |
| `download_url_ttl`          | TTL in ms of the download URLs generated by the edge module when rewriting Box.com links                                                                                                                                                                                                                                                               | `300000`      |
| `port`                      | `8081`               Server port to listen on                                                                                                                                                                                                                                                                                                          | `8081`        |
| `okapi_url`                 | Where to find Okapi (URL)                                                                                                                                                                                                                                                                                                                              | *required*    |
| `request_timeout_ms`        | Request Timeout                                                                                                                                                                                                                                                                                                                                        | `30000`       |
| `ssl_enabled`               | Set whether SSL/TLS is enabled for Vertx Http Server                                                                                                                                                                                                                                                                                                   | `false`       |
| `keystore_type`             | Set the key store type                                                                                                                                                                                                                                                                                                                                 | `NA`          |
| `keystore_provider`         | Set the provider name of the key store                                                                                                                                                                                                                                                                                                                 | `NA`          |
| `keystore_path`             | Set the path to the key store, if both `keystore_path` and `keystore_password` are populated - BCFKS security provider is used for SSL/TLS                                                                                                                                                                                                             | `NA`          |
| `keystore_password`         | Set the password for the key store                                                                                                                                                                                                                                                                                                                     | `NA`          |
| `key_alias`                 | Optional identifier that points to a specific key within the key store                                                                                                                                                                                                                                                                                 | `NA`          |
| `key_alias_password`        | Optional param that points to a password of `key_alias` if it protected                                                                                                                                                                                                                                                                                | `NA`          |
| `log_level`                 | Log4j Log Level                                                                                                                                                                                                                                                                                                                                        | `INFO`        |
| `token_cache_capacity`      | Max token cache size                                                                                                                                                                                                                                                                                                                                   | `100`         |
| `token_cache_ttl_ms`        | How long to cache JWTs, in milliseconds (ms)                                                                                                                                                                                                                                                                                                           | `100`         |
| `secure_store`              | Type of secure store to use.  Valid: `Ephemeral`, `AwsSsm`, `Vault`                                                                                                                                                                                                                                                                                    | `Ephemeral`   |
| `secure_store_props`        | Path to a properties file specifying secure store configuration                                                                                                                                                                                                                                                                                        | `NA`          |

### LTI Platforms

LTI Advantage requires that Platforms and Tools have knowledge of one other before they can safely and effectively communicate. This Tool is configured with the platforms it should respond to via the [`ui-lti-courses`](https://github.com/doytch/ui-lti-courses) module. More information about what is configurable can be found there, but at a minimum, this Tool needs to know the following about a platform:

- Client ID: The ID that was generated _for the Tool_ by the Platform.
- Issuer: Corresponds to the `iss` field in a JWT sent by the Platform.
- JWKS URL: A location where the Tool can fetch the Platform's JWKS so that it can decode the JWTs sent and signed by the Platform.
- OIDC Authorization URL: The URL that the Tool should redirect the Platform to after handling an OIDC Login Initiation.
- Search URL: A templated URL that the Tool will use when rendering HTML links to the course reserve items.

## Code Tour

[A full Code Tour that's intended for a technical audience is available here.](docs/code-tour.md)

## Security

[edge-common](https://github.com/folio-org/edge-common) contains a description of the security model as it relates to the `apiKey`. Additionally, further security is added due to LTI's use of an OIDC login flow.

### apiKey

This module requires passing the `apiKey` as a path param, e.g., `https://my-server.edu/lti-courses/launches/myApiKey`. The reason for this is that many LMS expect to have exclusive use of the query params and insert a second `?` in the URL, so this just ensures things are a bit less flaky.

#### Platform RSA Keys

The Platform's public key is fetched via a JWKS. The Platform's JWKS URL is configured using the [`ui-lti-courses`](https://github.com/doytch/ui-lti-courses) module.

## Requires Permissions

Institutional users should be granted the following permission in order to use this edge module:
- `course-reserves-storage.courselistings.reserves.collection.get`
- `course-reserves-storage.courses.collection.get`
- `configuration.entries.collection.get`

## Box.com API integration

Folio inventory items can have their "Electronic access" defined with an URL. If an item's electronic access URL is a link to a file stored on box.com (eg, https://my-uni.box.com/file/12345678), the link rendered by the edge module would normally link the user to that same box.com URL. If the user viewing the link doesn't have permissions to view the page, then the edge module can be configured so that the link rendered can instead result in the user downloading the file directly regardless of their permissions.

To enable this functionality, [you need to pass in a Box.com API App Token](https://developer.box.com/guides/applications/custom-apps/app-token-setup) via the `box_api_app_token` system property as described above. Afterwards, when responding to a LTI Request the edge module will rewrite box.com links into generated links to _itself_ that contain a hash of the file ID. The generated link will be valid for 10 minutes by default. When a user clicks the link, the edge module will download the file directly using it's box.com App Token and route the file contents to the user as a file download.

## Additional Docs

- [OAuth 2.0 and OIDC (in plain English)](https://www.youtube.com/watch?v=996OiexHze0): I love this video as a primer on the oft-confusing OAuth/OIDC flow.
- [Adding the reference implementation Tool to Sakai](https://github.com/sakaiproject/sakai/blob/master/basiclti/docs/IMS_RI.md): This is useful if you need something to pattern adding this Tool into your instance of Sakai (or other LMS).
- [LTI Advantage Reference Implementations](https://lti-ri.imsglobal.org/): Useful for debugging platform/tool issues.
- [ui-lti-courses](https://github.com/doytch/ui-lti-courses): Folio settings module that allows you to register LTI Platforms for this tool
