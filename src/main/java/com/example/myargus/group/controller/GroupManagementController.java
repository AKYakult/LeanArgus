package com.example.myargus.group.controller;

import com.example.myargus.common.api.ApiResponse;
import com.example.myargus.group.model.dto.CreateGroupRequest;
import com.example.myargus.group.model.dto.CreateInvitationRequest;
import com.example.myargus.group.model.vo.GroupMemberResponse;
import com.example.myargus.group.model.vo.MySentInvitationResponse;
import com.example.myargus.group.service.GroupManagementService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 群组管理控制器，提供群组创建、成员管理、邀请管理等接口。
 */
@RestController
@RequestMapping("/api/groups")
public class GroupManagementController {

    private final GroupManagementService groupManagementService;

    public GroupManagementController(GroupManagementService groupManagementService) {
        this.groupManagementService = groupManagementService;
    }

    /** 创建新群组 */
    @PostMapping
    public ApiResponse<Long> createGroup(@Valid @RequestBody CreateGroupRequest createGroupRequest) {
        return ApiResponse.ok(groupManagementService.createGroup(createGroupRequest));
    }

    /** 创建群组邀请 */
    @PostMapping("/{groupId}/invitations")
    public ApiResponse<Long> createInvitation(
            @PathVariable Long groupId,
            @Valid @RequestBody CreateInvitationRequest createInvitationRequest
    ) {
        return ApiResponse.ok(groupManagementService.createInvitation(groupId, createInvitationRequest));
    }

    /** 查询当前用户发出的所有邀请 */
    @GetMapping("/invitations/my-sent")
    public ApiResponse<List<MySentInvitationResponse>> listMySentInvitations() {
        return ApiResponse.ok(groupManagementService.listMySentInvitations());
    }

    /** 查询群组成员列表 */
    @GetMapping("/{groupId}/members")
    public ApiResponse<List<GroupMemberResponse>> listMembers(@PathVariable Long groupId) {
        return ApiResponse.ok(groupManagementService.listMembers(groupId));
    }

    /** 移除群组成员 */
    @DeleteMapping("/{groupId}/members/{userId}")
    public ApiResponse<Void> removeMember(@PathVariable Long groupId, @PathVariable Long userId) {
        groupManagementService.removeMember(groupId, userId);
        return ApiResponse.ok();
    }

    /** 退出群组 */
    @PostMapping("/{groupId}/leave")
    public ApiResponse<Void> leaveGroup(@PathVariable Long groupId) {
        groupManagementService.leaveGroup(groupId);
        return ApiResponse.ok();
    }
}
