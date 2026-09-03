package com.lumora.cloud.user.domain.dto.auth;

import com.lumora.cloud.user.domain.enums.ClientType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(min = 2, max = 80) String displayName,
        @NotNull ClientType clientType,
        @Size(max = 64) String deviceId,
        @Size(max = 120) String deviceName
) {
}
