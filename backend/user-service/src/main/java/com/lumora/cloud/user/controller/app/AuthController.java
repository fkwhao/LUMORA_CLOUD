package com.lumora.cloud.user.controller.app;

import com.lumora.cloud.user.config.AuthProperties;
import com.lumora.cloud.user.domain.enums.ClientType;
import com.lumora.cloud.user.domain.dto.auth.LoginRequest;
import com.lumora.cloud.user.domain.dto.auth.LogoutRequest;
import com.lumora.cloud.user.domain.dto.auth.RefreshRequest;
import com.lumora.cloud.user.domain.dto.auth.RegisterRequest;
import com.lumora.cloud.user.domain.model.AuthResult;
import com.lumora.cloud.user.domain.model.UserProfile;
import com.lumora.cloud.user.domain.vo.auth.AuthResponse;
import com.lumora.cloud.user.domain.vo.auth.UserProfileResponse;
import com.lumora.cloud.user.service.IAuthService;
import com.lumora.cloud.user.utils.RequestMetadata;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

import java.time.Duration;
import java.time.Instant;

@RestController
@RequestMapping("/api/app/auth")
@RequiredArgsConstructor
public class AuthController {

    private final IAuthService authService;
    private final AuthProperties properties;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest
    ) {
        return authenticationResponse(authService.register(request, metadata(servletRequest)));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        return authenticationResponse(authService.login(request, metadata(servletRequest)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @RequestBody(required = false) RefreshRequest request,
            HttpServletRequest servletRequest
    ) {
        String refreshCookie = refreshCookie(servletRequest);
        String refreshToken = firstNonBlank(refreshCookie, request == null ? null : request.refreshToken());
        return authenticationResponse(authService.refresh(refreshToken, metadata(servletRequest)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestBody(required = false) LogoutRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest
    ) {
        String refreshCookie = refreshCookie(servletRequest);
        String refreshToken = firstNonBlank(refreshCookie, request == null ? null : request.refreshToken());
        String sessionId = jwt == null ? null : jwt.getClaimAsString("sid");
        authService.logout(refreshToken, sessionId);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .build();
    }

    private ResponseEntity<AuthResponse> authenticationResponse(AuthResult result) {
        String responseRefreshToken = result.clientType() == ClientType.DESKTOP ? result.refreshToken() : null;
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (result.clientType() == ClientType.WEB) {
            response.header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken(), result.sessionExpiresAt()).toString());
        }
        return response.body(new AuthResponse(
                "Bearer",
                result.accessToken().value(),
                result.accessToken().expiresAt(),
                responseRefreshToken,
                result.sessionExpiresAt(),
                profile(result.user())
        ));
    }

    private UserProfileResponse profile(UserProfile profile) {
        return new UserProfileResponse(profile.id(), profile.email(), profile.displayName(), profile.status(), profile.roles());
    }

    private ResponseCookie refreshCookie(String value, Instant expiresAt) {
        Duration maxAge = Duration.between(Instant.now(), expiresAt);
        return ResponseCookie.from(properties.cookie().name(), value)
                .httpOnly(true)
                .secure(properties.cookie().secure())
                .sameSite(properties.cookie().sameSite())
                .path(properties.cookie().path())
                .maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge)
                .build();
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(properties.cookie().name(), "")
                .httpOnly(true)
                .secure(properties.cookie().secure())
                .sameSite(properties.cookie().sameSite())
                .path(properties.cookie().path())
                .maxAge(Duration.ZERO)
                .build();
    }

    private String refreshCookie(HttpServletRequest request) {
        var cookie = WebUtils.getCookie(request, properties.cookie().name());
        return cookie == null ? null : cookie.getValue();
    }

    private RequestMetadata metadata(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String ipAddress = StringUtils.hasText(forwardedFor)
                ? forwardedFor.split(",", 2)[0].trim()
                : request.getRemoteAddr();
        return new RequestMetadata(limit(ipAddress, 45), limit(request.getHeader(HttpHeaders.USER_AGENT), 255));
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String limit(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
