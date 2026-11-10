# 05 · Worker, despliegue y configuración (HEAD ba903f4, v1.9.2)

**Desde:** se comprobó con `git ls-tree`/`git grep` contra `15489f4` (producción v1.0.0). Los archivos existían en `15489f4` salvo `worker/test_modelo.py` y el archivo `modelo-actual.json` (`nuevo`). Para los elementos de contenido se buscó el identificador en `15489f4`; el resultado va en cada fila. Un `base` por identificador indica que el nombre ya existía; no garantiza que el comportamiento sea idéntico.

**Cifras clave:** 4 servicios en `docker-compose.yml` (db, api, worker, web) · 1 puerto publicado (web, 8088) · 23 variables en `portable/.env.example` · 6 tareas programadas diarias · 4 scripts `.bat` en `portable/` (iniciar, detener, abrir_red, probar_correo) · 1 archivo nuevo (`worker/test_modelo.py`) · 1 archivo de estado nuevo en el volumen (`modelo-actual.json`).

## 1. Etapas del pipeline y cómo se pasa de una a otra (`worker/worker.py`)

| ID | Etapa / paso | Qué hace | Cómo pasa a la siguiente | Desde | Archivo:línea |
|---|---|---|---|---|---|
| P1 | Toma del análisis | `tomar_siguiente`: `ORDER BY fechaanalisis, idanalisis LIMIT 1 FOR UPDATE SKIP LOCKED` | Sondeo cada 5 s (`POLL_SECONDS`) | base | cola.py:95-113 (SELECT en :101); comun.py:24 |
| P2 | `preprocesamiento` | Comprueba que el video existe, que la tanda tiene especímenes, que el modelo existe y lee el video con ffprobe (`sondear`) | `poner_etapa(...,"preprocesamiento")`; al final `progreso.etapa_hecha` | base | worker.py:139, :157-158, :178 |
| P3 | `cilindros` | `etiquetar.py` encuentra los tubos y mide al animal; compara los cilindros encontrados con la tanda | `poner_etapa(...,"cilindros")` en :187; si no coincide el número de cilindros, error | base | worker.py:187-221 |
| P4 | Pausa R-15 / RN-17 | Si hay 3 s seguidos sin animal en un cilindro en los últimos 300 s, `pausar_analisis` pone estado `en pausa` y guarda `pausa.json` y `pausa.jpg` | Antes de clasificar: la comprobación está en :225-233; si se pausa, cierra `tiempos.json` y termina | base | worker.py:225-233; definiciones :264-278; `pausar_analisis` :395 (UPDATE en :433) |
| P5 | `clasificacion` | `cnn/probar_seg.py` clasifica cada segundo con la red (`--red MODELO`), mide el ritmo y lo guarda en `ritmo_prediccion.json` | `poner_etapa(...,"clasificacion")` en :239 | base | worker.py:239-258; vigilancia.py:266-283 |
| P6 | `completar` (dentro de la etapa de clasificación) | Escribe resultados y la copia `web.mp4` con ffmpeg (límite 30 min) | `UPDATE analisis SET estado='completado', etapa='clasificacion', nivelclasif=...` | base | worker.py:462 (llamada en :262), :491-514, :559 |
| P7 | Error | `marcar_error` y `fallo_del_pipeline` | Estado `error` y `rutadiagnostico` (solo con estado error, CHECK `ck_analisis_diagnostico`) | base | cola.py:122-147; worker.py:125-131 |
| P8 | Reencolado de huérfanos y pausas | `reencolar_huerfanos`, `revisar_pausas` (72 h) | Al arrancar y en cada vuelta | base | cola.py:29-86 |

Límites: un paso que pasa de 60 min se da por perdido (`LIMITE_PASO_S`, worker.py:45); ffmpeg 30 min (worker.py:46). Un análisis conserva solo los últimos 300 s (`MAX_SEGUNDOS`, worker.py:44, D-54).

