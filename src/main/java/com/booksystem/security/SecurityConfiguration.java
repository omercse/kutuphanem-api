package com.booksystem.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll() // Kayıt/Giriş HERKESE AÇIK
                        .requestMatchers("/", "/index.html", "/favicon.ico").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll() // Docker healthcheck

                        // 🔥 1. OKUMA İŞLEMLERİ: Hem USER hem ADMIN yapabilir
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/books/**").hasAnyRole("USER", "ADMIN")

                        // 🔥 2. İSTİSNA (DURUM DEĞİŞTİRME): Hem USER hem ADMIN yapabilir
                        .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/v1/books/*/status").hasAnyRole("USER", "ADMIN")

                        // 🔥 3. TEHLİKELİ İŞLEMLER: SADECE ADMIN YAPABİLİR (Ekleme, Silme, Excel)
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/books/**").hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/v1/books/**").hasRole("ADMIN") // İstisna dışındaki tüm PUT'lar
                        .requestMatchers(org.springframework.http.HttpMethod.PATCH, "/api/v1/books/**").hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/v1/books/**").hasRole("ADMIN")

                        .anyRequest().authenticated()
                )
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}