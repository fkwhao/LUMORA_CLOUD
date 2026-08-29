package com.lumora.cloud.modelgateway.error;

import com.lumora.cloud.api.AuthHeaders;
import com.lumora.cloud.common.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.ServerWebExchange;

import java.time.Instant;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApiException(ApiException exception, ServerWebExchange exchange) {
        return error(exception.getStatus(), exception.getCode(), exception.getMessage(), exchange);
    }

    @ExceptionHandler(DecodingException.class)
    ResponseEntity<ApiError> handleDecoding(DecodingException exception, ServerWebExchange exchange) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_BODY", "请求体不是有效的 JSON", exchange);
    }

    @ExceptionHandler(ServerWebInputException.class)
    ResponseEntity<ApiError> handleInput(ServerWebInputException exception, ServerWebExchange exchange) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_BODY", "请求体格式不正确", exchange);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception, ServerWebExchange exchange) {
        log.error("Unhandled model-gateway-service error", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "模型网关暂时不可用", exchange);
    }

    private ResponseEntity<ApiError> error(
            HttpStatus status,
            String code,
            String message,
            ServerWebExchange exchange
    ) {
        String traceId = exchange.getRequest().getHeaders().getFirst(AuthHeaders.REQUEST_ID);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        return ResponseEntity.status(status).body(new ApiError(code, message, traceId, Instant.now()));
    }
}
