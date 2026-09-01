package com.example.myargus.user.controller;

import com.example.myargus.user.entity.User;
import com.example.myargus.user.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class UserTestController {
    private final UserMapper userMapper;
    @Autowired
    public UserTestController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @GetMapping("/test/users")
    public List<User> ListUsers() {
        return userMapper.selectList(null);
    }
}
