package com.lumora.cloud.user.controller.app;

import com.lumora.cloud.user.domain.model.UserProfile;
import com.lumora.cloud.user.service.IAuthService;
import com.lumora.cloud.user.domain.vo.auth.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/users")
@RequiredArgsConstructor
public class UserController {

    private final IAuthService authService;

    @GetMapping("/me")
    public UserProfileResponse me(@AuthenticationPrincipal Jwt jwt) {
        UserProfile profile = authService.profile(Long.valueOf(jwt.getSubject()));
        return new UserProfileResponse(profile.id(), profile.email(), profile.displayName(), profile.status(), profile.roles());
    }
}
