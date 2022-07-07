package org.folio.edge.ltiCourses;

import java.io.File;
import java.io.FileOutputStream;

import io.vertx.ext.web.RoutingContext;
import io.vertx.core.http.HttpHeaders;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.folio.edge.ltiCourses.cache.BoxFileCache;

import com.box.sdk.BoxTransactionalAPIConnection;
import com.box.sdk.BoxFile;

public class BoxDownloadHandler {
  protected BoxTransactionalAPIConnection api;

  private static final Logger logger = LogManager.getLogger(BoxDownloadHandler.class);

  public BoxDownloadHandler(String appToken) {
    if (appToken == null || appToken.isEmpty()) {
      logger.info("No Box API App Token was provided, Box URLs will not be transformed.");
    } else {
      logger.info("Creating new Box API instance using App Token starting with: " + appToken.substring(0, 5));
      api = new BoxTransactionalAPIConnection(appToken);
    }
  }

  protected void handleDownloadRequest(RoutingContext ctx) {
    if (api == null) {
      logger.error("No Box API App Token was provided, Box URLs cannot be transformed.");
      ctx.response()
        .setStatusCode(400)
        .end("Direct download of this file is not supported by the system.");

      return;
    }

    final String hash = ctx.request().getParam("hash");
    String boxFileId = BoxFileCache.getInstance().get(hash);

    if (boxFileId == null || boxFileId.isEmpty()) {
      ctx.response()
        .setStatusCode(400)
        .end("This file is no longer available for download. Reload the list of reserves and try again.");

      return;
    }

    File tempFile;
    try {
      tempFile = File.createTempFile("edge-lti-box-file-", null);
      tempFile.deleteOnExit();
    } catch (Exception e) {
      final String errorMessage = "Failed while creating file: " + e.getMessage();
      logger.error(errorMessage);
      ctx.response()
        .setStatusCode(500)
        .end(errorMessage);

      return;
    }

    BoxFile file;
    try (FileOutputStream stream = new FileOutputStream(tempFile.getPath());) {
      file = new BoxFile(api, boxFileId);
      file.download(stream);
    } catch (Exception e) {
      final String errorMessage = "Failed while downloading file: " + e.getMessage();
      logger.error(errorMessage);
      ctx.response()
        .setStatusCode(500)
        .end(errorMessage);

      return;
    }

    logger.info("Sending the Box file that was temporarily stored at " + tempFile.getPath());

    ctx.response()
      .putHeader(HttpHeaders.CONTENT_TYPE, "text/plain")
      .putHeader("Content-Disposition", "attachment; filename=\"" + file.getInfo().getName() + "\"")
      .putHeader(HttpHeaders.TRANSFER_ENCODING, "chunked")
      .sendFile(tempFile.getPath());

    if (!tempFile.delete()) {
      logger.info("Failed to delete temp file at " + tempFile.getPath());
    }
  }
}