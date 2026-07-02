package com.booksystem.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookRequestDTO {

    @NotBlank(message = "Kitap adı boş veya sadece boşluklardan oluşamaz!")
    @Size(min = 2, max = 150, message = "Kitap adı en az 2, en fazla 150 karakter olmalıdır!")
    private String title;

    @NotBlank(message = "Yazar adı boş veya sadece boşluklardan oluşamaz!")
    @Size(min = 2, max = 100, message = "Yazar adı en az 2, en fazla 100 karakter olmalıdır!")
    private String author;

    @Min(value = 1, message = "Excel sıra numarası 1'den küçük olamaz!")
    private Integer excelSiraNo; // İsteğe bağlı alan, ama girilirse en az 1 olmalı

    private String categoryName;
}