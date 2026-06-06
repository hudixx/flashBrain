package com.flashbrain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flashbrain.dto.AuthResponse;
import com.flashbrain.dto.LoginRequest;
import com.flashbrain.dto.RegisterRequest;
import com.flashbrain.dto.UserProfileResponse;
import com.flashbrain.entity.User;
import com.flashbrain.mapper.SnippetMapper;
import com.flashbrain.mapper.SubjectMapper;
import com.flashbrain.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class AuthService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SubjectMapper subjectMapper;

    @Autowired
    private SnippetMapper snippetMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = normalizeRequired(request.getUsername());
        String email = normalizeOptional(request.getEmail());
        ensureUsernameAvailable(username);
        if (StringUtils.hasText(email)) {
            ensureEmailAvailable(email);
        }

        boolean firstUser = userMapper.selectCount(null) == 0;
        LocalDateTime now = LocalDateTime.now();

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setDisplayName(normalizeOptional(request.getDisplayName()));
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEnabled(true);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);

        if (firstUser) {
            claimAnonymousData(user.getId());
        }

        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        String usernameOrEmail = normalizeRequired(request.getUsernameOrEmail());
        User user = findByUsernameOrEmail(usernameOrEmail);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled()) || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("用户名或密码错误");
        }
        return buildAuthResponse(user);
    }

    public UserProfileResponse getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            throw new RuntimeException("用户不存在或已禁用");
        }
        return toProfile(user);
    }

    private void ensureUsernameAvailable(String username) {
        QueryWrapper<User> query = new QueryWrapper<User>().eq("username", username);
        if (userMapper.selectCount(query) > 0) {
            throw new RuntimeException("用户名已存在");
        }
    }

    private void ensureEmailAvailable(String email) {
        QueryWrapper<User> query = new QueryWrapper<User>().eq("email", email);
        if (userMapper.selectCount(query) > 0) {
            throw new RuntimeException("邮箱已存在");
        }
    }

    private User findByUsernameOrEmail(String usernameOrEmail) {
        QueryWrapper<User> query = new QueryWrapper<User>()
                .eq("username", usernameOrEmail)
                .or()
                .eq("email", usernameOrEmail)
                .last("LIMIT 1");
        return userMapper.selectOne(query);
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtService.generateToken(user.getId(), user.getUsername());
        return new AuthResponse(token, "Bearer", jwtService.getExpirationSeconds(), toProfile(user));
    }

    private UserProfileResponse toProfile(User user) {
        return new UserProfileResponse(user.getId(), user.getUsername(), user.getEmail(), user.getDisplayName());
    }

    private void claimAnonymousData(Long userId) {
        subjectMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<com.flashbrain.entity.Subject>()
                .isNull("user_id")
                .set("user_id", userId));
        snippetMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<com.flashbrain.entity.Snippet>()
                .isNull("user_id")
                .set("user_id", userId));
    }

    private String normalizeRequired(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeOptional(String value) {
        String normalized = value == null ? null : value.trim();
        return StringUtils.hasText(normalized) ? normalized : null;
    }
}
