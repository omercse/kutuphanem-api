package com.booksystem.exception;

import com.booksystem.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice // Bu anotasyon projedeki tüm controller'ların hatalarını buraya bağlar
public class GlobalExceptionHandler {

    /**
     * SENARYO 1: `@Valid` kurallarına uymayan veriler geldiğinde tetiklenir (HTTP 400)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> validationErrors = new HashMap<>();

        // Tüm hataları dönüp hangi alanın hangi mesajı tetiklediğini haritalıyoruz
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            validationErrors.put(fieldName, errorMessage);
        });

        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Giriş Validasyon Hatası")
                .message("Gönderilen veriler kütüphane kurallarına uymuyor!")
                .timestamp(LocalDateTime.now())
                .errors(validationErrors) // Detaylı temiz mesajlar buraya gidiyor
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * SENARYO 2: Kitap bulunamadığında veya iş mantığı hatalarında fırlattığımız RuntimeException'ları yakalar
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;

        // Eğer mesajda "bulunamadı" geçiyorsa HTTP kodunu daha semantik olan 404 Not Found yapalım
        if (ex.getMessage().contains("bulunamadı")) {
            status = HttpStatus.NOT_FOUND;
        }

        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();

        return new ResponseEntity<>(errorResponse, status);
    }

    // Sınıfın en altına bu metodu ekle:
    /**
     * SENARYO 3: Çok büyük Excel dosyası yüklenmeye çalışıldığında Tomcat'in fırlattığı hatayı yakalar (HTTP 413)
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxSizeException(MaxUploadSizeExceededException exc) {

        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.PAYLOAD_TOO_LARGE.value()) // HTTP 413 Payload Too Large
                .error("Dosya Boyutu Çok Büyük")
                .message("Yüklemeye çalıştığınız Excel dosyası sistemin izin verdiği maksimum boyutu aşıyor. Lütfen dosyanızı küçültüp tekrar deneyin.")
                .timestamp(LocalDateTime.now())
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.PAYLOAD_TOO_LARGE);
    }
}