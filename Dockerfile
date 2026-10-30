# --- Derleme ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Once sadece pom: bagimlilik katmani kod degisikliginde yeniden kullanilir
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests
# Katmanli cikarma (layertools Boot 3.3+ ile deprecated, yerine jarmode=tools)
RUN java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination extracted

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
