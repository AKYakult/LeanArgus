package com.example.myargus.auth.model.vo;

import com.example.myargus.auth.service.AuthService.AuthTokens;

/**
 * 登录/刷新令牌响应，只包含 access token 和当前用户信息。
 * refresh token 走 httpOnly Cookie 下发，不出现在响应体里，避免前端 JS 能读到它。
 */
public record AuthTokensResponse(
        String accessToken,
        CurrentUserProfileResponse currentUser
) {
    public static AuthTokensResponse from(AuthTokens tokens) {
        return new AuthTokensResponse(tokens.accessToken(), CurrentUserProfileResponse.from(tokens.currentUser()));
    }
}