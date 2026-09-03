package com.example.myargus.auth;

import com.example.myargus.common.enums.SystemRole;
import com.example.myargus.common.enums.UserStatus;
import com.example.myargus.common.exception.BusinessException;
import com.example.myargus.common.exception.ForbiddenException;
import com.example.myargus.common.exception.UnauthorizedException;
import com.example.myargus.common.security.AuthenticatedUser;
import com.example.myargus.common.security.UserContext;
import com.example.myargus.user.service.UserQueryService;
import org.springframework.stereotype.Service;

/**
 * 当前登录用户服务。
 * <p>
 * 依赖 JwtAuthenticationFilter 在请求进入时设置 UserContext。
 */
@Service
public class CurrentUserService {

    private final UserQueryService userQueryService;

    public CurrentUserService(UserQueryService userQueryService) {
        this.userQueryService = userQueryService;
    }

    /** 获取当前登录用户，未登录抛出 401 */
    public CurrentUser getRequiredCurrentUser() {
        AuthenticatedUser authenticatedUser = UserContext.get();

        if (authenticatedUser != null) {
            return loadUserById(authenticatedUser.userId());
        }

        throw new UnauthorizedException("当前请求未登录");
    }

    /** 要求当前用户为系统管理员，否则抛出 403 */
    public CurrentUser requireSystemAdmin() {
        CurrentUser currentUser = getRequiredCurrentUser();

        if (currentUser.systemRole() != SystemRole.ADMIN) {
            throw new ForbiddenException("当前用户不是系统管理员");
        }

        return currentUser;
    }

    /** 要求当前用户为业务用户（非管理员），否则抛出 403 */
    public CurrentUser requireBusinessUser() {
        CurrentUser currentUser = getRequiredCurrentUser();

        if (currentUser.systemRole() == SystemRole.ADMIN) {
            throw new ForbiddenException("系统管理员不能访问普通业务区");
        }

        return currentUser;
    }

    /** 从数据库加载用户并校验状态 */
    private CurrentUser loadUserById(Long userId) {
        UserQueryService.UserRecord user = userQueryService.findById(userId);

        if (user == null) {
            throw new BusinessException("当前用户不存在");
        }

        if (user.status() == UserStatus.DISABLED) {
            throw new BusinessException("账号已被禁用");
        }

        return new CurrentUser(
                user.userId(),
                user.userCode(),
                user.displayName(),
                user.systemRole(),
                user.mustChangePassword()
        );
    }

    /** 当前登录用户信息 */
    public record CurrentUser(
            Long userId,
            String userCode,
            String displayName,
            SystemRole systemRole,
            boolean mustChangePassword
    ) {

        public CurrentUser(
                Long userId,
                String userCode,
                String displayName
        ) {
            this(
                    userId,
                    userCode,
                    displayName,
                    SystemRole.USER,
                    false
            );
        }
    }
}