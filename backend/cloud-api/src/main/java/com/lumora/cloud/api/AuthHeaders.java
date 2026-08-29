package com.lumora.cloud.api;

import java.util.List;

public final class AuthHeaders {

    public static final String USER_ID = "X-Lumora-User-Id";
    public static final String SESSION_ID = "X-Lumora-Session-Id";
    public static final String DEVICE_ID = "X-Lumora-Device-Id";
    public static final String ROLES = "X-Lumora-Roles";
    public static final String CLIENT_TYPE = "X-Lumora-Client-Type";
    public static final String REQUEST_ID = "X-Lumora-Request-Id";
    public static final String CLIENT_REQUEST_ID = "X-Lumora-Client-Request-Id";
    public static final String SERVICE_ID = "X-Lumora-Service-Id";
    public static final String INTERNAL_TOKEN = "X-Lumora-Internal-Token";

    public static final String SESSION_CACHE_PREFIX = "lumora:auth:session:";
    public static final String REVOKED_SESSION_PREFIX = "lumora:auth:revoked:session:";

    public static final List<String> CLIENT_FORBIDDEN_HEADERS = List.of(
            USER_ID,
            SESSION_ID,
            DEVICE_ID,
            ROLES,
            CLIENT_TYPE,
            REQUEST_ID,
            SERVICE_ID,
            INTERNAL_TOKEN
    );

    public static final List<String> FEIGN_RELAY_HEADERS = List.of(
            "Authorization",
            USER_ID,
            SESSION_ID,
            DEVICE_ID,
            ROLES,
            CLIENT_TYPE,
            REQUEST_ID
    );

    private AuthHeaders() {
    }
}
