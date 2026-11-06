package com.his.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.his.common.BizException;
import com.his.security.JwtService;
import com.his.security.UserPrincipal;
import com.his.system.SysUser;
import com.his.system.mapper.SysUserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long ttlMinutes;

    public AuthService(SysUserMapper userMapper,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       @org.springframework.beans.factory.annotation.Value("${his.jwt.ttl-minutes}") long ttlMinutes) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.ttlMinutes = ttlMinutes;
    }

    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request) {
        SysUser user = userMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, request.username()));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BizException("LOGIN_FAILED", "用户名或密码错误", HttpStatus.UNAUTHORIZED);
        }
        if (Boolean.FALSE.equals(user.getEnabled())) {
            throw new BizException("USER_DISABLED", "账号已停用", HttpStatus.FORBIDDEN);
        }
        UserPrincipal principal = new UserPrincipal(
                user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
        String token = jwtService.issue(principal);
        return new AuthDtos.LoginResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole(),
                LocalDateTime.now().plusMinutes(ttlMinutes));
    }
}
