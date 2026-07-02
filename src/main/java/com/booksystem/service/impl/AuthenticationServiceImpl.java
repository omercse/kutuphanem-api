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
        // Yeni kullanıcıyı oluştur ve şifresini BCrypt ile şifrele
        var user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.ADMIN) // Varsayılan olarak herkes normal kullanıcı (USER) başlar
                .build();

        userRepository.save(user);

        // Kullanıcıya özel JWT Token üret
        var jwtToken = jwtService.generateToken(user);
        return AuthenticationResponse.builder()
                .token(jwtToken)
                .role(user.getRole().name()) // 🔥 SİHİRLİ DOKUNUŞ: Rolü pakete koyduk
                .build();    }

    @Override
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        // Kullanıcı adı ve şifreyi doğrula (Yanlışsa burada Exception fırlar)
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        // Kullanıcıyı veritabanından çek
        var user = userRepository.findByEmail(request.getEmail())
                .orElseThrow();

        // Yeni bir token üret ve dön
        var jwtToken = jwtService.generateToken(user);
        return AuthenticationResponse.builder()
                .token(jwtToken)
                .role(user.getRole().name()) // 🔥 SİHİRLİ DOKUNUŞ: Rolü pakete koyduk
                .build();    }
}