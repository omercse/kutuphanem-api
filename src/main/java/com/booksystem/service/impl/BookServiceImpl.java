package com.booksystem.service.impl;

import com.booksystem.dto.BookRequestDTO;
import com.booksystem.dto.BookResponseDTO;
import com.booksystem.model.Author;
import com.booksystem.model.Book;
import com.booksystem.model.Category;
import com.booksystem.repository.AuthorRepository;
import com.booksystem.repository.BookRepository;
import com.booksystem.repository.CategoryRepository;
import com.booksystem.service.AiService;
import com.booksystem.service.IBookService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j  // 🔥 EKSİK OLAN SİHİRLİ ANOTASYON BU
public class BookServiceImpl implements IBookService {

    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;
    private final CategoryRepository categoryRepository;

    private final AiService aiService;

    // Türkçe karakter dönüşümleri için ortak Locale nesnesi
    private final Locale trLocale = Locale.forLanguageTag("tr-TR");

    /**
     * 1. EXCEL'DEN TOPLU KİTAP YÜKLEME METODU
     * Performans için yazar önbelleği (Cache) kullanır ve verileri normalleştirir.
     */
    @Override
    @Async // 🔥 Metodun başında bu anotasyonun kesinlikle olduğundan emin ol!
    public void uploadBooksFromExcel(byte[] fileBytes) {
        try (InputStream is = new ByteArrayInputStream(fileBytes);
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            List<Book> tempBookList = new ArrayList<>();
            List<String> aiInputList = new ArrayList<>();
            DataFormatter dataFormatter = new DataFormatter();

            Map<String, Author> authorCache = new HashMap<>();
            authorRepository.findAll().forEach(a -> authorCache.put(a.getName(), a));

            Map<String, Category> categoryCache = new HashMap<>();
            categoryRepository.findAll().forEach(c -> categoryCache.put(c.getName(), c));

            // 1. AŞAMA: Tüm Excel verisini hafızaya alıp hazırlıyoruz
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row)) continue;

                Book book = new Book();

                if (row.getCell(0) != null) {
                    try {
                        String numStr = dataFormatter.formatCellValue(row.getCell(0)).trim();
                        if (!numStr.isEmpty()) {
                            book.setExcelSiraNo(Integer.parseInt(numStr));
                        }
                    } catch (NumberFormatException ignored) {
                        // Geçersiz sıra numaralarında sessizce atla
                    }
                }

                String title = "";
                if (row.getCell(1) != null) {
                    title = dataFormatter.formatCellValue(row.getCell(1)).trim().toUpperCase(trLocale);
                }
                book.setTitle(title);

                String authorName = "BİLİNMİYOR";
                if (row.getCell(2) != null && !row.getCell(2).toString().trim().isEmpty()) {
                    authorName = dataFormatter.formatCellValue(row.getCell(2)).trim().toUpperCase(trLocale);
                }

                String finalAuthorName = authorName;
                Author author = authorCache.computeIfAbsent(finalAuthorName, name ->
                        authorRepository.save(Author.builder().name(finalAuthorName).build())
                );
                book.setAuthor(author);
                book.setStatus("OKUNMADI");

