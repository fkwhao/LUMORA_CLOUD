package com.lumora.cloud.user.web;

import com.lumora.cloud.user.service.AuthService;
import com.lumora.cloud.user.web.AuthContracts.UserProfileResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/users")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    public UserProfileResponse me(@AuthenticationPrincipal Jwt jwt) {
        AuthService.UserProfile profile = authService.profile(Long.valueOf(jwt.getSubject()));
        return new UserProfileResponse(profile.id(), profile.email(), profile.displayName(), profile.status(), profile.roles());
    }
}