Dónde se escribe el avance: `progreso.json` por análisis (`progreso.py`); pesos `PESO_ETAPA = {preprocesamiento: 1, cilindros: 9, clasificacion: 90}` (progreso.py:29; base). Dentro de cada etapa, `vigilancia.py` reparte por pasos (`PESO_PASO_CILINDROS`, vigilancia.py:45; `PESO_FASE_CLASIFICACION = {recortando: 3, prediciendo: 85, web: 12}`, vigilancia.py:48). El avance de la pantalla va de 0 a 100 %: siempre se escribe, con o sin modo desarrollador (tiempos.py:36-38).

## 2. Modelo: qué es y dónde se busca

| ID | Elemento | Valor | Desde | Archivo:línea |
|---|---|---|---|---|
| M1 | Ruta del modelo dentro del worker | `MODELO_CNN`, por omisión `/data/modelos/fst_cnn_seg.pt` | base | worker.py:42; tiempos.py:291-294 (`ruta_del_modelo`) |
| M2 | Montaje en el contenedor | `./modelos` → `/data/modelos`, solo lectura | base | docker-compose.yml:111, :118 |
| M3 | El modelo no va dentro de la imagen | comentario del Dockerfile | base | worker/Dockerfile:3-4 |
| M4 | Código del pipeline dentro de la imagen | `COPY pipeline ./pipeline`; `etiquetar.py`, `cnn/probar_seg.py`, `segundos.py`, `lib.py` | base | worker/Dockerfile:27; worker.py:43 |
| M5 | Ignorado por git | `/modelos/` y `*.pt` | base | .gitignore:18-19; worker/Dockerfile.dockerignore |
| M6 | Identidad del modelo (solo modo desarrollador) | archivo, versión, SHA-256, tamaño, fecha, `videosDeEntrenamiento`, `clips` | base | tiempos.py:106-148 |
| M7 | Estado publicado para la API | `modelo-actual.json` en el volumen de archivos (solo modo desarrollador) | **nuevo** (la cadena `modelo-actual` no existe en 15489f4) | tiempos.py:287-322 |

## 3. Tiempos (`tiempos.json`) y modo desarrollador

| ID | Elemento | Detalle | Desde | Archivo:línea |
|---|---|---|---|---|
| T1 | Activación | `MODO_DESARROLLADOR` = true/1/si/sí/yes; sin el valor no se escribe nada | base | tiempos.py:35-39; docker-compose.yml:79 (api), :115 (worker) |
| T2 | Ruta | `analisis/{id}/tiempos.json` en el volumen de archivos | base | tiempos.py:1, :169, :200 |
| T3 | Campos | `inicio`, `fin`, `totalSegundos`, `etapas[]` (etapa, inicio, fin, segundos), `subpasos{cilindros,clasificacion}`, `equipo` (nombre, cpu, nucleos, memoriaGB, dockerCPU), `modelo` (archivo, version, sha256, tamanoMB, modificado, videosDeEntrenamiento, clips), `video` (duracionSegundos, ancho, alto, tamanoMB) | base | tiempos.py:5-11 (esquema), :204-212 |
| T4 | Nombre del equipo | `EQUIPO_NOMBRE` (vacío = modelo del procesador) | base | docker-compose.yml:113; tiempos.py:81-95 |
| T5 | Escritura atómica | temporal + `os.replace` | base | tiempos.py:184-190 |
| T6 | Cierre en error o pausa | `cerrar_archivo` cierra `fin` y `totalSegundos` | base | tiempos.py:259-270 |
| T7 | Diferencia en modo normal | sin modo desarrollador no se escribe `tiempos.json` ni `modelo-actual.json`; el avance (`progreso.json`) sí se escribe | base | tiempos.py:36-38, :250-252, :309 |
| T8 | Qué muestra el banner / PDF en modo desarrollador | banner con versión y tiempos; PDF y reportes con el modelo del análisis | base | tiempos.py:14; diagnostico.py:69 |

## 4. Servicios de `docker-compose.yml` (proyecto `fst-portable`, HEAD)

