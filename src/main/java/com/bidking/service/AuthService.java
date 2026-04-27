package com.bidking.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidking.dto.AuthRequest;
import com.bidking.dto.AuthResponse;
import com.bidking.entity.Player;
import com.bidking.mapper.PlayerMapper;
import com.bidking.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 玩家注册/登录，JWT 签发
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final PlayerMapper playerMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthResponse register(AuthRequest req) {
        if (playerMapper.selectOne(new LambdaQueryWrapper<Player>()
                .eq(Player::getUsername, req.getUsername())) != null) {
            throw new IllegalArgumentException("用户名已存在");
        }
        Player player = new Player();
        player.setUsername(req.getUsername());
        player.setNickname(req.getNickname() != null ? req.getNickname() : req.getUsername());
        player.setPassword(passwordEncoder.encode(req.getPassword()));
        playerMapper.insert(player);
        log.info("[Auth] 注册成功 username={} playerId={}", player.getUsername(), player.getId());
        String token = jwtUtil.generate(player.getId(), player.getUsername());
        return new AuthResponse(token, player.getId(), player.getUsername(), player.getNickname());
    }

    public AuthResponse login(AuthRequest req) {
        Player player = playerMapper.selectOne(new LambdaQueryWrapper<Player>()
                .eq(Player::getUsername, req.getUsername()));
        if (player == null || !passwordEncoder.matches(req.getPassword(), player.getPassword())) {
            log.warn("[Auth] 登录失败 username={}", req.getUsername());
            throw new IllegalArgumentException("用户名或密码错误");
        }
        log.info("[Auth] 登录成功 username={} playerId={}", player.getUsername(), player.getId());
        String token = jwtUtil.generate(player.getId(), player.getUsername());
        return new AuthResponse(token, player.getId(), player.getUsername(), player.getNickname());
    }
}
