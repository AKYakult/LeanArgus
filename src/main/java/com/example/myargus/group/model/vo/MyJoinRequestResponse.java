package com.example.myargus.group.model.vo;

import java.time.LocalDateTime;

/**
 * 当前用户视角的加入申请响应。
 */
public record MyJoinRequestResponse(
        Long requestId,
        Long groupId,
        String groupCode,
        String groupName,
        String status,
        LocalDateTime createdAt,
        LocalDateTime decidedAt
) {
}
