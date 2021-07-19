# Code Tour for Developers

## Purpose

Give a technical overview of how this program is structured. We expect decent familiarity with Folio and its API norms, but not necessarily with how edge applications are built. We also expect some familiarity with the LTI 1.3/Advantage protocol and will not go into tonnes of detail there because implementations of it vary across LMSs like Sakai/Blackboard/Moodle/Canvas/etc. Technical details of JWT and cryptography will be ignored but we assume you know how public/private key-pair cryptography works in broad strokes.

## Table of contents

- [Purpose](#purpose)
- [Entry Point: `MainVerticle`](#entry-point-mainverticle)
  - [OIDC State Cache](#oidc-state-cache)
  - [RSA, Tool Keys, & JWKS](#rsa-tool-keys--jwks)
  - [LTI Endpoints](#lti-endpoints)
  - [Box.com API Integrations](#boxcom-api-integrations)
- [LTI Endpoint Handler: `LtiCoursesHandler`](#lti-endpoint-handler-lticourseshandler)
  - [`handleOIDCLoginInit`](#handleoidclogininit)
  - [`handleRequest`, `handleRequestCourseExternalId`, `handleRequestCourseRegistrarId`](#handlerequest-handlerequestcourseexternalid-handlerequestcourseregistrarid)
  - [`handleLaunch`](#handlelaunch)
  - [Look up the Requesting LTI Platform: `handleCommonLTI`](#look-up-the-requesting-lti-platform-handlecommonlti)
  - [Validate the Request's JWT: `handleLaunch`](#validate-the-requests-jwt-handlelaunch)
  - [Look up the Course: `getCourse`](#look-up-the-course-getcourse)
  - [Render our LTI Resource Link Response: `getCourse` and `renderResourceLink`](#render-our-lti-resource-link-response-getcourse-and-renderresourcelink)
- [Rendering a Reserve](#rendering-a-reserve)
- [Box.com API Integration](#boxcom-api-integration)
  - [Prerequisites](#prerequisites)
  - [Code Flow](#code-flow)

## Entry Point: `MainVerticle`

Like most Folio edge applications, a suitable starting place to start our tour is the [`MainVerticle`](https://github.com/folio-org/edge-lti-courses/blob/master/src/main/java/org/folio/edge/ltiCourses/MainVerticle.java). Note it extends `EdgeVerticle`, which we use for a lot of core functionality. In general, the `org.folio.edge.core` package gives us a lot that we use.

The [`defineRoutes`](https://github.com/folio-org/edge-lti-courses/blob/master/src/main/java/org/folio/edge/ltiCourses/MainVerticle.java#L83) method does what it says on the tin. After some initial setup, we define what routes the edge app provides and what methods are responsibile for handling those routes.

### OIDC State Cache

LTI communications consist of handshake done using the OIDC protocol. The handshakes must be completed within some amount of time. The OIDC State Cache provides the mechanism to manage the `state` of the OIDC protocol and expire it after some time. The implementation is fairly boring and relies mainly on functionality provided by `org.folio.edge.core`.

### RSA, Tool Keys, & JWKS

Part of the LTI Advantage spec includes signing a JWT that one side is sending with RSA keys. So this LTI Tool needs access to the LTI Platform's public key to validate the signature on JWTs it receives, and the LTI Platform needs this tool's public key to perform the validation on JWTs sent by this tool. Different platforms expect this key exchange to work in different ways. Some generate the keys for the tool which you'd then put into the `LTI_TOOL_PRIVATE_KEY_FILE` and `LTI_TOOL_PUBLIC_KEY_FILE`, and others let the tool generate its own keys and either let you paste in the public key or the URL to the tool's JWKS endpoint. This app supports all these mechanism.

### LTI Endpoints

The various LTI endpoints are handled by the `LtiCoursesHandler`. Different institutions may want the LTI Course ID stored in different places on a Folio Course object, so we provide separate `/launches/` endpoints for lookup via Course ID, External ID and Registrar ID.

### Box.com API Integrations

We'll get to this here!

## LTI Endpoint Handler: `LtiCoursesHandler`

We do most of the heavy lifting here. We extend from `org.folio.edge.core.Handler` which saves us some brute work. It may be helpful to have [this section in the IMS LTI spec that discusses security and OIDC model](https://www.imsglobal.org/spec/security/v1p0/#platform-originating-messages) open when understanding this class.

### `handleOIDCLoginInit`

InThe first thing that'll happen in the OIDC flow is the [third-party initiated login](https://www.imsglobal.org/spec/security/v1p0/#step-1-third-party-initiated-login), the third-party here being the LTI Platform. So the platform's request will enter this handler, which will be validated to make sure that it includes the required fields. A `nonce` and `state` are generated for the aforementioned OIDC State Cache, and an URL is constructued which will be returned with our response as a 302 Redirect.

That URL will redirect the browser back to the LTI Platform's auth endpoint, completing the OIDC handshake. When it's handling that, it'll pull off some of the query parameters from the URL and use those when constructing the JWT it will send to use during the LTI Tool Launch phase that is about to begin...

### `handleRequest`, `handleRequestCourseExternalId`, `handleRequestCourseRegistrarId`

The LTI Platform finished handling the OIDC login init, so now it requests one of our `/launches/` endpoints. As part of configuring the LTI Platform (in the Sakai/Blackboard/Canvas/etc admin UI), the tool's launch URL was configured as one of those endpoints depending on the library's needs and the way the LMS is configured. Since the work they all do is the same, the work is implemented in a common `handleLaunch` method.

### `handleLaunch`

This method looks up the requesting LTI platform, validates the request's JWT, fetches the Folio course which is refers to, and renders an LTI Resource Link containing the current reserves.

### Look up the Requesting LTI Platform: `handleCommonLTI`

This is common to several LTI requests (Resource Link Requests, Deep Link Requests), so it's separated into a `handleCommonLTI` method. In LTI exchanges, the Tool must only respond to requests from platforms that have been white-listed by it. This is why the `ui-lti-courses` Folio settings app is critical. It stores multiple configurations of platforms (eg, staging, dev, and production Sakai servers). This configuration is looked-up in `handleCommonLTI` and passed back to `handleLaunch`. A `400: Bad Request` is sent if the platform is not defined.

### Validate the Request's JWT: `handleLaunch`

We run a few validations on the JWT including validation of the signature to ensure this is a valid JWT. Here we see why pre-configuration of the platform is necessary since that configuration includes the platform's JWKS URL. That's how we can ensure that this was signed by an entity we trust. This is also where we finally use the nonce and state stored in the OIDC State Cache. This protects against replayed requests. Once we confirm this is a valid JWT, we...

### Look up the Course: `getCourse`

Using information from the JWT's claims, we look up the course about which this request wants reserves information for. Like all of our requests to Folio, this is mediated using the [`LtiCoursesOkapiClient`](https://github.com/folio-org/edge-lti-courses/blob/master/src/main/java/org/folio/edge/ltiCourses/utils/LtiCoursesOkapiClient.java). Once we get the raw data back from Okapi and mod-courses, we store it in our POJO and add some extra information from the pre-configured platform (discovery layer search URL, box.com integration, etc). Then we fetch the reserves from Folio, add those to the Course POJO, and get ready to....

### Render our LTI Resource Link Response: `getCourse` and `renderResourceLink`

The information in that Course POJO is then fed into a Pug (aka Jade) template. We use Pug templates for most of our responses. The Resource Link response is in the [`ResourceLinkResponse`](https://github.com/folio-org/edge-lti-courses/blob/master/src/main/resources/templates/ResourceLinkResponse.jade) template. Things here are fairly self-explanatory and the [Pug docs should help explain any syntax](https://pugjs.org/api/getting-started.html). 

We're done handling our request!

## Rendering a Reserve

When we render the response for a Resource Link, it contains a list of the current reserves for the course. The entire set of the course's reserves are filtered and rendered differently in different scenarios. Generally, a reserve is rendered as a link where the text is the item's name and it's contributors. However:

* [If the reserve item is not currently reserved,](https://github.com/folio-org/edge-lti-courses/blob/master/src/main/java/org/folio/edge/ltiCourses/model/Course.java#L86-L113), it won't be displayed.
* [If the reserve item's instance has had discovery suppressed](https://github.com/folio-org/edge-lti-courses/blob/328927d8358ae817aeeb71ef7249424eedbb0c2f/src/main/resources/templates/ResourceLinkResponse.jade#L11-L12), the reserve will be rendered without a link and instead will contain the effective location of the item.
* [If there is no `reserve.uri` (ie, no Electronic Access has been defined for the item),](https://github.com/folio-org/edge-lti-courses/blob/v1.2.0/src/main/java/org/folio/edge/ltiCourses/model/Course.java#L115), the barcode or instance HRID will be injected into the preconfigured discovery layer search URL for the LTI Platform. That URL will be the reserve's link `href`.
* [If there is a `reserve.uri`, said URI is a `box.com` URI, and Box.com integration has been configured,](https://github.com/folio-org/edge-lti-courses/blob/v1.2.0/src/main/java/org/folio/edge/ltiCourses/model/Course.java#L115), the URI will be rewritten as a link to this edge application's `/lti-courses/download-file/` endpoint.
* If there is a `reserve.uri` and box.com integration doesn't apply, the reserve's link will be to that URI.

## Box.com API Integration

It may be the case that your institution wants to have some electronic items (PDFs, etc) set aside as "reserved" and they upload these to Box.com for storage and usage metrics etc. You don't want to provide a public link to students because the link could be shared and everyone could have knowledge and the dear Content Providers would go hungry and the world would fall into collapse. So instead, this app can take those URLs to box.com and rewrite them as shortly-lived hashed URLs to itself. When a student clicks on such a link, this app will dereference the hash, download the file, and stream it out to the student.

### Prerequisites

* Set up an account (eg, service account) that has an [App Token](https://developer.box.com/guides/authentication/app-token/) generated for it.
* Store your files in a box.com folder and share the folder with that account.

### Code Flow

* Upon startup, [Box.com API integration](https://github.com/folio-org/edge-lti-courses/blob/v1.2.0/src/main/java/org/folio/edge/ltiCourses/MainVerticle.java#L132-L142) is setup if an App Token was provided. A cache is created for the shortly-lived URLs and an endpoint is registered.
* When handling an LTI request, if the API integration is enabled and the `reserve.uri` is a link to Box.com, [a shortly-lived URL is generated](https://github.com/folio-org/edge-lti-courses/blob/v1.2.0/src/main/java/org/folio/edge/ltiCourses/model/Course.java#L123-L130) and that URL is used instead of the original `reserve.uri`.
* When a student or whoever clicks on that link, this edge app handles the download request by [looking up the original box.com URL, downloading the file to a temp file, streaming it out, and deleting the temp file.](https://github.com/folio-org/edge-lti-courses/blob/v1.2.0/src/main/java/org/folio/edge/ltiCourses/BoxDownloadHandler.java#L29) It'd be nice if we didn't have to download the file and could just stream it out directly but that functionality currently (July 19 2021) doesn't exist in Box.com's Java SDK.
