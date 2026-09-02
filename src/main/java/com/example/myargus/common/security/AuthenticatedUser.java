package com.example.myargus.common.security;

import com.example.myargus.common.enums.SystemRole;

/**
 * JWT 解析成功后放入 UserContext 的用户信息。
 */
public record AuthenticatedUser(
        Long userId,
        String userCode,
        String displayName,
        SystemRole systemRole,
        boolean mustChangePassword
) {
}