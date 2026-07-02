package com.booksystem.service;

import java.util.List;

public interface AiService {
    // Tek bir metotta birden fazla kitabın kategorisini topluca isteyeceğiz
    List<String> detectCategoriesInBatch(List<String> bookTitlesWithAuthors);
}