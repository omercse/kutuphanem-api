package com.booksystem.repository;

import com.booksystem.model.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

    /**
     * Liste sayfasi. @EntityGraph sayesinde yazar ve kategori tek sorguda
     * JOIN ile geliyor. Bu olmadan 20 kitap icin 1 + 20 + 20 = 41 sorgu
     * atiliyordu (N+1 problemi).
     */
    @Override
    @EntityGraph(attributePaths = {"author", "category"})
    Page<Book> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"author", "category"})
    Optional<Book> findById(Long id);

    /**
     * Filtreli arama. Onceki hali List donduruyordu, yani 10.000 kitapta
     * hepsini belege yukluyordu; artik sayfali.
     */
    @EntityGraph(attributePaths = {"author", "category"})
    @Query("SELECT b FROM Book b WHERE " +
            "(:title IS NULL OR LOWER(b.title) LIKE LOWER(CONCAT('%', :title, '%'))) AND " +
            "(:author IS NULL OR LOWER(b.author.name) LIKE LOWER(CONCAT('%', :author, '%')))")
    Page<Book> searchBooks(@Param("title") String title,
                           @Param("author") String author,
                           Pageable pageable);

    /**
     * Excel aktariminda mukerrer kayit kontrolu icin.
     */
    boolean existsByTitleAndAuthorId(String title, Long authorId);
}