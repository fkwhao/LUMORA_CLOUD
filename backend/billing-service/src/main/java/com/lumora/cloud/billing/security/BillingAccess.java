package com.lumora.cloud.billing.security;

import com.lumora.cloud.api.UserContext;
import com.lumora.cloud.api.UserContextHolder;
import com.lumora.cloud.billing.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class BillingAccess {

    public Long requireUserId() {
        UserContext context = context();
        try {
            return Long.valueOf(context.userId());
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_USER_CONTEXT", "登录用户信息不正确");
        }
    }

    public void requireAdmin() {
        UserContext context = context();
        if (!context.roles().contains("ADMIN")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "需要管理员权限");
        }
    }

    public Long requireAdminUserId() {
        requireAdmin();
        return requireUserId();
    }

    private UserContext context() {
        return UserContextHolder.current().orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "请先登录")
        );
    }
}
