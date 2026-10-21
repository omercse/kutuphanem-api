package com.booksystem.service.impl;

import com.booksystem.dto.auth.AuthenticationRequest;
import com.booksystem.dto.auth.AuthenticationResponse;
import com.booksystem.dto.auth.RegisterRequest;
import com.booksystem.model.Role;
import com.booksystem.model.User;
import com.booksystem.repository.UserRepository;
import com.booksystem.security.JwtService;
import com.booksystem.service.IAuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements IAuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Override
    public AuthenticationResponse register(RegisterRequest request) {
        // Disaridan kayit olan herkes HER ZAMAN en dusuk yetkiyle baslar.
        // ADMIN yetkisi yalnizca DataInitializer tarafindan (ADMIN_EMAIL /
        // ADMIN_PASSWORD env degiskenleri ile) veya mevcut bir ADMIN tarafindan
        // verilebilir. Bu satirin USER disinda bir deger almasi, kayit olan
        // herkese silme ve toplu yukleme yetkisi vermek demektir.
        var user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .build();

        userRepository.save(user);

        var jwtToken = jwtService.generateToken(user);
        return AuthenticationResponse.builder()
                .token(jwtToken)
                .role(user.getRole().name())
                .build();
    }

    @Override
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        // Kullanici adi ve sifreyi dogrula (yanlissa buradan exception firlar)
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        var user = userRepository.findByEmail(request.getEmail())
                .orElseThrow();

        var jwtToken = jwtService.generateToken(user);
        return AuthenticationResponse.builder()
                .token(jwtToken)
                .role(user.getRole().name())
                .build();
    }
}