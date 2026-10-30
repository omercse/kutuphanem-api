# Adım 2a — Flyway ile şema yönetimi

## Neden

Şu an `spring.jpa.hibernate.ddl-auto=create`. Uygulama her açılışta tabloları siliyor ve yeniden kuruyor. Bunun üç sonucu var:

- Test verilerin her yeniden başlatmada uçuyor (bunu zaten yaşadın, `test@test.com` kullanıcısı iki kez kayboldu)
- Gerçek bir ortamda çalıştırılamaz, ilk açılışta tüm veriyi siler
- Şemanın nasıl evrildiğinin kaydı yok, geri dönülemez

Flyway şemayı sürümlenmiş SQL dosyalarıyla yönetir. Her dosya bir kez çalışır, çalıştığı `flyway_schema_history` tablosuna yazılır. Şema artık kodun yanında, versiyon kontrolünde duruyor.

---

## 1. pom.xml'e bağımlılıkları ekle

`<dependencies>` bloğunun içine, `spring-boot-starter-data-jpa` bağımlılığının hemen altına:

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

Versiyon yazmıyoruz, Spring Boot parent'ı uyumlu sürümü kendi belirliyor. İkinci bağımlılık Flyway 10'dan beri ayrı paket olarak geliyor, PostgreSQL desteği onda.

Ekledikten sonra IntelliJ sağ üstte Maven yenileme simgesi gösterir, ona bas (veya Maven panelinden Reload).

---

## 2. Migration dosyalarını ekle

`src/main/resources` altında `db` klasörü, onun içinde `migration` klasörü oluştur. Sonuç şu olmalı:

```
src/main/resources/db/migration/V1__init_schema.sql
src/main/resources/db/migration/V2__seed_categories.sql
```

Dosya adlandırma kuralı önemli: `V` + sürüm numarası + **çift alt çizgi** + açıklama. Tek alt çizgi olursa Flyway dosyayı tanımaz.

İçerikleri bu klasördeki dosyalardan kopyala.

`V1` dosyası, Hibernate'in şu ana kadar ürettiği şemanın birebir aynısı artı üç indeks. `V2` ise AI prompt'unda izin verilen kategorileri sabitliyor; bunlar `GeminiAiServiceImpl` içindeki listeyle birebir aynı olmalı, aksi halde kod yeni kategori satırı oluşturmaya devam eder.

---

## 3. application.properties'i güncelle

Bu klasördeki dosyayla değiştir. Üç satır değişti:

- `ddl-auto=create` → `validate`: Hibernate artık tablo oluşturmuyor, sadece entity'lerle gerçek şemayı karşılaştırıyor. Uyuşmazlık varsa uygulama açılmıyor, bu iyi bir şey: sessiz veri kaybı yerine gürültülü hata.
- `spring.jpa.open-in-view=false`: Loglardaki uyarının sebebi buydu. Açıkken veritabanı sorguları HTTP cevabı yazılırken de çalışabiliyor, bu da lazy loading sorunlarını gizliyor. 2b adımında N+1 problemini göreceğiz, bunun kapalı olması gerekiyor.
- Flyway ayarları eklendi.

---

## 4. Veritabanını temizle

Mevcut veritabanında Hibernate'in oluşturduğu tablolar var ama Flyway'in geçmiş tablosu yok. En temiz yol sıfırdan başlamak. pgAdmin veya psql ile:

```sql
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
```

Verilerin zaten her açılışta siliniyordu, kaybedecek bir şey yok.

---

## 5. Çalıştır ve doğrula

Uygulamayı başlat. Loglarda şunları görmelisin:

```
Flyway Community Edition ... by Redgate
Successfully validated 2 migrations
Creating Schema History table "public"."flyway_schema_history"
Migrating schema "public" to version "1 - init schema"
Migrating schema "public" to version "2 - seed categories"
Successfully applied 2 migrations
```

Ardından `drop table` satırlarının **artık görünmemesi** gerekiyor. Görünüyorsa `ddl-auto` hâlâ `create`.

Asıl test: kullanıcı oluştur, uygulamayı kapat, tekrar aç, giriş yap.

```bash
# 1. Kayit ol
curl -s -X POST localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"firstName":"Test","lastName":"User","email":"test@test.com","password":"test1234"}'

# --- IntelliJ'den uygulamayi durdur ve tekrar baslat ---

# 2. Ayni kullaniciyla giris yap
curl -s -X POST localhost:8080/api/v1/auth/authenticate \
  -H 'Content-Type: application/json' \
  -d '{"email":"test@test.com","password":"test1234"}'
```

İkinci istek token dönüyorsa veri kalıcı demektir. Adım 2a bitti.

İkinci açılışta Flyway şunu yazacak, bu normal:

```
Schema "public" is up to date. No migration necessary.
```

---

## Sık karşılaşılan hatalar

**`Schema-validation: missing table [x]`**
Flyway çalışmamış ama Hibernate doğrulamaya geçmiş. Migration dosyalarının `src/main/resources/db/migration` altında ve dosya adlarında çift alt çizgi olduğundan emin ol.

**`Schema-validation: wrong column type`**
V1 dosyasındaki tip ile entity'deki tip uyuşmuyor. Hata mesajı hangi kolon olduğunu söyler.

**`Found non-empty schema(s) "public" without schema history table`**
4. adımdaki şema temizliğini atlamışsın.

---

## Sırada ne var (Adım 2b)

Flyway oturduktan sonra model iyileştirmeleri geliyor, hepsi birer migration dosyası olarak:

- `status` alanı String yerine `BookStatus` enum'ı
- `@ManyToOne` ilişkilerde LAZY fetch ve `@EntityGraph`, N+1 sorgu problemi için
- `created_at` / `updated_at` alanları
- Aynı kitabın iki kez eklenmesini engelleyen unique kısıt
