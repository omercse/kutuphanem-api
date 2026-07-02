package com.booksystem.security;

import com.booksystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
public class ApplicationConfig {

    private final UserRepository userRepository;

    // Veritabanından kullanıcıyı e-posta ile arayıp bulma işlemi
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("Kullanıcı bulunamadı!"));
    }

    // Şifre kontrolünü yapacak olan sağlayıcı (Provider)
    // Şifre kontrolünü yapacak olan sağlayıcı (Provider)
    @Bean
    public AuthenticationProvider authenticationProvider() {
        // ARTIK userDetailsService'i sonradan değil, doğrudan parantez içine (Constructor'a) veriyoruz!
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService());

        // Bu metod henüz silinmedi, kullanılmaya devam ediliyor:
        authProvider.setPasswordEncoder(passwordEncoder());

        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // Şifreleri açık metin (123456) yerine kriptolanmış (BCrypt) olarak tutmamızı sağlayan encoder
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}