package com.booksystem.controller;

import com.booksystem.dto.BookRequestDTO;
import com.booksystem.dto.BookResponseDTO;
import com.booksystem.service.IBookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/books") // Versiyonlama (v1) iyi bir pratiktir
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // 🔥 DIŞARIDAN GELEN TARAYICI İSTEKLERİNE İZİN VERİR
public class BookController {

    // Interface üzerinden enjeksiyon (Loose Coupling)
    private final IBookService bookService;


    /**
     * Excel dosyasını alıp veritabanına kaydetmek için kullanılır.
     * Postman'de 'form-data' içinde 'file' anahtarıyla gönderilmeli.
     */
    @PostMapping("/upload")
    public ResponseEntity<String> uploadExcel(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Hata: Lütfen geçerli bir Excel dosyası seçin.");
        }

        try {
            // Dosyanın içindeki byteları alıp arka plan servisine gönderiyoruz
            bookService.uploadBooksFromExcel(file.getBytes());

            // Kullanıcıyı bekletmeden anında 202 ACCEPTED dönüyoruz
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body("Excel dosyanız başarıyla kuyruğa alındı. Arka planda işleniyor...");
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Dosya okunamadı.");
        }
    }



    /**
     * Veritabanındaki tüm kitapları listeler.
     */
    /**
     * Veritabanındaki tüm kitapları sayfa sayfa (Paginated) listeler.
     * Örnek: GET http://localhost:8080/api/v1/books?page=0&size=20&sortBy=title
     */
    @GetMapping
    public ResponseEntity<Page<BookResponseDTO>> getAllBooks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy) {

        // Sayfalama nesnesini oluşturuyoruz
        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by(sortBy));

        Page<BookResponseDTO> books = bookService.getAllBooks(pageable);
        return ResponseEntity.ok(books);
    }


    @PutMapping("/{id}/status")
    public ResponseEntity<BookResponseDTO> updateBookStatus(
            @PathVariable Long id,
            @RequestParam("status") String status) {

        BookResponseDTO updatedBook = bookService.updateBookStatus(id, status);
        return ResponseEntity.ok(updatedBook);
    }

    /**
     * Kitapları adına ve/veya yazarına göre filtreler.
     * Parametrelerin ikisi de zorunlu değildir (required = false).
     */
    @GetMapping("/search")
    public ResponseEntity<List<BookResponseDTO>> searchBooks(
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "author", required = false) String author) {

        List<BookResponseDTO> filteredBooks = bookService.searchBooks(title, author);
        return ResponseEntity.ok(filteredBooks);
    }

    /**
     * Veritabanına tek bir kitap eklemek için kullanılır.
     */
    @PostMapping
    public ResponseEntity<BookResponseDTO> createBook(@Valid @RequestBody BookRequestDTO bookRequestDTO) {
        BookResponseDTO createdBook = bookService.saveBook(bookRequestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdBook);
    }


    /**
     * Belirli bir kitabın kategorisini günceller veya yeni kategori atar.
     * Örnek URL: PATCH http://localhost:8080/api/v1/books/1/category?name=Roman
     */
    @PatchMapping("/{id}/category")
    public ResponseEntity<BookResponseDTO> updateBookCategory(
            @PathVariable Long id,
            @RequestParam("name") String categoryName) {

        BookResponseDTO updatedBook = bookService.updateBookCategory(id, categoryName);
        return ResponseEntity.ok(updatedBook);
    }

    /**
     * Mevcut bir kitabın bilgilerini (Adı, Yazarı, Sıra No) genel olarak günceller.
     * URL: PUT http://localhost:8080/api/v1/books/1
     */
    @PutMapping("/{id}")
    public ResponseEntity<BookResponseDTO> updateBook(
            @PathVariable Long id,
            @Valid @RequestBody BookRequestDTO bookRequestDTO) {

        BookResponseDTO updatedBook = bookService.updateBook(id, bookRequestDTO);
        return ResponseEntity.ok(updatedBook);
    }

    /**
     * Belirli bir kitabı veritabanından kalıcı olarak siler.
     * URL: DELETE http://localhost:8080/api/v1/books/1
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBook(@PathVariable Long id) {
        bookService.deleteBook(id);
        return ResponseEntity.noContent().build(); // Silme işlemlerinde HTTP 204 No Content dönmek standarttır
    }

    /**
     * Veritabanındaki güncel kitap listesini Excel (.xlsx) dosyası olarak indirir.
     * URL: GET http://localhost:8080/api/v1/books/export
     */
    @GetMapping("/export")
    public ResponseEntity<Resource> exportBooksToExcel() {
        String filename = "guncel_kutuphane_listesi.xlsx";

        InputStreamResource file = new InputStreamResource(bookService.exportBooksToExcel());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file);
    }


}