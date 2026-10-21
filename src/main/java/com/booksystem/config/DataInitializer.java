package com.booksystem.config;

import com.booksystem.model.Role;
import com.booksystem.model.User;
import com.booksystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${application.bootstrap.admin.email:}")
    private String adminEmail;

    @Value("${application.bootstrap.admin.password:}")
    private String adminPassword;

    @Bean
    public CommandLineRunner seedAdminUser() {
        return args -> {
            log.info("DataInitializer calisti. adminEmail='{}'", adminEmail);

            if (adminEmail.isBlank() || adminPassword.isBlank()) {
                log.warn("ADMIN_EMAIL / ADMIN_PASSWORD tanimli degil, admin olusturulmadi.");
                return;
            }
            if (userRepository.findByEmail(adminEmail).isPresent()) {
                log.info("Admin zaten mevcut: {}", adminEmail);
                return;
            }

            User admin = User.builder()
                    .firstName("System")
                    .lastName("Admin")
                    .email(adminEmail)
                    .password(passwordEncoder.encode(adminPassword))
                    .role(Role.ADMIN)
                    .build();

            userRepository.save(admin);
            log.info("Baslangic ADMIN kullanicisi olusturuldu: {}", adminEmail);
        };
    }
}