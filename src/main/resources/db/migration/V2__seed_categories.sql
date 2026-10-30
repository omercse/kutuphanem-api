-- Yapay zeka prompt'unda izin verilen kategoriler burada sabitleniyor.
-- Boylece AI beklenmedik bir deger dondurdugunde kod yeni bir kategori
-- satiri olusturmak yerine GENEL'e dusebilir.
-- Isimler GeminiAiServiceImpl icindeki prompt ile birebir ayni olmalidir.

INSERT INTO categories (name) VALUES
                                  ('EDEBİYAT / ROMAN'),
                                  ('TARİH'),
                                  ('DİN / İSLAM'),
                                  ('AİLE / PEDAGOJİ'),
                                  ('KİŞİSEL GELİŞİM / SAĞLIK'),
                                  ('GENEL')
    ON CONFLICT (name) DO NOTHING;