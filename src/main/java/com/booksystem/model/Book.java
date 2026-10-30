package com.booksystem.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "books",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_books_title_author",
                columnNames = {"title", "author_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer excelSiraNo;

    @Column(nullable = false)
    private String title;

    // LAZY: varsayilan EAGER, 20 kitaplik bir sayfa icin yazar ve kategoriyi
    // ayri ayri cekerek N+1 sorgu problemine yol aciyordu. Birlikte cekilmesi
    // gereken yerlerde BookRepository'deki @EntityGraph devreye giriyor.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private Author author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BookStatus status = BookStatus.OKUNMADI;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}