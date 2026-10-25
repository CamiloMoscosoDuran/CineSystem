# Multi-stage build para movie-catalog-service
# Etapa 1: Construcción con Maven
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# Copiar descriptores de dependencias primero (cache layer)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copiar código fuente y compilar
COPY src ./src
RUN mvn clean package -DskipTests -B

# Etapa 2: Imagen de runtime liviana
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Crear usuario no-root para seguridad
RUN addgroup -S cineapp && adduser -S cineapp -G cineapp

# Copiar JAR desde etapa de build
COPY --from=builder /build/target/movie-catalog-service-1.0.0.jar app.jar

# Cambiar al usuario no-root
USER cineapp

# Puerto de la aplicación
EXPOSE 8081

# Variables de entorno con valores por defecto
ENV ARTIFICIAL_DELAY_MS=0
ENV SERVER_PORT=8081

# Health check interno del contenedor
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
