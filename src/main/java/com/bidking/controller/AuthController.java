package com.bidking.controller;

import com.bidking.annotation.RateLimit;
import com.bidking.dto.AuthRequest;
import com.bidking.dto.AuthResponse;
import com.bidking.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口
 * 登录/注册均受 IP 限流保护，防暴力破解和批量注册
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 注册（每60秒最多3次，按IP限流） */
    @RateLimit("register")
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody AuthRequest req) {
        return ResponseEntity.ok(authService.register(req));
    }

    /** 登录（每60秒最多5次，按IP限流） */
    @RateLimit("login")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }
}
