# Adım 7+8 — Paketleme ve vitrin

Amaç: projeyi klonlayan birinin tek komutla çalıştırabilmesi ve README'yi okuyan birinin 60 saniyede ne yaptığını anlaması.

Bu dosyayı Claude Code'a oku ve sırayla uygula. Her adımdan sonra `mvn compile` ile derle.

---

## 1. Swagger / OpenAPI

`pom.xml`'e ekle:

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>3.0.0</version>
</dependency>
```

> Spring Boot 4 + Spring Framework 7 uyumlu sürüm springdoc 3.x. Maven Central'da mevcut en güncel 3.x sürümünü kullan; 2.x Boot 3 içindir ve bu projede çalışmaz.

`SecurityConfiguration` içindeki `permitAll()` listesine şunları ekle:

```java
.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
```

`BookApplication` sınıfına JWT şemasını tanıtan bir yapılandırma ekle ki Swagger UI'dan "Authorize" düğmesiyle token girilebilsin:

```java
@OpenAPIDefinition(
    info = @Info(title = "Kütüphanem API", version = "v1",
                 description = "JWT korumalı kitap yönetim API'si"),
    security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
    name = "bearerAuth", type = SecuritySchemeType.HTTP,
    scheme = "bearer", bearerFormat = "JWT"
)
```

Doğrulama: `http://localhost:8080/swagger-ui.html` açılmalı, Authorize düğmesiyle token girilip endpoint'ler denenebilmeli.

---

## 2. Actuator

`pom.xml`'e:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

`application.properties`'e:

```properties
management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=when-authorized
```

`SecurityConfiguration`'da `/actuator/health` yolunu `permitAll()` yap. Docker healthcheck bunu kullanacak.

---

## 3. Dockerfile'ı düzelt

Mevcut hali üç sorunlu: derleme JDK 21 kullanıyor ama pom Java 17 diyor, çalışma imajı JDK (JRE yeterli), konteyner root olarak çalışıyor. Ayrıca katmanlı jar kullanılmadığı için her kod değişikliğinde tüm bağımlılıklar yeniden indiriliyor.

Yeni hali:

```dockerfile
# --- Derleme ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Once sadece pom: bagimlilik katmani kod degisikliginde yeniden kullanilir
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests
RUN java -Djarmode=layertools -jar target/*.jar extract --destination extracted

# --- Calistirma ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build --chown=app:app /app/extracted/dependencies/ ./
COPY --from=build --chown=app:app /app/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /app/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /app/extracted/application/ ./
USER app
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q UP || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
```

> `pom.xml`'de `<java.version>` değerini 21 yap, Dockerfile ve IntelliJ ile tutarlı olsun. CV'deki "Java 17" ifadesini de 21 olarak güncelle.

`.dockerignore` dosyası oluştur:

```
target/
.git/
.idea/
.env
*.iml
docs/
```

---

## 4. docker-compose.yml

Proje kökünde:

```yaml
services:
  db:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: kutuphane_db
      POSTGRES_USER: ${DB_USER:-postgres}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER:-postgres} -d kutuphane_db"]
      interval: 5s
      timeout: 5s
      retries: 10

  app:
    build: .
    depends_on:
      db:
        condition: service_healthy
    environment:
      DB_URL: jdbc:postgresql://db:5432/kutuphane_db
      DB_USER: ${DB_USER:-postgres}
      DB_PASSWORD: ${DB_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
      ADMIN_EMAIL: ${ADMIN_EMAIL:-}
      ADMIN_PASSWORD: ${ADMIN_PASSWORD:-}
      GEMINI_API_KEY: ${GEMINI_API_KEY:-}
    ports:
      - "8080:8080"

volumes:
  pgdata:
```

Compose yanındaki `.env` dosyasını otomatik okur. Artık `.env` gerçekten işe yarıyor.

Doğrulama: `docker compose up --build` ile proje ayağa kalkmalı, `localhost:8080` açılmalı.

---

## 5. Arayüzdeki API adresi

`src/main/resources/static/index.html` içinde `localhost:8080` sabit yazılmış. Konteynerde veya deploy ortamında çalışmaz. JavaScript'teki API temel adresini şununla değiştir:

```javascript
const API_BASE = window.location.origin;
```

Ayrıca arama fonksiyonu artık `Page` nesnesi alıyor; `data` yerine `data.content` okunmalı.

---

## 6. GitHub Actions

`.github/workflows/ci.yml`:

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven
      - run: mvn -B clean verify
```

> Not: Şu anki tek test `@SpringBootTest` olduğu için ayakta bir Postgres istiyor ve CI'da patlar. Geçici çözüm olarak o testi `@Disabled` ile kapat ya da `mvn -B clean compile` kullan. Kalıcı çözüm Adım 6'daki Testcontainers.

---

## 7. pom.xml temizliği

Boş `<name/>`, `<description/>`, `<licenses><license/></licenses>`, `<developers><developer/></developers>` ve boş `<scm>` etiketlerini sil ya da doldur. `artifactId` değerini `book`'tan `kutuphanem-api`'ye çevir.

---

## 8. README.md

Proje kökünde. Taslak ayrı dosyada (`README-TASLAK.md`), oradaki `TODO` işaretlerini doldur.

Mutlaka olması gerekenler:
- İlk paragrafta ne yaptığı ve neden yazıldığı
- Arayüzden bir ekran görüntüsü veya GIF (`docs/screenshot.png`)
- Teknoloji listesi
- `docker compose up` ile çalıştırma talimatı
- Ortam değişkenleri tablosu
- Endpoint tablosu (yetki sütunuyla birlikte)
- "Tasarım kararları" bölümü: Flyway neden, RBAC nasıl, admin nasıl oluşuyor
- "Bilinen eksikler" bölümü: dürüstlük puan kazandırır

---

## 9. LICENSE

MIT lisans dosyası ekle. GitHub arayüzünden Add file → Create new file → dosya adına `LICENSE` yazınca şablon önerir.
