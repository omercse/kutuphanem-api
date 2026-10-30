# Kütüphanem API

Kişisel kitaplığımı Excel tablolarından çıkarıp yönetilebilir bir API'ye taşımak için yazdığım Spring Boot uygulaması. Binlerce satırlık bir Excel dosyasını yükleyip kitapları otomatik kategorilendirebiliyor, okuma durumlarını takip edebiliyor ve güncel listeyi tekrar Excel olarak dışa aktarabiliyorum.

> TODO: Yukarıdaki paragrafı kendi hikayene göre düzenle. Gerçek bir ihtiyaçtan doğduysa bunu yaz, "CV projesi" izlenimi vermekten kaçın.

![Arayüz](docs/screenshot.png)

> TODO: Arayüzden bir ekran görüntüsü al, `docs/screenshot.png` olarak kaydet.
> Kitap listesi ve kategori etiketlerinin göründüğü bir ekran en iyisi.

---

## Ne yapıyor

- **JWT tabanlı kimlik doğrulama**, USER ve ADMIN olmak üzere iki rol
- **Excel toplu içe aktarma**, arka planda asenkron çalışıyor, istek anında 202 dönüyor
- **Yapay zeka ile otomatik kategorilendirme**: Google Gemini'ye 10'ar kitaplık gruplar halinde başlık gönderip kategori tahmini alıyor
- **Sayfalama, sıralama ve filtreli arama**
- **Excel dışa aktarma** (Apache POI)
- **Flyway** ile sürümlenmiş veritabanı şeması
- Tek komutla ayağa kalkan **Docker** kurulumu

---

## Teknolojiler

| Katman | Kullanılan |
|---|---|
| Dil / Çatı | Java 21, Spring Boot 4.0 |
| Güvenlik | Spring Security, JWT (jjwt) |
| Veri | PostgreSQL 15, Spring Data JPA, Hibernate 7, Flyway |
| Dosya işleme | Apache POI |
| Yapay zeka | Google Gemini API |
| Dokümantasyon | springdoc-openapi (Swagger UI) |
| Paketleme | Docker, docker compose |

---

## Çalıştırma

### Docker ile (önerilen)

```bash
git clone https://github.com/omercse/kutuphanem-api.git
cd kutuphanem-api
cp .env.example .env
# .env dosyasini doldur (asagidaki tabloya bak)
docker compose up --build
```

Uygulama: http://localhost:8080
Swagger UI: http://localhost:8080/swagger-ui.html

### Yerelde

PostgreSQL'in çalışıyor ve `kutuphane_db` veritabanının oluşturulmuş olması gerekiyor.

```bash
export DB_PASSWORD=... JWT_SECRET=... ADMIN_EMAIL=... ADMIN_PASSWORD=...
./mvnw spring-boot:run
```

---

## Ortam değişkenleri

| Değişken | Zorunlu | Açıklama |
|---|---|---|
| `DB_URL` | hayır | Varsayılan `jdbc:postgresql://localhost:5432/kutuphane_db` |
| `DB_USER` | hayır | Varsayılan `postgres` |
| `DB_PASSWORD` | **evet** | Tanımsızsa uygulama açılmaz |
| `JWT_SECRET` | **evet** | 32 baytlık hex. Üretmek için `openssl rand -hex 32` |
| `JWT_EXPIRATION` | hayır | Milisaniye, varsayılan 24 saat |
| `ADMIN_EMAIL` | hayır | Doluysa açılışta ADMIN oluşturulur |
| `ADMIN_PASSWORD` | hayır | `ADMIN_EMAIL` ile birlikte kullanılır |
| `GEMINI_API_KEY` | hayır | Boşsa AI kategorilendirme devre dışı kalır, kitaplar GENEL olur |

Kodda hiçbir sır tutulmuyor; `DB_PASSWORD` ve `JWT_SECRET` için bilinçli olarak varsayılan değer tanımlanmadı, eksiklerse uygulama sessizce zayıf bir değere düşmek yerine açılışta hata veriyor.

---

## Endpoint'ler

| Metot | Yol | Yetki | Açıklama |
|---|---|---|---|
| POST | `/api/v1/auth/register` | herkes | Kayıt, her zaman USER rolüyle |
| POST | `/api/v1/auth/authenticate` | herkes | Giriş, JWT döner |
| GET | `/api/v1/books` | USER, ADMIN | Sayfalı liste (`?page=&size=&sort=`) |
| GET | `/api/v1/books/search` | USER, ADMIN | `?title=&author=` ile filtreli arama |
| GET | `/api/v1/books/export` | USER, ADMIN | Excel dışa aktarma |
| POST | `/api/v1/books` | ADMIN | Tek kitap ekleme |
| POST | `/api/v1/books/upload` | ADMIN | Excel toplu yükleme (202 döner) |
| PUT | `/api/v1/books/{id}/status` | USER, ADMIN | Okuma durumu güncelleme |
| PUT | `/api/v1/books/{id}` | ADMIN | Kitap güncelleme |
| DELETE | `/api/v1/books/{id}` | ADMIN | Silme |

---

## Tasarım kararları

**Kayıt olan herkes USER.** ADMIN yetkisi `/register` üzerinden verilemiyor, çünkü o endpoint kimlik doğrulama istemiyor; aksi halde API adresini bilen herkes silme yetkisi kazanırdı. İlk ADMIN, sunucuya erişimi olan kişinin tanımladığı `ADMIN_EMAIL` ve `ADMIN_PASSWORD` değişkenleriyle açılışta oluşuyor. Sistemde zaten bir ADMIN varsa hiçbir şey yapılmıyor, bu yüzden şifre değişkenini sonradan değiştirmek mevcut hesabın şifresini güncellemez.

**Şemayı Flyway yönetiyor, Hibernate değil.** `ddl-auto` `validate` olarak ayarlı: Hibernate açılışta entity sınıflarıyla gerçek şemayı karşılaştırıyor, uyuşmazlık varsa uygulama başlamıyor. Şema değişiklikleri `db/migration` altında sürümlenmiş SQL dosyaları olarak duruyor.

**N+1 sorgu önlemi.** `Book` ile `Author` ve `Category` arasındaki `@ManyToOne` ilişkiler LAZY; liste sorgularında `@EntityGraph` ile tek JOIN'e indiriliyor. `spring.jpa.open-in-view` bilinçli olarak kapalı, aksi halde lazy loading sorunları görünmez hale geliyor.

**Yapay zeka çağrıları gruplanıyor.** Her kitap için ayrı istek atmak yerine 10'ar başlık tek prompt'ta gönderiliyor, bu da API çağrısı sayısını yaklaşık 10 kat azaltıyor.

> TODO: Gerçek veri setinde kaç kitap işlediğini buraya yaz. Somut sayı güçlü durur.

---

## Bilinen eksikler

Dürüst olmak gerekirse tamamlanmamış tarafları:

- Excel içe aktarma 202 dönüyor ama işin durumunu sorgulayacak bir endpoint yok; başarısız olursa kullanıcı öğrenemiyor
- Gemini çağrılarında istek aralığı sabit bekleme ile yönetiliyor, backoff yok
- Test kapsamı düşük
- Arama JPA Specification yerine elle yazılmış JPQL kullanıyor

> TODO: Bu listeyi adımlar ilerledikçe güncelle. Düzelttiğin maddeyi sil.

---

## Lisans

MIT
