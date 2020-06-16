# edge-lti-courses

Copyright (C) 2018-2019 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Introduction

Edge API to allow LTI platforms to fetch course reserves

## Overview

The purpose of this edge API is to connect LMS such as Sakai and Blackboard to Folio via the [LTI Advantage](https://www.imsglobal.org/lti-advantage-overview) protocol for the purposes  of sharing the course reserves stored in Folio.

LTI 1.3 contains [the Deep Linking 2.0 spec](https://www.imsglobal.org/spec/lti-dl/v2p0) which we use when communicating via LTI. The workflow is split into two phases that can occur weeks apart: finding a course, and then listing that course's reserves.

First, a course is found via LTI:

1. The LTI platform (an LMS such as Sakai) sends a Deep Linking Request to this module. That request is a JWT signed with RSA256 by the platform.
1. We verify that JWT using the stored copy of the platform's public key.
1. We parse the JWT and find the course (context) that generated this request.
1. We look up that course in mod-courses via Okapi and find it's `id`.
1. We send a Deep Linking Response that contains a [link of type HTML fragment](https://www.imsglobal.org/spec/lti-dl/v2p0/#html-fragment). The fragment contains Javascript which contains the course's `id` and the URL of this module.
1. The fragment is injected into the course page by the LMS.

Now that HTML has been placed into the LMS course site, we can dynamically fetch the course reserves each time the site is loaded.

1. When the page is loaded, the HTML fragment we injected in phase one will fetch the reserves for given course `id` from this module.
1. We fetch the reserves from mod-courses and send it back to the page.
1. The injected HTML fragment's Javascript takes the response and creates an HTML list based on it.

## Security

See [edge-common](https://github.com/folio-org/edge-common) for a description of the security model as it relates to the apiKey.

Furthermore, the communication via LTI requires additional security measures.

### LTI RSA256 Keys

LTI uses RSA256 encoding for its JWT messages. This tool requires the sending LTI platform's public key
to verify the JWTs it receives, and a private key with which to sign the responses it sends back to the platform.

Currently, these can be specified using the following config parameters:
- `lti_tool_private_key_file`
- `lti_tool_public_key_file`
- `lti_platform_public_key_file`

The private key must be in PKCS8 format. To generate one, run the following
> `openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out src/main/resources/private.pem `

To generate it's corresponding public key, run the following
> `openssl rsa -in src/main/resources/private.pem -pubout -out src/main/resources/public.pem`

### Fetching the LTI Tool Public Key

The LTI platform needs this tool's public key to decode the responses we sign using our private key. To simplify this, the `/lti-courses/public-key` endpoint is available which responds with the encoded public key.

## Requires Permissions

Institutional users should be granted the following permission in order to use this edge API:
- `course-reserves-storage.courselistings.reserves.collection.get`
- `course-reserves-storage.courses.collection.get`

## Configuration

See [edge-common](https://github.com/folio-org/edge-common) for a description of how configuration works.

Additionally, the module has other configuration parameters that can be set. Some are defined in the LTI RSA256 section. Others include:

- `base_url`: The URL at which this edge module will be listening. This is used when sending back the HTML fragment that contains information about where to fetch the list of reserves. This is required to handle scenarios where the module is behind reverse proxies, load balancers, TLS handlers, etc.

## Additional information

### Issue tracker

See project [EDGLTIC](https://issues.folio.org/browse/EDGLTIC)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker).

### Other documentation

Other [modules](https://dev.folio.org/source-code/#server-side) are described,
with further FOLIO Developer documentation at [dev.folio.org](https://dev.folio.org/)

