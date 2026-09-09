package com.example.myargus.group.controller;

import com.example.myargus.common.api.ApiResponse;
import com.example.myargus.group.service.GroupManagementService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 邀请决策控制器，提供接受、拒绝、取消邀请接口。
 */
@RestController
@RequestMapping("/api/invitations")
public class InvitationDecisionController {

    private final GroupManagementService groupManagementService;

    public InvitationDecisionController(GroupManagementService groupManagementService) {
        this.groupManagementService = groupManagementService;
    }

    /** 接受群组邀请 */
    @PostMapping("/{invitationId}/accept")
    public ApiResponse<Void> acceptInvitation(@PathVariable Long invitationId) {
        groupManagementService.acceptInvitation(invitationId);
        return ApiResponse.ok();
    }

    /** 拒绝群组邀请 */
    @PostMapping("/{invitationId}/reject")
    public ApiResponse<Void> rejectInvitation(@PathVariable Long invitationId) {
        groupManagementService.rejectInvitation(invitationId);
        return ApiResponse.ok();
    }

    /** 取消群组邀请（仅群组 OWNER 可操作） */
    @PostMapping("/{invitationId}/cancel")
    public ApiResponse<Void> cancelInvitation(@PathVariable Long invitationId) {
        groupManagementService.cancelInvitation(invitationId);
        return ApiResponse.ok();
    }
}
