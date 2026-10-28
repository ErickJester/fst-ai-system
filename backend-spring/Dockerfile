# Etapa 1: compila el jar con Maven. Primero solo el pom.xml, para que Docker guarde en caché las
# dependencias y no las vuelva a descargar cada vez que cambia el código.
# Las imágenes base llevan versión EXACTA (nada de tags flotantes como 3.9 o 21-jre): la imagen que se construye hoy y la que
# se construya dentro de seis meses salen de las mismas bases.
FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src src
RUN mvn -q package -DskipTests

# Etapa 2: solo el JRE y el jar, sin Maven ni el código fuente.
FROM eclipse-temurin:21.0.12.1_1-jre
WORKDIR /app
# ffprobe (viene con ffmpeg) valida que el video sea reproducible y horizontal al subirlo (sección 5). curl es para el
# healthcheck del compose (GET /health).
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg curl && rm -rf /var/lib/apt/lists/*
# Usuario sin privilegios: el servidor no corre como root.
RUN useradd --system --uid 10001 --no-create-home --shell /usr/sbin/nologin app
COPY --from=build /app/target/*.jar app.jar
# El compose definitivo pone SERVER_PORT=8000 (nginx reenvía /api/ a api:8000); el compose de desarrollo usa el 8084 por omisión.
EXPOSE 8000
# El contenedor arranca como root SOLO para arreglar los permisos del volumen y se baja a «app» antes de lanzar Java.
# Los volúmenes de instalaciones anteriores (cuando el servidor corría como root) tienen archivos de root: el find de aquí
# solo toca los que no son de «app» (en un arranque normal no hay ninguno, así que no cuesta nada) y así el servidor puede
# seguir escribiendo en ellos. Si el volumen no existe (por ejemplo, en una prueba suelta), el error del find se ignora.
# -XX:MaxRAMPercentage=75: el heap toma el 75 % del límite de memoria del contenedor (mem_limit del compose).
# Sin un script aparte: así no depende de los saltos de línea del archivo al construir en Windows.
ENTRYPOINT ["sh", "-c", "find /data/archivos ! -user app -exec chown app:app {} + 2>/dev/null; exec setpriv --reuid=app --regid=app --init-groups java -XX:MaxRAMPercentage=75 -jar app.jar"]
