package com.flashbrain.service;

import com.flashbrain.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    @Test
    void shouldGenerateAndParseToken() {
        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", "flashbrain-test-secret-flashbrain-test-secret");
        ReflectionTestUtils.setField(jwtService, "expirationSeconds", 3600L);

        String token = jwtService.generateToken("7", "alice");
        UserPrincipal principal = jwtService.parseUser(token);

        assertThat(principal.getId()).isEqualTo("7");
        assertThat(principal.getUsername()).isEqualTo("alice");
        assertThat(jwtService.getExpirationSeconds()).isEqualTo(3600L);
    }
}
