package com.example.myargus.group.model.vo;

import java.time.LocalDateTime;

/**
 * 群主视角的加入申请响应。
 */
public record OwnerJoinRequestResponse(
        Long requestId,
        Long groupId,
        Long applicantUserId,
        String applicantUserCode,
        String applicantDisplayName,
        String status,
        LocalDateTime createdAt
) {
}
