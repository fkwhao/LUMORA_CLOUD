package com.lumora.cloud.common.logging;

import org.slf4j.Logger;
import org.springframework.http.HttpStatus;

/**
 * Writes request failure metadata without logging request/response bodies or authentication data.
 */
public final class SafeRequestErrorLogger {

    private SafeRequestErrorLogger() {
    }

    public static void log(
            Logger logger,
            String service,
            HttpStatus status,
            String code,
            String traceId,
            String method,
            String path,
            Throwable error
    ) {
        String errorType = error == null ? "NONE" : error.getClass().getSimpleName();
        if (status.is5xxServerError()) {
            logger.error(
                    "event=request_failed service={} traceId={} method={} path={} status={} code={} errorType={}",
                    service, traceId, method, path, status.value(), code, errorType, error
            );
            return;
        }
        logger.warn(
                "event=request_rejected service={} traceId={} method={} path={} status={} code={} errorType={}",
                service, traceId, method, path, status.value(), code, errorType
        );
    }
}