| ID | Servicio | Imagen | Puertos | Volúmenes | Healthcheck | Dependencias | Límite de memoria | Desde | Archivo:línea |
|---|---|---|---|---|---|---|---|---|---|
| S1 | `db` | `postgres:16.15-alpine` (`pull_policy: never`) | ninguno | `pgdata:/var/lib/postgresql/data`; `./backend-spring/db/init/001_esquema.sql` como `/docker-entrypoint-initdb.d/001_esquema.sql` (solo lectura) | `pg_isready -U $POSTGRES_USER -d $POSTGRES_DB`, cada 5 s, 10 reintentos | — | — | base | :36-52 |
| S2 | `api` | `fst-api:${FST_VERSION}` (build `./backend-spring`) | ninguno publicado (8000 interno) | `videos_reportes:/data/archivos` | `curl -fsS http://127.0.0.1:8000/health`, cada 15 s, 5 reintentos, inicio 90 s | `db` sano | 1536 m (JVM al 75 %) | base | :57-93 |
| S3 | `worker` | `fst-worker:${FST_VERSION}` (build `.` con `worker/Dockerfile`) | ninguno | `videos_reportes:/data/archivos`; `./modelos:/data/modelos:ro`; `./respaldos:/data/respaldos` | `python -c "import comun; comun.conectar().close()"`, cada 60 s, 3 reintentos, inicio 30 s | `db` sano | 5 g | base | :97-136 |
| S4 | `web` | `fst-web:${FST_VERSION}` (build `./frontend`, `Dockerfile.prod`) | `${PUERTO_WEB:-8088}:80` | ninguno | ninguno | `api` sano | ninguno | base | :140-157 |

Volúmenes con nombre: `pgdata` (:160), `videos_reportes` (:161). Registro de logs: json-file, 10 MB × 5 archivos por servicio (:23-27). Nombre del proyecto: `fst-portable` (:19).

Build args de `web`: `VITE_USE_MOCKS=false`, `VITE_API_BASE=/api`, `VITE_MAX_UPLOAD_MB=${MAX_UPLOAD_MB:-2048}` (:145-148).

Variables fijadas en el compose (no vienen del `.env`): `api` → `DB_HOST=db`, `DB_PORT=5432`, `SERVER_PORT=8000`, `ARCHIVOS_DIR=/data/archivos`, `WEB_URL=${DIRECCION_WEB:-}`, `SWAGGER_HABILITADO=false`, `MODO_DESARROLLADOR=${MODO_DESARROLLADOR:-false}` (:66-79). `worker` → `DB_HOST=db`, `DB_PORT=5432`, `ARCHIVOS_DIR=/data/archivos`, `MODELO_CNN=/data/modelos/fst_cnn_seg.pt`, `EQUIPO_NOMBRE`, `MODO_DESARROLLADOR` (:108-115).

Imágenes: `pull_policy: never` en todos (nada se descarga); `FST_VERSION` obligatorio (`:?`), lo escribe `iniciar.bat` (:58, :98, :141).

## 5. Variables de `portable/.env.example` (solo nombres, valor por omisión y uso)

Se leyó la plantilla `portable/.env.example`, no `portable/.env` (que no se abrió).

| ID | Variable | Valor por omisión en la plantilla | Para qué sirve | Desde | Archivo:línea |
|---|---|---|---|---|---|
| V1 | POSTGRES_USER | `fst` | Usuario de la base | base | portable/.env.example:16 |
| V2 | POSTGRES_PASSWORD | `GENERAR` | Clave de la base; `iniciar.bat` la genera solo en una instalación nueva | base | :17 |
| V3 | POSTGRES_DB | `fst` | Nombre de la base | base | :18 |
| V4 | JWT_SECRET | `GENERAR` | Llave con la que se firman las sesiones | base | :23 |
| V5 | JWT_EXPIRES_HOURS | `168` | Duración de la sesión (7 días) | base | :25 |
| V6 | ADMIN_ID | vacío | Cuenta del administrador inicial (máx. 10 caracteres) | base | :35 |
| V7 | ADMIN_NOMBRE | vacío | Nombre del administrador | base | :36 |
| V8 | ADMIN_APELLIDOS | vacío | Apellidos del administrador | base | :37 |
| V9 | ADMIN_CORREO | vacío | Correo (usuario de acceso) | base | :38 |
| V10 | ADMIN_PASSWORD | vacío | Contraseña temporal (mínimo 8) | base | :39 |
| V11 | MAX_UPLOAD_MB | `2048` | Tamaño máximo de video en MB | base | :43 |
| V12 | PUERTO_WEB | `8088` | Puerto publicado de la página | base | :48 |
| V13 | DIRECCION_WEB | vacío | Dirección que va en los correos (si vacía, `iniciar.bat` usa el nombre de la PC) | base | :56 |
| V14 | WORKER_TZ | `America/Mexico_City` | Zona horaria de tareas y avisos | base | :60 |
| V15 | MODO_DESARROLLADOR | `false` | Herramientas de desarrollo (tiempos, banner) | base | :67 |
| V16 | EQUIPO_NOMBRE | vacío | Nombre de la PC para los reportes de tiempos | base | :73 |
| V17 | SMTP_HOST | vacío | Servidor de correo; vacío = sin correo | base | :98 |
| V18 | SMTP_PORT | `587` | Puerto SMTP | base | :99 |
| V19 | SMTP_USER | vacío | Usuario SMTP | base | :100 |
| V20 | SMTP_PASSWORD | vacío | Contraseña SMTP | base | :101 |
| V21 | SMTP_AUTH | `true` | Usa autenticación | base | :102 |
| V22 | SMTP_STARTTLS | `true` | Usa STARTTLS | base | :103 |
| V23 | SMTP_FROM | vacío | Remitente | base | :104 |

