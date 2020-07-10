# edge-lti-courses

Copyright (C) 2018-2019 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Overview

The purpose of this edge API is to connect LMS such as Sakai and Blackboard (LTI Platforms) to Folio via the [LTI Advantage](https://www.imsglobal.org/lti-advantage-overview) protocol for the purposes of sharing the course reserves stored in Folio.

To accomplish this, this module acts as an LTI Tool Provider. It adheres to two parts of the LTI Advantage spec to accomplish this. First, security is enabled via a [third-party-initiated OIDC Authentication flow](https://www.imsglobal.org/spec/security/v1p0/#platform-originating-messages) (this is a generic ). Second, the actual course reserves are requested and shown via messages using [the Resource Link spec](https://www.imsglobal.org/spec/lti/v1p3/#resource-link-launch-request-message).

The general use flow is as follows:

1. Upon navigation to the tool's page on an LMS such as Sakai (the LTI platform), the platform sends an OIDC login initiation request to the tool at a predefined URL.
1. This request is handled in `LtiCoursesHandler::handleOidcLoginInit`. The request is parsed and validated.
1. If the request is valid, it responds with an HTTP 302 Redirect to a preconfigured URL for that platform.
1. The platform handles that redirect and bounces a request back to the tool to the tool launch endpoint with the LTI request itself.
1. The tool parses the LTI request which contains info about which course is being viewed in the LMS. Based on that, it fetches the course and its reserves from mod-courses, renders them in HTML, and sends back the HTML.
1. The platform takes the response and displays it in an iframe.

## Configuration

Configuration is done in two ways. Values common to Folio Edge modules are configured using the methods made available in [edge-common](https://github.com/folio-org/edge-common). These handle things like the `port`, `okapi_url`, or `secure_store_props`.

### LTI Platforms

LTI Advantage requires that Platforms and Tools have knowledge of one other before they can safely and effectively communicate. This Tool is configured with the platforms it should respond to via the [`ui-lti-courses`](https://github.com/doytch/ui-lti-courses) module. More information about what is configurable can be found there, but at a minimum, this Tool needs to know the following about a platform:

- Client ID: The ID that was generated _for the Tool_ by the Platform.
- Issuer: Corresponds to the `iss` field in a JWT sent by the Platform.
- JWKS URL: A location where the Tool can fetch the Platform's JWKS so that it can decode the JWTs sent and signed by the Platform.
- OIDC Authorization URL: The URL that the Tool should redirect the Platform to after handling an OIDC Login Initiation.
- Search URL: A templated URL that the Tool will use when rendering HTML links to the course reserve items.

## Security

[edge-common](https://github.com/folio-org/edge-common) contains a description of the security model as it relates to the `apiKey`. Additionally, further security is added due to LTI's use of an OIDC login flow.

### apiKey

This module requires passing the `apiKey` as a path param, e.g., `https://my-server.edu/lti-courses/launches/myApiKey`. The reason for this is that many LMS expect to have exclusive use of the query params and insert a second `?` in the URL, so this just ensures things are a bit less flaky.

#### Platform RSA Keys

The Platform's public key is fetched via a JWKS. The Platform's JWKS URL is configured using the [`ui-lti-courses`](https://github.com/doytch/ui-lti-courses) module.

## Requires Permissions

Institutional users should be granted the following permission in order to use this edge API:
- `course-reserves-storage.courselistings.reserves.collection.get`
- `course-reserves-storage.courses.collection.get`
- `configuration.entries.collection.get`

## Additional Docs

- [OAuth 2.0 and OIDC (in plain English)](https://www.youtube.com/watch?v=996OiexHze0): I love this video as a primer on the oft-confusing OAuth/OIDC flow.
- [Adding the reference implementation Tool to Sakai](https://github.com/sakaiproject/sakai/blob/master/basiclti/docs/IMS_RI.md): This is useful if you need something to pattern adding this Tool into your instance of Sakai (or other LMS).
- [LTI Advantage Reference Implementations](https://lti-ri.imsglobal.org/): Useful for debugging platform/tool issues.
- [ui-lti-courses](https://github.com/doytch/ui-lti-courses): Folio settings module that allows you to register LTI Platforms for this tool
