-- 1. Durum alanini guvence altina al -----------------------------------------
-- Kodda enum'a geciyoruz, veritabani da ayni degerleri zorlasin.
UPDATE books SET status = 'OKUNMADI' WHERE status IS NULL;

ALTER TABLE books
    ALTER COLUMN status SET DEFAULT 'OKUNMADI',
ALTER COLUMN status SET NOT NULL;

ALTER TABLE books
    ADD CONSTRAINT chk_books_status
        CHECK (status IN ('OKUNMADI', 'OKUNUYOR', 'OKUNDU'));

-- 2. Denetim alanlari ---------------------------------------------------------
ALTER TABLE books
    ADD COLUMN created_at TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMP;

-- Mevcut satirlar icin makul bir baslangic degeri.
UPDATE books SET created_at = now(), updated_at = now()
WHERE created_at IS NULL;

-- 3. Ayni kitabin iki kez eklenmesini engelle ---------------------------------
-- Once olasi mukerrer kayitlari temizle: her (baslik, yazar) ikilisinden
-- en kucuk id'li satir kalir.
DELETE FROM books b
WHERE b.id NOT IN (
    SELECT MIN(id) FROM books GROUP BY title, author_id
);

ALTER TABLE books
    ADD CONSTRAINT uq_books_title_author UNIQUE (title, author_id);

-- 4. Baslik aramasi icin indeks -----------------------------------------------
-- LOWER(title) LIKE '%...%' sorgusu normal indeksten faydalanamaz; bu indeks
-- en azindan "ile baslayan" aramalarinda ise yarar.
CREATE INDEX idx_books_title_lower ON books (LOWER(title));