Variables que el código lee y que **no** están en la plantilla (valores por omisión en el código): `WORKER_POLL_SECONDS` = 5, `WORKER_DAILY_HOUR` = 3, `PAUSE_TIMEOUT_HOURS` = 72, `DISK_ALERT_PERCENT` = 80, `DISK_CLEANUP_PERCENT` = 90 (`worker/comun.py:24-28`); `DISK_SIMULATED_PERCENT` (solo pruebas, comun.py:30); `RESPALDOS_DIR` = `/data/respaldos` (comun.py:37); `ARCHIVOS_DIR` = `/data/archivos` (comun.py:34). `FST_VERSION` no está en la plantilla: lo escribe `iniciar.bat` (iniciar.bat:138-139).

`backend-spring/.env.example` y `frontend/.env.example` existen en HEAD (base); no se listaron sus variables aquí porque son del entorno de desarrollo.

## 6. Versionado (`tools/version.py`)

| ID | Elemento | Detalle | Desde | Archivo:línea |
|---|---|---|---|---|
| W1 | Commit base | `BASE_COMMIT = 15489f4d…`, `BASE_VERSION = (1, 0, 0)` (producción) | base | tools/version.py:35-36 |
| W2 | Regla de conteo | `feat` sube el minor; `fix` sube el patch; el resto no cuenta | base | tools/version.py:32, :69 |
| W3 | Escritura con `--escribir` | `VERSION` (raíz); `frontend/package.json` (campo version); `backend-spring/src/main/resources/version.properties` (`app.version`) | base | tools/version.py:82-94 |
| W4 | Versión actual (HEAD) | **1.9.2** (`python tools/version.py` imprime 1.9.2 y el archivo `VERSION` dice 1.9.2) | — | VERSION:1 |

## 7. Scripts de `portable/`

