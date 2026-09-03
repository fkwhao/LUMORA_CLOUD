package com.lumora.cloud.user.domain.vo.admin;

import java.util.List;

public record AdminUserPageResponse(
        List<AdminUserResponse> items,
        Long nextCursor,
        boolean hasMore
) {
    public AdminUserPageResponse {
        items = List.copyOf(items);
    }
}
