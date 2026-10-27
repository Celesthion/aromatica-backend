# --- сборка (Maven + JDK 21 LTS) ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
# сначала только описание сборки: слой с зависимостями пересобирается, лишь когда меняется pom
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package

# --- запуск (только JRE) ---
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 app
COPY --from=build /app/target/*.jar app.jar
USER app
# куча — от памяти контейнера (по умолчанию JVM берёт лишь четверть)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
# Порт слушаем из PORT (см. application.yaml).
ENTRYPOINT ["java", "-jar", "app.jar"]
