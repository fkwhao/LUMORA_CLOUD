package com.lumora.cloud.api;

import java.util.Optional;

public final class UserContextHolder {

    private static final ThreadLocal<UserContext> CONTEXT = new ThreadLocal<>();

    private UserContextHolder() {
    }

    public static Optional<UserContext> current() {
        return Optional.ofNullable(CONTEXT.get());
    }

    public static UserContext getRequired() {
        return current().orElseThrow(() -> new IllegalStateException("No authenticated user context is available"));
    }

    public static void set(UserContext context) {
        CONTEXT.set(context);
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