                tempBookList.add(book);
                aiInputList.add(title + " - " + authorName);
            }

            // ════════════════════════════════════════════════════════════════
            // 2. AŞAMA: Kitapları 10'arlı paketler halinde AI'a gönderip
            // ANINDA veritabanına kaydediyoruz (Arayüz canlı canlı görsün diye)
            // ════════════════════════════════════════════════════════════════
            int batchSize = 10;

            for (int i = 0; i < tempBookList.size(); i += batchSize) {
                int end = Math.min(i + batchSize, tempBookList.size());

                List<String> batchInput = aiInputList.subList(i, end);
                List<Book> batchBooks = tempBookList.subList(i, end);

                // Yapay Zekadan 10'luk paketin kategorilerini alıyoruz
                List<String> batchCategories = aiService.detectCategoriesInBatch(batchInput);

                // Kategorileri kitaplara atıyoruz
                for (int j = 0; j < batchBooks.size(); j++) {
                    String categoryName = "GENEL";
                    if (j < batchCategories.size()) {
                        categoryName = batchCategories.get(j);
                    }

                    String finalCategoryName = categoryName;
                    Category category = categoryCache.computeIfAbsent(finalCategoryName, name ->
                            categoryRepository.save(Category.builder().name(finalCategoryName).build())
                    );
                    batchBooks.get(j).setCategory(category);
                }

                // 🔥 KRİTİK NOKTA: 10'luk paket hazır olur olmaz ANINDA veritabanına kaydediyoruz!
                // Böylece arayüz (Frontend) 5 dakika beklemez, kitaplar tabloya parti parti düşer.
                bookRepository.saveAll(batchBooks);

                // Google'dan 503 (Yoğunluk) hatası yememek için paketler arası 20 saniye mola veriyoruz
                if (end < tempBookList.size()) {
                    try {
                        log.info("Google kotalarını aşmamak için 20 saniye bekleniyor... (Mevcut ilerleme: {}/{})", end, tempBookList.size());
                        Thread.sleep(20000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.error("Arka plan thread'i kesintiye uğradı: " + e.getMessage());
                    }
                }
            }

            // ════════════════════════════════════════════════════════════════
            // 3. AŞAMA: İşlem Bitişi
            // ════════════════════════════════════════════════════════════════
            // Artık burada saveAll() yapmıyoruz çünkü 2. Aşamada parti parti kaydettik.
            if (!tempBookList.isEmpty()) {
                log.info("Arka plan Excel işleme operasyonu BAŞARIYLA TAMAMLANDI! Toplam {} adet kitap sisteme işlendi.", tempBookList.size());
            }

        } catch (Exception e) {
            log.error("Arka planda Excel işlenirken kritik bir hata meydana geldi: ", e);
        }
    }


    /**
     * 2. TÜM KİTAPLARI SAYFA SAYFA LİSTELEME METODU
     */
    @Override
    public Page<BookResponseDTO> getAllBooks(Pageable pageable) {
        return bookRepository.findAll(pageable).map(this::mapToResponseDTO);
    }

    /**
     * 3. TÜRKÇE KARAKTER UYUMLU DİNAMİK ARAMA/FİLTRELEME METODU
     */
    @Override
    public List<BookResponseDTO> searchBooks(String title, String author) {
        String titleParam = (title != null && !title.trim().isEmpty()) ? title.trim().toUpperCase(trLocale) : null;
        String authorParam = (author != null && !author.trim().isEmpty()) ? author.trim().toUpperCase(trLocale) : null;

        return bookRepository.searchBooks(titleParam, authorParam).stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * 4. TEK BİR KİTAP EKLEME METODU
     * Eklenen yazarın veritabanında mükerrer kayıt oluşturmasını engeller.
     */
    /**
     * 4. TEK BİR KİTAP EKLEME METODU (Yapay Zeka Destekli)
     */
    @Override
    @Transactional
    public BookResponseDTO saveBook(BookRequestDTO requestDTO) {
        String formattedTitle = requestDTO.getTitle().trim().toUpperCase(trLocale);
        String formattedAuthor = requestDTO.getAuthor().trim().toUpperCase(trLocale);

        // 1. Yazar kontrolü (Varsa getir, yoksa oluştur)
        Author author = authorRepository.findByName(formattedAuthor)
                .orElseGet(() -> authorRepository.save(Author.builder().name(formattedAuthor).build()));

        // 2. 🔥 YAPAY ZEKA İLE KATEGORİ TESPİTİ (Tekil İstek)
        // Toplu istek atan metodumuza, sadece 1 eleman barındıran bir liste (List.of) gönderiyoruz
        List<String> aiInput = List.of(formattedTitle + " - " + formattedAuthor);
        List<String> aiResult = aiService.detectCategoriesInBatch(aiInput);

        // Eğer yapay zeka cevap döndüyse ilk elemanı (0. index) al, yoksa GENEL ata
        String detectedCategoryName = (aiResult != null && !aiResult.isEmpty()) ? aiResult.get(0) : "GENEL";

        // 3. Kategoriyi DB'den bul veya yeni oluştur
        Category category = categoryRepository.findByName(detectedCategoryName)
                .orElseGet(() -> categoryRepository.save(Category.builder().name(detectedCategoryName).build()));

        // 4. Kitabı inşa et ve kaydet
        Book book = Book.builder()
                .title(formattedTitle)
                .author(author)
                .category(category) // Artık "GENEL" değil, yapay zekanın bulduğu kategori atanıyor!
                .excelSiraNo(requestDTO.getExcelSiraNo())
                .status("OKUNMADI")
                .build();

        Book savedBook = bookRepository.save(book);
        return mapToResponseDTO(savedBook);
    }

    /**
     * 5. KİTAP DURUMUNU GÜNCELLEME METODU
     */
    @Override
    @Transactional
    public BookResponseDTO updateBookStatus(Long id, String status) {
        // 1. Kitabı bul, yoksa standart EntityNotFoundException fırlat
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Hata: " + id + " ID'li kitap bulunamadı!"));

        // 2. Boşluk (Null) kontrolü
        if (status == null || status.trim().isEmpty()) {
            throw new IllegalArgumentException("Hata: Durum bilgisi boş olamaz!");
        }

        // 3. Türkçe karakter uyumlu formatlama
        Locale trLocale = new Locale("tr", "TR");
        String formattedStatus = status.trim().toUpperCase(trLocale);

        // 🔥 4. SİHİRLİ DOKUNUŞ (Validasyon): Sadece izin verilen durumların girilmesini sağla
        List<String> validStatuses = Arrays.asList("OKUNMADI", "OKUNUYOR", "OKUNDU");
        if (!validStatuses.contains(formattedStatus)) {
            throw new IllegalArgumentException("Hata: Geçersiz durum bilgisi! Kabul edilen değerler: OKUNMADI, OKUNUYOR, OKUNDU");
        }

        // 5. Güncelle ve Kaydet
        book.setStatus(formattedStatus);
        Book updatedBook = bookRepository.save(book);

        // 6. DTO'ya çevirip arayüze (Frontend'e) döndür
        return mapToResponseDTO(updatedBook);
    }

    /**
     * YARDIMCI METOT: Book nesnesini BookResponseDTO'ya eşler (Mapping)
     */
    private BookResponseDTO mapToResponseDTO(Book book) {
        return BookResponseDTO.builder()
                .id(book.getId())
                .excelSiraNo(book.getExcelSiraNo())
                .title(book.getTitle())
                .author(book.getAuthor() != null ? book.getAuthor().getName() : "YAZAR BİLİNMİYOR")
                .category(book.getCategory() != null ? book.getCategory().getName() : "GENEL") // Güncellenen kısım
                .status(book.getStatus())
                .build();
    }



    @Override
    @Transactional
    public BookResponseDTO updateBookCategory(Long id, String categoryName) {
        // 1. Kitabı veritabanından bul, yoksa hata fırlat
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Hata: " + id + " ID'li kitap bulunamadı!"));

        if (categoryName == null || categoryName.trim().isEmpty()) {
            throw new IllegalArgumentException("Hata: Kategori adı boş olamaz!");
        }

        // 2. Gelen kategori adını Türkçe büyük harfe çevir (Örn: roman -> ROMAN)
        String formattedCategoryName = categoryName.trim().toUpperCase(trLocale);

        // 3. Akıllı Kategori Yönetimi: DB'de varsa getir, yoksa sıfırdan oluşturup kaydet
        Category category = categoryRepository.findByName(formattedCategoryName)
                .orElseGet(() -> categoryRepository.save(Category.builder().name(formattedCategoryName).build()));

        // 4. Kitabın kategorisini güncelle ve kaydet
        book.setCategory(category);
        Book updatedBook = bookRepository.save(book);

        // 5. Güncel veriyi DTO'ya map'leyip dön
        return mapToResponseDTO(updatedBook);
    }

    @Override
    @Transactional
    public BookResponseDTO updateBook(Long id, BookRequestDTO requestDTO) {
        // 1. Kitabı veritabanından bul, yoksa hata fırlat
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Hata: " + id + " ID'li kitap bulunamadı!"));

        Locale trLocale = new Locale("tr", "TR");
        boolean needsCategoryUpdate = false;
        String newTitle = book.getTitle();
        String newAuthorName = book.getAuthor() != null ? book.getAuthor().getName() : "BİLİNMİYOR";

        // 2. Kitap Adı Güncelleme
        if (requestDTO.getTitle() != null && !requestDTO.getTitle().trim().isEmpty()) {
            newTitle = requestDTO.getTitle().trim().toUpperCase(trLocale);
            if (!newTitle.equals(book.getTitle())) {
                book.setTitle(newTitle);
                needsCategoryUpdate = true; // İsim değişti
            }
        }

        // 3. Yazar Güncelleme
        if (requestDTO.getAuthor() != null && !requestDTO.getAuthor().trim().isEmpty()) {
            newAuthorName = requestDTO.getAuthor().trim().toUpperCase(trLocale);
            if (book.getAuthor() == null || !newAuthorName.equals(book.getAuthor().getName())) {
                String finalNewAuthorName = newAuthorName;
                Author author = authorRepository.findByName(newAuthorName)
                        .orElseGet(() -> authorRepository.save(Author.builder().name(finalNewAuthorName).build()));
                book.setAuthor(author);
                needsCategoryUpdate = true; // Yazar değişti
            }
        }

        // 4. Sıra No Güncelleme
        if (requestDTO.getExcelSiraNo() != null) {
            book.setExcelSiraNo(requestDTO.getExcelSiraNo());
        }

        // 5. 🔥 KATEGORİ GÜNCELLEMESİ (İnsan Kararı vs. Yapay Zeka Kararı)

        // A DURUMU: Kullanıcı arayüzden bir kategori seçtiyse ("AI" dışında), İnsanın Kararı Geçerlidir!
        if (requestDTO.getCategoryName() != null && !requestDTO.getCategoryName().trim().isEmpty() && !requestDTO.getCategoryName().equalsIgnoreCase("AI")) {

            String manualCategoryName = requestDTO.getCategoryName().trim().toUpperCase(trLocale);
            Category category = categoryRepository.findByName(manualCategoryName)
                    .orElseGet(() -> categoryRepository.save(Category.builder().name(manualCategoryName).build()));

            book.setCategory(category);
            log.info("Kitap güncellendi, Kullanıcı (Manuel) kategori atadı: {}", manualCategoryName);

        }
        // B DURUMU: Arayüzden kategori gelmediyse ama isim/yazar değiştiyse (Eski senin yazdığın AI Mantığı)
        else if (needsCategoryUpdate) {
            List<String> aiInput = List.of(newTitle + " - " + newAuthorName);
            List<String> aiResult = aiService.detectCategoriesInBatch(aiInput);

            String detectedCategoryName = (aiResult != null && !aiResult.isEmpty()) ? aiResult.get(0) : "GENEL";
            Category category = categoryRepository.findByName(detectedCategoryName)
                    .orElseGet(() -> categoryRepository.save(Category.builder().name(detectedCategoryName).build()));

            book.setCategory(category);
            log.info("Kitap güncellendi, AI yeni kategori atadı: {}", detectedCategoryName);
        }

        // 6. Değişiklikleri kaydet ve DTO olarak dön
        Book updatedBook = bookRepository.save(book);
        return mapToResponseDTO(updatedBook);
    }

    @Override
    @Transactional
    public void deleteBook(Long id) {
        // Kitap var mı kontrol et, yoksa hata fırlat
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Hata: " + id + " ID'li kitap bulunamadı!"));

        // Kitabı sil
        bookRepository.delete(book);
    }


    /**
     * YARDIMCI METOT: Boş görünen ama içi boşluk dolu Excel satırlarını yakalar
     */
    private boolean isRowEmpty(Row row) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }

    @Override
    public java.io.ByteArrayInputStream exportBooksToExcel() {
        // Tüm kitapları ID sırasına göre veritabanından çekiyoruz
        List<Book> books = bookRepository.findAll();

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Güncel Kitap Listesi");

            // 1. Stil Ayarları: Başlık satırı için kalın yazı tipi oluşturuyoruz
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.BLACK.getIndex());

            CellStyle headerCellStyle = workbook.createCellStyle();
            headerCellStyle.setFont(headerFont);
            headerCellStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerCellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // 2. Başlık Satırını Oluşturma
            Row headerRow = sheet.createRow(0);
            String[] columns = {"ID", "EXCEL SIRA NO", "KİTAP ADI", "YAZAR", "KATEGORİ", "DURUM"};

            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerCellStyle);
            }

            // 3. Veri Satırlarını Doldurma
            int rowIdx = 1;
            for (Book book : books) {
                Row row = sheet.createRow(rowIdx++);

                row.createCell(0).setCellValue(book.getId());
                row.createCell(1).setCellValue(book.getExcelSiraNo() != null ? book.getExcelSiraNo() : 0);
                row.createCell(2).setCellValue(book.getTitle());
                row.createCell(3).setCellValue(book.getAuthor() != null ? book.getAuthor().getName() : "BİLİNMİYOR");
                row.createCell(4).setCellValue(book.getCategory() != null ? book.getCategory().getName() : "GENEL");
                row.createCell(5).setCellValue(book.getStatus());
            }

            // 4. Optimizasyon: Sütun genişliklerini içeriğe göre otomatik sığdır
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());

        } catch (IOException e) {
            throw new RuntimeException("Excel dosyası oluşturulurken bir hata meydana geldi: " + e.getMessage());
        }
    }
}