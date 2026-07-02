package com.booksystem.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class ErrorResponse {
    private int status;                   // HTTP Durum Kodu (Örn: 400, 404)
    private String error;                 // Hata Türü (Örn: Bad Request, Not Found)
    private String message;               // Genel Hata Mesajı
    private LocalDateTime timestamp;      // Hatanın Oluştuğu Zaman
    private Map<String, String> errors;   // Eğer varsa, validation (doğrulama) detayları
}