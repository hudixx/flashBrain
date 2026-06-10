package com.flashbrain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flashbrain.dto.AuthResponse;
import com.flashbrain.dto.LoginRequest;
import com.flashbrain.dto.RegisterRequest;
import com.flashbrain.entity.User;
import com.flashbrain.mapper.SnippetMapper;
import com.flashbrain.mapper.SubjectMapper;
import com.flashbrain.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private SubjectMapper subjectMapper;

    @Mock
    private SnippetMapper snippetMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    void shouldRegisterUserWithEncodedPasswordAndToken() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(" alice ");
        request.setEmail("alice@example.com");
        request.setPassword("password123");
        when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(userMapper.selectCount(null)).thenReturn(0L);
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(jwtService.generateToken(any(), any(String.class))).thenReturn("token");
        when(jwtService.getExpirationSeconds()).thenReturn(86400L);

        AuthResponse response = authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User inserted = captor.getValue();
        assertThat(inserted.getUsername()).isEqualTo("alice");
        assertThat(inserted.getPasswordHash()).isEqualTo("encoded");
        assertThat(inserted.getPasswordHash()).isNotEqualTo("password123");
        assertThat(response.getToken()).isEqualTo("token");
        assertThat(response.getUser().getUsername()).isEqualTo("alice");
        verify(subjectMapper).update(any(), any());
        verify(snippetMapper).update(any(), any());
    }

    @Test
    void shouldRejectDuplicateUsername() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setPassword("password123");
        when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("用户名已存在");
    }

    @Test
    void shouldLoginWithPassword() {
        LoginRequest request = new LoginRequest();
        request.setUsernameOrEmail("alice");
        request.setPassword("password123");
        User user = new User();
        user.setId("5");
        user.setUsername("alice");
        user.setPasswordHash("encoded");
        user.setEnabled(true);
        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("password123", "encoded")).thenReturn(true);
        when(jwtService.generateToken("5", "alice")).thenReturn("token");
        when(jwtService.getExpirationSeconds()).thenReturn(86400L);

        AuthResponse response = authService.login(request);

        assertThat(response.getToken()).isEqualTo("token");
        assertThat(response.getUser().getId()).isEqualTo("5");
        assertThat(response.getUser().getUsername()).isEqualTo("alice");
    }

    @Test
    void shouldRejectWrongPassword() {
        LoginRequest request = new LoginRequest();
        request.setUsernameOrEmail("alice");
        request.setPassword("bad-password");
        User user = new User();
        user.setUsername("alice");
        user.setPasswordHash("encoded");
        user.setEnabled(true);
        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("bad-password", "encoded")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("用户名或密码错误");
    }
}
