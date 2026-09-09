package com.example.myargus.group.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.myargus.common.enums.GroupInvitationStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 群组邀请实体，映射 group_invitations 表。
 */
@TableName("group_invitations")
@Data
public class GroupInvitation {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 群组 ID */
    private Long groupId;

    /** 邀请人用户 ID */
    private Long inviterUserId;

    /** 被邀请人用户 ID */
    private Long inviteeUserId;

    /** 邀请状态 */
    private GroupInvitationStatus status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}