| ID | Archivo | Qué hace | Salidas / notas | Desde | Archivo:línea |
|---|---|---|---|---|---|
| B1 | `iniciar.bat` | Prende el sistema en 9 pasos: Docker, `.env` (lo crea desde la plantilla la primera vez), cuenta de administrador, llaves (`JWT_SECRET`, `POSTGRES_PASSWORD`), modelo `..\modelos\fst_cnn_seg.pt`, versión (`..\VERSION` → `FST_VERSION`), carga de `fst-imagenes-X.tar` si faltan imágenes, respaldo previo al actualizar (`tareas_programadas.py --respaldo`), `docker compose up -d --no-build`, espera a `/salud` y `/api/health` (hasta 5 min) | Códigos de salida 0–11 (rem de :3-6) | base | portable/iniciar.bat:1-6, :138, :197 (respaldo previo), :227 (up) |
| B2 | `detener.bat` | `docker compose … stop`: apaga sin borrar nada | — | base | detener.bat:21 |
| B3 | `abrir_red.bat` | Abre en el firewall de Windows el puerto `PUERTO_WEB` solo para redes privadas; se ejecuta una vez como administrador | Códigos 0, 1 (no administrador), 2 | base | abrir_red.bat:1-6, :36-37 |
| B4 | `probar_correo.bat` | Manda un correo de prueba al `ADMIN_CORREO` con el contenedor del worker (`probar_correo.py`) | Códigos 0–3 | base | probar_correo.bat:1-8, :40 |
| B5 | `empaquetar.ps1` | Arma `dist\` con el zip chico y el tar de imágenes `fst-imagenes-X.Y.Z.tar` | — | base | portable/empaquetar.ps1:1-12 |

Variables de prueba de los `.bat`: `FST_SIN_NAVEGADOR`, `FST_SIN_PAUSA`, `FST_SIN_RESPALDO`, `FST_PROYECTO` (iniciar.bat:8-11).

## 8. Respaldos (`respaldos/`)

| ID | Elemento | Detalle | Desde | Archivo:línea |
|---|---|---|---|---|
| R1 | Carpeta | `./respaldos` en el equipo, montada en el worker como `/data/respaldos` | base | docker-compose.yml:119-121 |
| R2 | Nombre | `fst-AAAAMMDD.dump` (formato custom, `pg_dump -Fc`); si ya hay uno de hoy, se reemplaza | base | tareas_programadas.py:152-160 |
| R3 | Retención | Se conservan los 7 más nuevos (`CONSERVAR_RESPALDOS = 7`) | base | tareas_programadas.py:26, :182-184 |
| R4 | Tiempo máximo | `LIMITE_RESPALDO_S = 30 * 60` | base | tareas_programadas.py:27 |
| R5 | Cuándo | Diario a `WORKER_DAILY_HOUR` (3:00 por omisión); también antes de actualizar (`iniciar.bat`) | base | comun.py:25; tareas_programadas.py:37-43; portable/iniciar.bat:197 |
| R6 | Archivos en la carpeta (no versionados) | `fst-20261026.dump`, `fst-20261108.dump` | — | .gitignore:25-26 (`respaldos/`) |

## 9. Tareas programadas diarias (`worker/tareas_programadas.py`)

Orden de `correr_todas` (tareas_programadas.py:43-56):

| ID | Tarea | Función | Regla | Desde | Archivo:línea |
|---|---|---|---|---|---|
| K1 | Respaldo de la base | `respaldar_base` | Primero; si falla, las demás siguen | base | :152; llamada en :46 |
| K2 | Borrar videos vencidos | `borrar_videos_vencidos` | 30 días tras el análisis (`VIDEO.archivo` pasa a NULL) | base | :67; :64 |
| K3 | Borrar pausas huérfanas | `borrar_pausas_huerfanas` | — | base | :88 |
| K4 | Borrar reportes huérfanos | `borrar_reportes_huerfanos` | — | base | :109 |
| K5 | Avisar vencimientos | `avisar_vencimientos` | Aviso con 7 días o menos | base | :128-143 (llamada en :50) |
| K6 | Vigilar disco | `vigilar_disco` | Alerta al 80 %; limpieza al 90 % | base | :198-268; comun.py:27-28 |

Plazos: pausa 72 h (`PAUSE_TIMEOUT_HOURS`, comun.py:26); video 30 días tras `fechaanalisis`, en la consulta SQL (tareas_programadas.py:68-78); el aviso de los últimos 7 días está en :128-143.

## 10. Pruebas del worker (resumen; detalle en 06)

Pruebas por `unittest` (no pytest): `worker/test_*.py`, 8 archivos, 93 métodos `test_` (conteo estático). Cada prueba dice cómo correrla en su docstring (p. ej. `docker exec <worker> python -m unittest test_diagnostico`, test_diagnostico.py:5). `test_modelo.py` es el único archivo **nuevo**. `Dockerfile` no copia los `test_*.py` a la imagen (worker/Dockerfile:28-30).

## 11. Lo que no pude confirmar

- Que la imagen `fst-worker` se construya hoy sin error: requiere Docker, que no se usó.
- Que el worker corra las pruebas `unittest` fuera del contenedor: dependen de torch/ffmpeg instalados localmente.
- Que `modelo-actual.json` aparezca en la pantalla de carga de la API (eso está en el backend; no se revisó aquí).
