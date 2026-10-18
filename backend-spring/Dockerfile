# Etapa 1: compila el jar con Maven. Primero solo el pom.xml, para que Docker guarde en caché las
# dependencias y no las vuelva a descargar cada vez que cambia el código.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src src
RUN mvn -q package -DskipTests

# Etapa 2: solo el JRE y el jar, sin Maven ni el código fuente.
FROM eclipse-temurin:21-jre
WORKDIR /app
# ffprobe (viene con ffmpeg) valida que el video sea reproducible y horizontal al subirlo (sección 5).
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
