package com.booksystem.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BookResponseDTO {
    private Long id;
    private Integer excelSiraNo;
    private String title;
    private String author;
    private String category; // Yeni eklenen alan
    private String status;

}