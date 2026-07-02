package com.booksystem.repository;

import com.booksystem.model.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

    @Query("SELECT b FROM Book b WHERE " +
            "(CAST(:title AS string) IS NULL OR LOWER(b.title) LIKE LOWER(CONCAT('%', CAST(:title AS string), '%'))) AND " +
            "(CAST(:author AS string) IS NULL OR LOWER(b.author.name) LIKE LOWER(CONCAT('%', CAST(:author AS string), '%')))")
    List<Book> searchBooks(@Param("title") String title, @Param("author") String author);
}
