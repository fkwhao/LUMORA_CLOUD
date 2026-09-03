package com.lumora.cloud.common.sentinel;

import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.system.SystemBlockException;
import org.springframework.http.HttpStatus;

import java.util.UUID;
import java.util.regex.Pattern;

record SentinelBlockResponse(HttpStatus status, String code, String message) {

    private static final Pattern TRACE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{7,127}");

    static SentinelBlockResponse from(Throwable error) {
        if (error instanceof DegradeException) {
            return new SentinelBlockResponse(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SERVICE_CIRCUIT_OPEN",
                    "服务正在熔断保护中，请稍后重试"
            );
        }
        if (error instanceof SystemBlockException) {
            return new SentinelBlockResponse(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SERVICE_OVERLOADED",
                    "服务当前负载过高，请稍后重试"
            );
        }
        return new SentinelBlockResponse(
                HttpStatus.TOO_MANY_REQUESTS,
                "REQUEST_RATE_LIMITED",
                "请求过于频繁，请稍后重试"
        );
    }

    static String traceId(String candidate) {
        return candidate != null && TRACE_ID.matcher(candidate.trim()).matches()
                ? candidate.trim()
                : UUID.randomUUID().toString();
    }
}
