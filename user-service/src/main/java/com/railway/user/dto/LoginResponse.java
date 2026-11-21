package com.railway.user.dto;

public record LoginResponse(String token, String tokenType, Long userId, String name, String role) {
}
