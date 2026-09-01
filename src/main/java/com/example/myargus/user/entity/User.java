package com.example.myargus.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("users")
@Data
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userCode;
    private String username;
    private String email;
    private String displayName;
    private String passwordHash;
    private String systemRole;
    private String status;
    private Boolean mustChangePassword;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}