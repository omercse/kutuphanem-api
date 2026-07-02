package com.booksystem.controller;

import com.booksystem.dto.auth.AuthenticationRequest;
import com.booksystem.dto.auth.AuthenticationResponse;
import com.booksystem.dto.auth.RegisterRequest;
import com.booksystem.service.IAuthenticationService; // Arayüzü import ettik
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    // Artık Impl sınıfına değil, arayüze (Interface) bağımlıyız (Loose Coupling)
    private final IAuthenticationService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthenticationResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/authenticate")
    public ResponseEntity<AuthenticationResponse> authenticate(@RequestBody AuthenticationRequest request) {
        return ResponseEntity.ok(authService.authenticate(request));
    }
}