package com.booksystem.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthenticationResponse {
    private String token;
    // 🔥 Giriş yapan kişinin yetkisini (ADMIN/USER) arayüze bildirmek için eklendi
    private String role;
}