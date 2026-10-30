# Adım 2b — Veri modeli iyileştirmeleri

Dört değişiklik: enum, LAZY fetch, denetim alanları, mükerrer kayıt kısıtı.

---

## 1. Yeni dosyaları ekle

| Dosya | Hedef |
|---|---|
| `BookStatus.java` | `com.booksystem.model` paketine, **yeni** |
| `Book.java` | üzerine yaz |
| `BookRepository.java` | üzerine yaz |
| `V3__refine_book_model.sql` | `src/main/resources/db/migration/`, **yeni** |

---

## 2. BookServiceImpl.java — altı yerde düzeltme

`Book.status` artık `String` değil `BookStatus`, derleme hataları çıkacak. Her birini sırayla düzelt.

**a) Import ekle** (dosyanın başına, diğer `model` importlarının yanına):

```java
import com.booksystem.model.BookStatus;
```

**b) ~101. satır** (Excel aktarımı içinde):

```java
book.setStatus("OKUNMADI");
```
→
```java
book.setStatus(BookStatus.OKUNMADI);
```

**c) ~222. satır** (`addBook` içindeki builder):

```java
.status("OKUNMADI")
```
→
```java
.status(BookStatus.OKUNMADI)
```

**d) `updateBookStatus` metodunun gövdesi** — `Book book = ...` satırından sonrasını tamamen şununla değiştir:

```java
        BookStatus newStatus = BookStatus.fromString(status);

        book.setStatus(newStatus);
        Book updatedBook = bookRepository.save(book);

        return mapToResponseDTO(updatedBook);
    }
```

Böylece boşluk kontrolü, Türkçe büyük harf dönüşümü ve geçerli değer listesi tek bir yere, enum'ın içine taşınmış oluyor. Metodun içindeki `Locale trLocale`, `formattedStatus` ve `validStatuses` satırları gidiyor.

**e) `mapToResponseDTO` içinde:**

```java
.status(book.getStatus())
```
→
```java
.status(book.getStatus().name())
```

`BookResponseDTO.status` alanı `String` kalıyor, arayüzün beklediği format değişmesin diye.

**f) ~438. satır** (Excel dışa aktarımı):

```java
row.createCell(5).setCellValue(book.getStatus());
```
→
```java
row.createCell(5).setCellValue(book.getStatus().name());
```

---

## 3. searchBooks artık sayfalı

`BookRepository.searchBooks` imzası `List` yerine `Page` döndürüyor ve `Pageable` alıyor. Buna bağlı olarak:

**IBookService.java** — mevcut `searchBooks` imzasını bul ve `Pageable` ekle:

```java
Page<BookResponseDTO> searchBooks(String title, String author, Pageable pageable);
```

**BookServiceImpl.java** — karşılığını güncelle:

```java
    @Override
    @Transactional(readOnly = true)
    public Page<BookResponseDTO> searchBooks(String title, String author, Pageable pageable) {
        return bookRepository.searchBooks(title, author, pageable)
                .map(this::mapToResponseDTO);
    }
```

**BookController.java** — arama endpoint'ine `Pageable` parametresi ekle:

```java
    @GetMapping("/search")
    public ResponseEntity<Page<BookResponseDTO>> searchBooks(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String author,
            Pageable pageable) {
        return ResponseEntity.ok(bookService.searchBooks(title, author, pageable));
    }
```

Spring, `?page=0&size=20&sort=title,desc` parametrelerini `Pageable`'a kendisi çeviriyor. Böylece elle `sortBy` parametresi tutmaya gerek kalmıyor ve geçersiz alan adı 500 yerine 400 dönüyor.

> Not: Arayüzdeki (`index.html`) arama fonksiyonu artık düz dizi yerine `{content: [...], totalElements: n}` şeklinde bir cevap alacak. JavaScript tarafında `data` yerine `data.content` okunmalı. Arayüzü 8. adımda elden geçireceğiz; şimdilik arama kutusu bozulursa sebebi bu.

---

## 4. Mükerrer kayıt kontrolü (opsiyonel ama önerilir)

Excel'i iki kez yüklersen artık veritabanı kısıtı hata fırlatacak. Daha temiz davranış, yüklemeden önce kontrol etmek. `BookServiceImpl` içindeki Excel döngüsünde, `tempBookList.add(book)` satırından önce:

```java
                if (bookRepository.existsByTitleAndAuthorId(title, author.getId())) {
                    log.info("Zaten kayıtlı, atlandı: {} - {}", title, authorName);
                    continue;
                }
```

---

## 5. Çalıştır ve doğrula

```bash
# Yeni migration uygulanmali
# Loglarda: Migrating schema "public" to version "3 - refine book model"
```

Sonra N+1 düzeltmesini gözle doğrula. `show-sql=true` açık olduğu için sorgular loglara düşüyor:

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/authenticate \
  -H 'Content-Type: application/json' \
  -d '{"email":"test@test.com","password":"test1234"}' \
  | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

# Birkac kitap ekle, sonra listele
curl -s "localhost:8080/api/v1/books?page=0&size=20" -H "Authorization: Bearer $TOKEN" > /dev/null
```

Loglarda tek bir `select ... from books ... left join authors ... left join categories` sorgusu görmelisin. Düzeltmeden önce burada bir liste sorgusu artı her kitap için ayrı `select ... from authors where id=?` sorguları oluyordu.

Durum güncellemeyi de dene:

```bash
curl -s -X PUT "localhost:8080/api/v1/books/1/status?status=okunuyor" -H "Authorization: Bearer $TOKEN"
curl -s -o /dev/null -w "%{http_code}\n" -X PUT "localhost:8080/api/v1/books/1/status?status=SACMA" -H "Authorization: Bearer $TOKEN"
```

İlki küçük harfle gönderilmesine rağmen çalışmalı (Türkçe uyumlu dönüşüm), ikincisi 400 dönmeli.

---

## Bu adımda bilinçli olarak yapılmayanlar

- `excelSiraNo` alan adı hâlâ Excel'e bağımlı bir isim. Yeniden adlandırmak arayüzü de etkiliyor, 8. adıma bıraktım.
- Kategori hâlâ serbest metin olarak kaydedilebiliyor; allowlist kontrolü Adım 5'te, Gemini servisiyle birlikte.
- `Author` ve `Category` için denetim alanları eklenmedi, pratik faydası yok.
