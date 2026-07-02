package com.booksystem.service;

import com.booksystem.dto.BookRequestDTO;
import com.booksystem.dto.BookResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IBookService {
    // 1. Excel'den toplu kitap yükleme
// Parametreyi byte[] olarak değiştir
    void uploadBooksFromExcel(byte[] fileBytes);
    // 2. Tüm kitapları sayfa sayfa (Paginated) listeleme
    Page<BookResponseDTO> getAllBooks(Pageable pageable);

    // 3. Kitap adı ve/veya yazar adına göre dinamik filtreleme
    List<BookResponseDTO> searchBooks(String title, String author);

    // 4. Tek bir kitap ekleme
    BookResponseDTO saveBook(BookRequestDTO requestDTO);

    // 5. Kitap okuma durumunu güncelleme
    BookResponseDTO updateBookStatus(Long id, String status);

    BookResponseDTO updateBookCategory(Long id, String categoryName);

    BookResponseDTO updateBook(Long id, BookRequestDTO requestDTO);

    // com.booksystem.service.BookService arayüzünün içine ekleyin:
    java.io.ByteArrayInputStream exportBooksToExcel();

    void deleteBook(Long id);
}