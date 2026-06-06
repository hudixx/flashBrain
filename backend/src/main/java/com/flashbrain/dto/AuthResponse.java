package com.flashbrain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String tokenType;
    private Long expiresIn;
    private UserProfileResponse user;
}
