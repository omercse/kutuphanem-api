# Adım 1 — Güvenlik Acil Müdahalesi

Sırayı bozmadan uygula. 1. madde her şeyden önce gelir.

---

## 1. Gemini API anahtarını iptal et (ÖNCE BU)

1. https://aistudio.google.com/apikey adresine git.
2. `AQ.Ab8RN6IQ...` ile başlayan anahtarı **Delete** ile sil.
3. **Create API key** ile yeni bir anahtar üret, bir kenara not et.

Anahtar public repoda durduğu sürece kod temizliği bir işe yaramaz. Önce iptal, sonra temizlik.

---

## 2. Yeni sırları üret

```bash
# JWT secret (HS256 icin 32 byte)
openssl rand -hex 32

# Admin sifresi
openssl rand -base64 24
```

---

## 3. Dosyaları yerine koy

Bu klasördeki dosyaları repo içinde aynı yollara kopyala:

| Dosya | Hedef |
|---|---|
| `src/main/resources/application.properties` | üzerine yaz |
| `src/main/java/com/booksystem/service/impl/AuthenticationServiceImpl.java` | üzerine yaz |
| `src/main/java/com/booksystem/service/impl/GeminiAiServiceImpl.java` | üzerine yaz |
| `src/main/java/com/booksystem/config/DataInitializer.java` | yeni dosya |
| `.gitignore` | üzerine yaz |
| `.env.example` | yeni dosya |

---

## 4. Ortam değişkenlerini tanımla

`.env.example` dosyasını `.env` olarak kopyala ve doldur. `.env` artık `.gitignore` içinde.

**IntelliJ IDEA:** Run → Edit Configurations → BookApplication → Environment variables alanına:

```
DB_PASSWORD=...;JWT_SECRET=...;ADMIN_EMAIL=admin@kutuphanem.local;ADMIN_PASSWORD=...;GEMINI_API_KEY=...
```

**Terminal:**

```bash
export DB_PASSWORD='...'
export JWT_SECRET='...'
export ADMIN_EMAIL='admin@kutuphanem.local'
export ADMIN_PASSWORD='...'
export GEMINI_API_KEY='...'
./mvnw spring-boot:run
```

`DB_PASSWORD` veya `JWT_SECRET` tanımlı değilse uygulama açılışta hata verir. Bu bilinçli: sessizce zayıf bir varsayılana düşmesindense patlaması daha iyi.

---

## 5. Git geçmişini temizle

Anahtar sadece son commit'te değil, eklendiği commit'ten itibaren tüm geçmişte duruyor.

```bash
# Once yedek al
cd ..
cp -r kutuphanem-api kutuphanem-api-yedek
cd kutuphanem-api

# git-filter-repo kur
pip install git-filter-repo

# application.properties'i TUM gecmisten cikar
git filter-repo --path src/main/resources/application.properties --invert-paths --force

# Temiz halini geri ekle
# (dosyayi bu klasordeki versiyonla olustur)
git add src/main/resources/application.properties .gitignore .env.example
git add src/main/java/com/booksystem/
git commit -m "security: move all secrets to environment variables, fix default role"

# Remote'u yeniden bagla ve zorla gonder
git remote add origin https://github.com/omercse/kutuphanem-api.git
git push origin --force --all
git push origin --force --tags
```

> `git filter-repo` remote'u siler, bu yüzden `git remote add` adımı gerekli.
> Depo bir fork değilse ve başka kimse klonlamadıysa force push güvenli.

Alternatif ve en kesin yol: GitHub'da depoyu sil, lokalde `rm -rf .git && git init` yap, tek temiz commit ile yeniden yayınla. Commit geçmişini kaybedersin ama anahtarın sızmadığından %100 emin olursun.

---

## 6. Doğrula

```bash
# Anahtar gecmiste kalmis mi?
git log --all -p | grep -i "AQ.Ab8RN6" ; echo "cikti bossa temiz"

# Yeni kullanici USER mi?
curl -s -X POST localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"firstName":"Test","lastName":"User","email":"test@test.com","password":"test1234"}'
# Beklenen: "role":"USER"

# USER silme yapabiliyor mu? (yapmamali)
TOKEN=<yukaridaki token>
curl -i -X DELETE localhost:8080/api/v1/books/1 -H "Authorization: Bearer $TOKEN"
# Beklenen: 403 Forbidden

# Admin girisi calisiyor mu?
curl -s -X POST localhost:8080/api/v1/auth/authenticate \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@kutuphanem.local","password":"<ADMIN_PASSWORD>"}'
# Beklenen: "role":"ADMIN"
```

Dördü de beklendiği gibi çalışıyorsa adım 1 tamam.

---

## Bu adımda bilinçli olarak yapılmayanlar

Sonraki adımlara bırakıldı, unutulmadı:

- `ddl-auto=create` hâlâ duruyor → **Adım 2** (Flyway ile birlikte değişecek)
- Register'da `@Valid` ve mükerrer e-posta kontrolü yok → **Adım 3**
- 401/403 cevapları `ErrorResponse` formatında değil → **Adım 3**
- Süresi geçmiş token 500 veriyor → **Adım 3**
- `@CrossOrigin("*")` duruyor → **Adım 3**
- Gemini `RestTemplate`'inde timeout yok → **Adım 5**
