package com.lumora.cloud.user.service;

import com.lumora.cloud.user.domain.dto.auth.LoginRequest;
import com.lumora.cloud.user.domain.dto.auth.RegisterRequest;
import com.lumora.cloud.user.domain.model.AuthResult;
import com.lumora.cloud.user.domain.model.UserProfile;
import com.lumora.cloud.user.utils.RequestMetadata;

public interface IAuthService {

    AuthResult register(RegisterRequest request, RequestMetadata metadata);

    AuthResult login(LoginRequest request, RequestMetadata metadata);

    AuthResult refresh(String rawRefreshToken, RequestMetadata metadata);

    void logout(String rawRefreshToken, String authenticatedSessionId);

    UserProfile profile(Long userId);
}
