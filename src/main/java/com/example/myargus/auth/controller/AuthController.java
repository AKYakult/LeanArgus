package com.example.myargus.auth.controller;

import com.example.myargus.auth.CurrentUserService;
import com.example.myargus.auth.config.AuthProperties;
import com.example.myargus.auth.model.dto.LoginRequest;
import com.example.myargus.auth.model.dto.RegisterRequest;
import com.example.myargus.auth.model.vo.AuthTokensResponse;
import com.example.myargus.auth.model.vo.CurrentUserProfileResponse;
import com.example.myargus.auth.security.AuthCookieSupport;
import com.example.myargus.auth.service.AuthService;
import com.example.myargus.auth.service.AuthService.AuthTokens;
import com.example.myargus.common.api.ApiResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器：登录、注册、刷新令牌、登出、获取当前用户。
 * refresh token 通过 httpOnly Cookie 下发，前端无需手动处理。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieSupport authCookieSupport;
    private final CurrentUserService currentUserService;
    private final AuthProperties authProperties;

    public AuthController(
            AuthService authService,
            AuthCookieSupport authCookieSupport,
            CurrentUserService currentUserService,
            AuthProperties authProperties
    ) {
        this.authService = authService;
        this.authCookieSupport = authCookieSupport;
        this.currentUserService = currentUserService;
        this.authProperties = authProperties;
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokensResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        AuthTokens tokens = authService.login(request.loginId(), request.password());
        authCookieSupport.writeRefreshTokenCookie(response, tokens.refreshToken());
        return ApiResponse.ok(AuthTokensResponse.from(tokens));
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ApiResponse.ok();
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthTokensResponse> refresh(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        AuthTokens tokens = authService.refresh(extractRefreshToken(request));
        authCookieSupport.writeRefreshTokenCookie(response, tokens.refreshToken());
        return ApiResponse.ok(AuthTokensResponse.from(tokens));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(extractRefreshToken(request));
        authCookieSupport.clearRefreshTokenCookie(response);
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserProfileResponse> currentUser() {
        return ApiResponse.ok(CurrentUserProfileResponse.from(currentUserService.getRequiredCurrentUser()));
    }

    private String extractRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String refreshCookieName = authProperties.getRefreshCookieName();
        for (Cookie cookie : cookies) {
            if (refreshCookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}