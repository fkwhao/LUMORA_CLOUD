package com.lumora.cloud.catalog.security;

import com.lumora.cloud.api.UserContext;
import com.lumora.cloud.api.UserContextHolder;
import com.lumora.cloud.catalog.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class CatalogAccess {

    public void requireUser() {
        context();
    }

    public void requireAdmin() {
        if (!context().roles().contains("ADMIN")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "需要管理员权限");
        }
    }

    private UserContext context() {
        return UserContextHolder.current().orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "请先登录")
        );
    }
}
