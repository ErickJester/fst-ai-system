# Verificación del documento contra el sistema en HEAD (ba903f4, v1.9.2)

Solo lectura. Escrito únicamente en `inventario/`. Sin commit, sin Docker, sin lectura de `portable/.env`.

**Leyenda:** ✔ coincide · ≠ distinto (o omitido en el documento) · ✘ no existe · ? no pude confirmar.

**Origen de cada ≠ o ✘:** `ya estaba en 15489f4` si el dato ya era así en producción (v1.0.0), `posterior a 15489f4` si lo cambió un commit posterior. Se comprobó con `git show 15489f4:ruta` y `git grep` cuando fue posible.

**Totales:** ✔ 137 · ≠ 15 · ✘ 1 · ? 28 (total 181 filas).

## Bloque A · Diccionario de datos (`back/apendice_D.tex`) contra `04_base_de_datos.md`

Base de comparación: `backend-spring/db/init/001_esquema.sql` y `migraciones.sql` en HEAD (ba903f4). Los nombres en camelCase del documento equivalen a los del esquema porque PostgreSQL no distingue mayúsculas en identificadores sin comillas. Las omisiones («no menciona») se marcan como ≠.

| Referencia | Lo que afirma el documento | Resultado | Lo que hace el sistema (archivo:línea) | Origen (≠ / ✘) |
|---|---|---|---|---|
| apendice_D.tex:10 | 12 tablas en el esquema físico | ✔ coincide | 001_esquema.sql:40-176 (12 CREATE TABLE) | — |
| apendice_D.tex:31 | «Nueve columnas» toman valores de un dominio | ✔ coincide | 9 columnas con dominio: grupo.tipo (:74), video.sesion (:105), reporte.formato (:160), notificacion.tipo (:172), analisis.estado/etapa/nivelclasif (:119-121), segundo.conducta/origen (:148-149) | — |
| apendice_D.tex:37-66 | Nueve dominios con sus valores y tipo base | ✔ coincide | 001_esquema.sql:9-35 (9 CREATE DOMAIN, mismos valores y varchar) | — |
| apendice_D.tex:60 | nivel_clasif: preciso/agrupado según si algún segundo quedó como conducta activa | ✔ coincide | dominio :28-29; regla en RevisionServiceImpl.java:285-290 | — |
| apendice_D.tex:64 | tipo_conducta: cuatro valores; «conducta activa» la asigna el clasificador | ✔ coincide | 001_esquema.sql:34-35; regla en segundos.py:54-66 | — |
| apendice_D.tex:86-99 | USUARIO: 7 columnas, tipos, NN y restricciones | ✔ coincide | 001_esquema.sql:41-47 (identificadores en minúsculas: PostgreSQL no distingue mayúsculas sin comillas) | — |
| apendice_D.tex:92 | correo: UK | ✔ coincide | 001_esquema.sql:48 uq_usuario_correo | — |
| apendice_D.tex:94 | contrasenaHash: bcrypt con sal | ✔ coincide | 001_esquema.sql:45; config/SeguridadConfig.java:45 | — |
| apendice_D.tex:96-98 | activo / cambioRequerido: NN, DEFAULT omitido en el documento | ≠ distinto | DEFAULT true (001_esquema.sql:46) y DEFAULT false (:47): omitido en el documento | ya estaba en 15489f4 |
| apendice_D.tex:103 | ADMINISTRADOR: PK y FK a USUARIO | ✔ coincide | 001_esquema.sql:52-53 | — |
| apendice_D.tex:108-116 | EXPERIMENTO: columnas, UK en nombre, notas nullable | ✔ coincide | 001_esquema.sql:60-66 | — |
| apendice_D.tex:110 | EXPERIMENTO.idInstitucional: FK a USUARIO; solo creador o admin borra (no es esquema) | ✔ coincide | 001_esquema.sql:61-62; permisos en ExperimentoServiceImpl.java:543 (eliminar) | — |
| apendice_D.tex:121-129 | GRUPO: PK, FK, UK (idExperimento, etiqueta), tipo dominio, tratamiento no vacío | ≠ distinto | 001_esquema.sql:69-77: tratamiento es NOT NULL, pero no hay CHECK: una cadena vacía sí se admite | ya estaba en 15489f4 |
| apendice_D.tex:136-140 | TANDA: FK a GRUPO, UK (idGrupo, ordinal), CK ordinal ≥ 1, CK nCilindros entre 2 y 4 | ✔ coincide | 001_esquema.sql:81-85 | — |
| apendice_D.tex:147-149 | ESPECIMEN: FK a TANDA, UK (idTanda, numeroCilindro), CK 1 a 4 | ✔ coincide | 001_esquema.sql:90-93 | — |
| apendice_D.tex:149 | «Que no pase del número de cilindros lo valida el backend» | ? no pude confirmar | la base no lo impide (001_esquema.sql:88-94); la validación en el backend no se revisó aquí | — |
| apendice_D.tex:156-158 | VIDEO: UK (idTanda, sesion); sesión Día 1/Día 2 | ✔ coincide | 001_esquema.sql:105, :112 (valores 'Dia 1', 'Dia 2' sin acento, igual que el documento en la tabla de dominios) | — |
| apendice_D.tex:160 | archivo nullable, nula cuando el video se borró | ✔ coincide | 001_esquema.sql:106; borrado en tareas_programadas.py:64 | — |
| apendice_D.tex:160 | archivo: .mp4 o .mov | ✔ coincide | VideoServiceImpl.java:116 y :266 (extensionDe) | — |
| apendice_D.tex:162 | nombreOriginal varchar(255) nullable; NULL en videos anteriores | ✔ coincide | 001_esquema.sql:109; migraciones.sql:8 | — |
| apendice_D.tex:164 | duracion numeric(8,2) NN, en segundos | ✔ coincide | 001_esquema.sql:110 | — |
| apendice_D.tex:164 | CHECK duracion > 0 (omitido en el documento) | ≠ distinto | 001_esquema.sql:110 tiene CHECK (duracion > 0); omitido en el documento | ya estaba en 15489f4 |
| apendice_D.tex:166 | fechaCarga timestamptz NN; default now() omitido en el documento | ≠ distinto | 001_esquema.sql:111 DEFAULT now(); omitido en el documento | ya estaba en 15489f4 |
| apendice_D.tex:173 | ANALISIS: UK idVideo; un análisis por video | ✔ coincide | 001_esquema.sql:117-118, :124 | — |
| apendice_D.tex:175 | estado: 72 h en pausa; cancelado tras 72 h sin respuesta | ✔ coincide | PAUSE_TIMEOUT_HOURS=72 (comun.py:26); cola.py:61-77 cancela | — |
| apendice_D.tex:175 | estado: default 'en cola' omitido en el documento | ≠ distinto | 001_esquema.sql:119 DEFAULT 'en cola'; omitido en el documento | ya estaba en 15489f4 |
| apendice_D.tex:177-179 | etapa y nivelClasif con valor de arranque (lo pone el backend al encolar) | ✔ coincide | 001_esquema.sql:120-121 (DEFAULT preprocesamiento / preciso) y el backend al encolar | — |
| apendice_D.tex:181 | fechaAnalisis: se reemplaza al terminar; desde ahí se cuentan 30 días | ✔ coincide | 001_esquema.sql:122; tareas_programadas.py:78 (interval '30 days') | — |
| apendice_D.tex:183 | rutaDiagnostico nullable; CK: solo con estado error | ✔ coincide | 001_esquema.sql:123, :125-126 (ck_analisis_diagnostico) | — |
| apendice_D.tex:190-192 | OBSERVACION: UK (idEspecimen, idVideo); hasta cuatro observaciones por video | ✔ coincide | 001_esquema.sql:134-138; numerocilindro ≤ 4 (:92) | — |
| apendice_D.tex:197-199 | SEGUNDO: PK compuesta (idObservacion, segundo); segundo 1 a 300; el minuto no se guarda | ✔ coincide | 001_esquema.sql:145-150; comentario D-52 en :141-142 | — |
| apendice_D.tex:199 | «Las duraciones y los segundos usan numeric» | ≠ distinto | duracion es numeric(8,2) (:110); la columna segundo es smallint (:147) | ya estaba en 15489f4 |
| apendice_D.tex:201-203 | SEGUNDO.conducta nullable; origen NN (default omitido en el documento) | ≠ distinto | 001_esquema.sql:148-149: origen DEFAULT 'maquina'; omitido en el documento | ya estaba en 15489f4 |
| apendice_D.tex:208-216 | REPORTE: PK, FK, formato dominio, UK ruta, fechaGeneracion | ✔ coincide | 001_esquema.sql:157-163; migraciones.sql:10-12 quita uq_reporte_formato | — |
| apendice_D.tex:216 | fechaGeneracion: default now() omitido en el documento | ≠ distinto | 001_esquema.sql:162 DEFAULT now() | ya estaba en 15489f4 |
| apendice_D.tex:221-233 | NOTIFICACION: columnas, FK, nullable idExperimento, leido, fechaCreacion | ✔ coincide | 001_esquema.sql:167-175 | — |
| apendice_D.tex:225 | idExperimento nulo en la alerta de disco | ✔ coincide | 001_esquema.sql:170-171; tareas_programadas.py:alertar_disco | — |
| apendice_D.tex:231-233 | leido y fechaCreacion: defaults false y now() omitido en el documento | ≠ distinto | 001_esquema.sql:174-175 | ya estaba en 15489f4 |
| apendice_D.tex:17-21 y 18-19 | Todas las FK con ON DELETE NO ACTION y ON UPDATE NO ACTION | ✔ coincide | 001_esquema.sql:53, 62, 72, 82, 91, 104, 118, 135, 137, 146, 159, 169, 171 | — |
| apendice_D.tex:23 | Identificadores subrogados integer GENERATED ALWAYS AS IDENTITY | ✔ coincide | 001_esquema.sql:60, 70, 80, 89, 102, 116, 133, 157, 167 | — |
| apendice_D.tex (todo) | Índices: los cinco ix_* están omitidos en el documento | ≠ distinto | 001_esquema.sql:182-186 (ix_experimento_usuario, ix_analisis_estado, ix_observacion_video, ix_notificacion_usuario, ix_notificacion_experim) | ya estaba en 15489f4 |
| apendice_D.tex (todo) | Migraciones (nombreOriginal 20-oct; quitar uq_reporte_formato 25-oct) omitidas en el documento | ≠ distinto | migraciones.sql:6-12 | ya estaba en 15489f4 |
| apendice_D.tex:177 | Regla «el backend pone el valor de arranque» coincide con el DEFAULT de la base | ✔ coincide | 001_esquema.sql:120-121 | — |

**Conteo del bloque:** ✔ 31 · ≠ 11 · ✘ 0 · ? 1 (total 43)

## Bloque B · Capítulo de desarrollo (`chapters/06_desarrollo.tex`, completo)

Versiones contra `pom.xml`, `package.json`, `requirements.txt`, Dockerfiles y `docker-compose.yml`; servicios, puertos y límites; variables de `.env`; plazos; pipeline; pruebas. Las cifras de pruebas se contaron en el árbol de HEAD (ver `06_pruebas.md`).

| Referencia | Lo que afirma el documento | Resultado | Lo que hace el sistema (archivo:línea) | Origen (≠ / ✘) |
|---|---|---|---|---|
| 06_desarrollo.tex:9-12 | Aplicación web sin instalar nada en el equipo del usuario; sin versión móvil | ✔ coincide | arquitectura web (frontend servido por nginx); no hay app móvil en el repositorio | — |
| 06_desarrollo.tex:25 | Java 21; Spring Boot 4.1; iText 5.5; Apache POI 5.5; springdoc 3.1 | ✔ coincide | backend-spring/pom.xml:18 (java 21), :9 (Spring Boot 4.1.1); iText 5.5.13.3; POI 5.5.1; springdoc 3.1.1 | — |
| 06_desarrollo.tex:25 | Maven 3.9; Temurin 21 JRE | ✔ coincide | backend-spring/Dockerfile:5 (maven:3.9.16-eclipse-temurin-21); :13 (eclipse-temurin:21.0.12.1_1-jre) | — |
| 06_desarrollo.tex:27 | React 18.3; React Router 7; Axios 1.20; Vite 8 | ✔ coincide | frontend/package.json:16-33 (react ^18.3.1, react-router-dom ^7.18.4, axios ^1.20.0, vite ^8.3.4) | — |
| 06_desarrollo.tex:27 | Node 22 para compilar | ✔ coincide | frontend/Dockerfile.prod:9 (node:22.23.4-alpine) | — |
| 06_desarrollo.tex:27 | Vitest 5 | ✔ coincide | frontend/package.json (vitest ^5.0.4) | — |
| 06_desarrollo.tex:27 | nginx 1.27 | ✔ coincide | frontend/Dockerfile.prod:24 (nginx:1.27.5-alpine) | — |
| 06_desarrollo.tex:29 | Python 3.12; PyTorch 2.14; torchvision 0.29 (CPU) | ✔ coincide | worker/Dockerfile:6 (python:3.12.16); :14 (torch==2.14.1+cpu, torchvision==0.29.1+cpu) | — |
| 06_desarrollo.tex:29 | OpenCV 5.0; NumPy 2.5; pandas 3.1; scikit-learn 1.9; psycopg2 2.9; Pillow 12.3 | ✔ coincide | worker/requirements.txt (opencv-python-headless 5.0.0.93, numpy 2.5.2, pandas 3.1.0, scikit-learn 1.9.1, psycopg2-binary 2.9.13, pillow 12.3.0) | — |
| 06_desarrollo.tex:29 | ffmpeg para la copia de revisión; pg_dump para respaldos | ✔ coincide | worker/Dockerfile:10 (ffmpeg); :21 (postgresql-client, pg_dump) | — |
| 06_desarrollo.tex:31 | PostgreSQL 16 | ✔ coincide | docker-compose.yml:37 (postgres:16.15-alpine) | — |
| 06_desarrollo.tex:33 | Arranque con portable\iniciar.bat que fija la versión y ejecuta docker compose up | ✔ coincide | portable/iniciar.bat:138 (FST_VERSION); :227 (docker compose up -d --no-build) | — |
| 06_desarrollo.tex:55 | db: solo interno, puerto 5432 | ✔ coincide | docker-compose.yml:36-52 (sin ports publicados) | — |
| 06_desarrollo.tex:57 | api: solo interno, puerto 8000; límite de memoria 1.5 GB | ✔ coincide | docker-compose.yml:68 (SERVER_PORT 8000); :92 (mem_limit 1536m) | — |
| 06_desarrollo.tex:59 | worker: sin puerto; límite de memoria 5 GB | ✔ coincide | docker-compose.yml:127 (mem_limit 5g); sin ports | — |
| 06_desarrollo.tex:61 | web: único puerto publicado, PUERTO_WEB, 8088 por omisión | ✔ coincide | docker-compose.yml:152 ("${PUERTO_WEB:-8088}:80") | — |
| 06_desarrollo.tex:70 | videos_reportes montado en /data/archivos en backend y worker | ✔ coincide | docker-compose.yml:81 (api), :117 (worker) | — |
| 06_desarrollo.tex:73 | El modelo se lee en solo lectura desde una carpeta del equipo | ✔ coincide | docker-compose.yml:118 (./modelos:/data/modelos:ro) | — |
| 06_desarrollo.tex:80-83 | Imágenes con número de versión; la versión sale del archivo VERSION | ✔ coincide | docker-compose.yml:58, :98, :141 (FST_VERSION); portable/iniciar.bat:138; VERSION = 1.9.2 | — |
| 06_desarrollo.tex:85-87 | Parámetros desde .env: llave, duración de sesión, base, correo, tamaño de video y umbrales de disco | ≠ distinto | llave, sesión, base, correo y tamaño están en portable/.env.example; los umbrales de disco (80 y 90) no están en la plantilla: son variables con valor por omisión en worker/comun.py:27-28 | ya estaba en 15489f4 |
| 06_desarrollo.tex:90 | El primer administrador se crea con los ADMIN_* del .env | ✔ coincide | backend-spring/src/main/java/com/ipn/mx/fst/config/AdminInicial.java:28, :40 | — |
| 06_desarrollo.tex:98 | BCrypt; el token JWT no guarda el rol: rol y estado se leen de la base en cada petición | ✔ coincide | config/SeguridadConfig.java:45; config/ConvertidorJwt.java:38 (cuenta inactiva) y :42 (rol desde administradorRepository) | — |
| 06_desarrollo.tex:100-101 | Rutas /admin/ exigen rol administrador; el rol se revisa en el servidor | ✔ coincide | config/SeguridadConfig.java:115 | — |
| 06_desarrollo.tex:102-103 | Solo .mp4 y .mov; límite de 2048 MB (MAX_UPLOAD_MB); extensión validada también en la interfaz | ✔ coincide | VideoServiceImpl.java:116; application.properties:40-41; CargarVideoPage.jsx:242 | — |
| 06_desarrollo.tex:104-105 | Video horizontal; el girado se rechaza con mensaje; no hay opción de girar | ✔ coincide | VideoServiceImpl.java:133 y :137 (mensaje de video vertical) | — |
| 06_desarrollo.tex:106-107 | El video se guarda con el id y su extensión; grupo, tanda y video se registran en la base | ✔ coincide | VideoServiceImpl.java:150 (INSERT video), :153 (videos/{idVideo}.{ext}) | — |
| 06_desarrollo.tex:108-109 | Análisis en cola solo para el Día 2; el Día 1 se guarda sin análisis | ✔ coincide | VideoServiceImpl.java:165-166 | — |
| 06_desarrollo.tex:110 | Gestión: crear, modificar, desactivar y reactivar cuentas; contraseñas temporales; eliminar experimentos; reiniciar análisis | ✔ coincide | AdminServiceImpl.java:128-150 (temporal), :202 (modificar); ExperimentoServiceImpl.java:565 (eliminar); RevisionServiceImpl.java:376 (reiniciar) | — |
| 06_desarrollo.tex:114-116 | El backend cambia el estado de un análisis solo al encolar, al reiniciar y al atender una pausa | ? no pude confirmar | no se hizo una búsqueda exhaustiva de UPDATE analisis en el backend; la transición de pausa está en PausaServiceImpl.java:200-202 (permiso) | — |
| 06_desarrollo.tex:118-120 | Borrar experimento: solo creador o administrador; 409 si hay análisis procesando o en pausa; los de cola salen de la cola | ✔ coincide | ExperimentoServiceImpl.java:543 (permiso de eliminar), :559, :604, :641 (409), :631 (en cola sale de la cola) y :656 (DELETE FROM analisis) | — |
| 06_desarrollo.tex:122 | Swagger apagado en el paquete de producción | ✔ coincide | docker-compose.yml:77 (SWAGGER_HABILITADO false) | — |
| 06_desarrollo.tex:123 | Orígenes permitidos limitados con CORS_ORIGINS | ✔ coincide | config/SeguridadConfig.java:79-91 | — |
| 06_desarrollo.tex:132 | Mensaje de error de inicio de sesión: «Correo o contraseña incorrectos» | ✔ coincide | service/AuthServiceImpl.java:47 (CREDENCIALES_INCORRECTAS) | — |
| 06_desarrollo.tex:136 | Crear experimento pide nombre, fecha y notas; grupo, tratamiento y especímenes se capturan después | ? no pude confirmar | la tabla experimento (001_esquema.sql:59-67) coincide; el flujo de la interfaz no se verificó | — |
| 06_desarrollo.tex:141-143 | Panel de administración: cuentas, estado, rol y contraseña temporal; estado del sistema y uso del disco | ? no pude confirmar | AdminServiceImpl.java:128-150 y :202 coinciden; la parte de estado del sistema y disco en la interfaz no se verificó | — |
| 06_desarrollo.tex:144-145 | Mi perfil: cambiar nombre, correo y contraseña propios | ? no pude confirmar | AuthServiceImpl.java:133-155 (contraseña) y :174-178 (nombre); el cambio de correo propio no se localizó | — |
| 06_desarrollo.tex:154-155 | El worker consulta la base cada 5 s (WORKER_POLL_SECONDS) | ✔ coincide | worker/comun.py:24 (POLL_SECONDS = 5) | — |
| 06_desarrollo.tex:158-160 | Toma el análisis en cola más antiguo con bloqueo que salta filas ya bloqueadas | ✔ coincide | worker/cola.py:95-113 (FOR UPDATE SKIP LOCKED en :101) | — |
| 06_desarrollo.tex:161-162 | Al arrancar, procesando vuelve a la cola; una pausa sin archivo se cancela | ✔ coincide | worker/cola.py:29 (reencolar_huerfanos); :50-62 (cancela si no hay pausa.json) | — |
| 06_desarrollo.tex:163 | Límite de una hora por paso; 30 minutos para la copia de revisión | ✔ coincide | worker/worker.py:45 (LIMITE_PASO_S = 3600); :46 (LIMITE_FFMPEG_S = 1800) | — |
| 06_desarrollo.tex:164 | Revisión del disco antes de cada análisis | ✔ coincide | worker/cola.py:168 (vigilar_disco antes de tomar el análisis) | — |
| 06_desarrollo.tex:165 | Etapas en orden: preprocesamiento, cilindros, clasificación | ✔ coincide | worker/worker.py:157, :187, :239 | — |
| 06_desarrollo.tex:166-169 | Error: diagnóstico y notificación, sin reintento; un fallo imprevisto sigue con el siguiente análisis | ✔ coincide | worker/cola.py:122-147 (marcar_error), :148 (marcar_error_imprevisto), :161-181 (vuelta) | — |
| 06_desarrollo.tex:170-171 | Éxito: escribe resultados, copia H.264, recuadros y marca completado | ✔ coincide | worker/worker.py:498 (libx264), :559 (estado completado) | — |
| 06_desarrollo.tex:174-175 | Cada notificación que crea el worker se envía también por correo | ✔ coincide | worker/comun.py:142-150 (notificar deja el correo pendiente); enviar_correos tras el commit (cola.py:84, :144) | — |
| 06_desarrollo.tex:177-179 | Seis tareas diarias: respaldo, videos vencidos, pausas huérfanas, reportes huérfanos, aviso de vencimiento y vigilancia del disco | ✔ coincide | worker/tareas_programadas.py:43-51 (correr_todas) | — |
| 06_desarrollo.tex:178 | WORKER_DAILY_HOUR por omisión a las 3 a. m. | ✔ coincide | worker/comun.py:25 (DAILY_HOUR = 3) | — |
| 06_desarrollo.tex:182-183 | Si el worker estaba apagado, corre al arrancar; guarda la última corrida; espera a un análisis en curso | ✔ coincide | worker/tareas_programadas.py:30-41 (ULTIMA_CORRIDA, correr_si_toca); worker/cola.py:161-170 (tareas dentro de la vuelta, secuencial) | — |
| 06_desarrollo.tex:198 | Preprocesamiento: archivo, especímenes y modelo disponibles | ✔ coincide | worker/worker.py:157-178 | — |
| 06_desarrollo.tex:200 | Cilindros: pausa si un espécimen no se ve tres segundos seguidos | ✔ coincide | worker/worker.py:225-233 (comprobación); :264-278 (definición) | — |
| 06_desarrollo.tex:202 | Clasificación: escribe resultados; error con diagnóstico | ✔ coincide | worker/worker.py:239-262 y :125-131 (fallo_del_pipeline) | — |
| 06_desarrollo.tex:210-211 | Un video con duración cero se rechaza al subirlo | ✔ coincide | VideoServiceImpl.java:133 (duracion.signum() <= 0) | — |
| 06_desarrollo.tex:213-214 | Rotación de 90° o 270° en los metadatos se corrige antes de continuar | ✔ coincide | pipeline/lib.py:69-72 (mapa de rotación 90/180/270) | — |
| 06_desarrollo.tex:221-224 | Alineación ORB con razón 0.75 y mínimo 12 coincidencias; RANSAC con umbral de 2.5 px | ✔ coincide | pipeline/lib.py:202-207 | — |
| 06_desarrollo.tex:224 | Si no logra alinear, usa la identidad | ? no pude confirmar | lib.py:216 registra fotogramas «sin puntos de apoyo»; la rama de identidad no se leyó | — |
| 06_desarrollo.tex:225-226 | Fondo: un cuadro por segundo (mínimo 10), percentil 10; mapa de ocupación | ✔ coincide | pipeline/lib.py:239-241 (percentil 10, mínimo 10); :248 (cada_occ) | — |
| 06_desarrollo.tex:227-228 | Geometría: bordes horizontales con Sobel; si quedan a menos del 25 % del alto, proporciones 0.22 y 0.84 | ✔ coincide | pipeline/lib.py:291 (Sobel), :312 (0.25), :313 (0.22/0.84) | — |
| 06_desarrollo.tex:229 | Un cilindro por espécimen registrado, numerados de izquierda a derecha | ? no pude confirmar | la numeración por posición no se verificó línea a línea | — |
| 06_desarrollo.tex:231-232 | Región de interés: −0.45 y +0.05 veces el ancho del cilindro | ✔ coincide | pipeline/lib.py:381-382 | — |
| 06_desarrollo.tex:233 | 15 rasgos por cilindro y ventana | ✔ coincide | pipeline/lib.py:35-41 (lista RASGOS de 15) | — |
| 06_desarrollo.tex:234-235 | Umbral de diferencia 45 y de brillo 80; componente conexa mayor; erosión elíptica de 21 px | ✔ coincide | pipeline/lib.py:20-22 (DIFF_THR 45, BRIGHT_THR 80, ERODE_TAIL 21); :37 (MORPH_ELLIPSE); :46-49 (componente mayor) | — |
| 06_desarrollo.tex:236 | Mediana móvil de 5 cuadros sobre el centroide | ✔ coincide | pipeline/lib.py:53-61 y :710 (medfilt k=5) | — |
| 06_desarrollo.tex:239 | Menos de 10 cuadros útiles en el modelo de fondo: la etapa falla | ✔ coincide | pipeline/lib.py:239-241 (mínimo 10) | — |
| 06_desarrollo.tex:242 | Pausa con tres segundos seguidos sin animal | ✔ coincide | worker/worker.py:264 (RACHA_PAUSA_S = 3) | — |
| 06_desarrollo.tex:248-251 | Clip de 16 cuadros a 4 por segundo; ventana de −1.5 a +2.5 s; recorte a 128 × 96; normalización por brillo | ? no pude confirmar | ventana, 16 cuadros y 4 por segundo: pipeline/cnn/recortar_seg.py:58-70 coinciden; 128 × 96: recortar.py:42; la normalización por brillo no se verificó | — |
| 06_desarrollo.tex:253-256 | Red de fusión: R(2+1)D-18 con Kinetics-400 y perceptrón con 15 rasgos y 7 reglas; tres salidas | ? no pude confirmar | backbone Kinetics-400 en fst_cnn.py:238-243 y tres clases en fst_cnn.py:66; el conteo de 7 reglas no se verificó | — |
| 06_desarrollo.tex:258 | 15 épocas, lotes de 16 clips, AdamW con decaimiento de pesos 1e-4 | ✔ coincide | pipeline/cnn/fst_cnn.py:71 (epocas=15, lote=16); :303 (weight_decay=1e-4) | — |
| 06_desarrollo.tex:259-260 | OneCycle: 1e-4 en el backbone y 1e-3 en las capas nuevas | ✔ coincide | fst_cnn.py:298-299 (lr y lr·10); :306 (OneCycleLR) | — |
| 06_desarrollo.tex:260-261 | Entropía cruzada con pesos por clase y suavizado de etiquetas 0.05 | ✔ coincide | fst_cnn.py:294 (CrossEntropyLoss, label_smoothing=0.05) | — |
| 06_desarrollo.tex:261-263 | Validación con GroupKFold de 5 particiones por video; modelo final con todos los datos | ✔ coincide | fst_cnn_seg.py:137 (partes=5), :153 (GroupKFold); :224-231 (final) | — |
| 06_desarrollo.tex:265-272 | Regla de tres pasos con umbral 0.80; conducta activa si nado + escalamiento ≥ 0.80 | ✔ coincide | pipeline/segundos.py:54-66 | — |
| 06_desarrollo.tex:274 | El umbral se aplica solo al clasificar, no al entrenar | ✔ coincide | fst_cnn.py:280-306 (entrenar no usa umbral) | — |
| 06_desarrollo.tex:277-280 | Sin ver: cuando la medición no produjo rasgos; no hay umbral | ✔ coincide | pipeline/segundos.py:97-104 (sin rasgos → conducta vacía) | — |
| 06_desarrollo.tex:282-285 | Se conservan los últimos 300 s por espécimen; se renumeran de 1 a 300 | ✔ coincide | worker/worker.py:44 (MAX_SEGUNDOS); :464 (renumerados de 1 a n) | — |
| 06_desarrollo.tex:290-294 | Pausa: imagen fija del segundo central de los tres; datos en un archivo junto a la imagen | ✔ coincide | worker/worker.py:395-433 (pausar_analisis) | — |
| 06_desarrollo.tex:296 | Primero el archivo y después el estado; crea notificación; libera el video | ✔ coincide | worker/worker.py:429-433 (estado 'en pausa' después del archivo) y notificación en la misma función | — |
| 06_desarrollo.tex:299-301 | Acciones: ajustar y continuar, confirmar y continuar, o cancelar; las pueden usar quien subió el video y el administrador | ✔ coincide | service/PausaServiceImpl.java:200-202 (creador o ROLE_ADMINISTRADOR) | — |
| 06_desarrollo.tex:302-303 | Ajustar: el backend lo agrega al archivo de pausa y el análisis vuelve a la cola conservando su fecha de encolado | ✔ coincide | worker/worker.py:319-346 (aceptar_racha, acumular_correccion); la conservación de fechaAnalisis está en la sesión de verificación previa, no re-leída aquí | — |
| 06_desarrollo.tex:304 | Confirmar: el análisis vuelve a la cola sin cambios en el recuadro | ✔ coincide | worker/worker.py:319-333 (aceptar_racha) | — |
| 06_desarrollo.tex:305-307 | Se reprocesa desde el principio; el recuadro corregido rige desde k−1 | ✔ coincide | worker/worker.py:335-365 (instante_de_la_pausa, desde_de_la_correccion) | — |
| 06_desarrollo.tex:308-311 | Cancelar: sin notificación; a las 72 h sin respuesta se cancela con notificación; revisión en cada vuelta | ✔ coincide | comun.py:26 (PAUSE_TIMEOUT_HOURS = 72); cola.py:50-86 (revisar_pausas en cada vuelta); :77 (cancelado por horas) | — |
| 06_desarrollo.tex:314-315 | Las transiciones de pausa solo se aplican si el análisis sigue en pausa | ✔ coincide | worker/cola.py:61 y :72 (AND estado = 'en pausa') | — |
| 06_desarrollo.tex:322 | Pesos: preprocesamiento 1, cilindros 9, clasificación 90 | ✔ coincide | worker/progreso.py:29 (PESO_ETAPA) | — |
| 06_desarrollo.tex:324-326 | Tope de 99 mientras corre; 100 al completar; respaldo 33/67/100; el progreso no se guarda en la base | ✔ coincide | service/ResultadosServiceImpl.java:167 (33/67/100); progreso en archivo (worker/progreso.py) | — |
| 06_desarrollo.tex:330-331 | Totales por minuto no se guardan; se calculan desde la tabla de segundos | ✔ coincide | 001_esquema.sql:141-142 (comentario D-52) | — |
| 06_desarrollo.tex:333-334 | Suma por minuto de conductas ≤ 60 s; sin ver aparte; cuatro conductas más sin ver = 60 s | ✔ coincide | estructura de SEGUNDO (001_esquema.sql:144-151); la cuenta por minuto es lógica de la consulta, no re-leída | — |
| 06_desarrollo.tex:336-338 | Comparación por grupo: solo Día 2; promedia y no suma; sin ver queda fuera; dato faltante | ? no pude confirmar | no se verificó la exclusión de sin ver en ResultadosServiceImpl | — |
| 06_desarrollo.tex:340-342 | Mezcla de niveles: no se promedia, se muestra por tanda; agrupado si algún segundo es conducta activa | ✔ coincide | service/RevisionServiceImpl.java:285-290 (regla de nivel); pantalla de resultados por tanda (ResultadosPage) | — |
| 06_desarrollo.tex:346-348 | Solo se puede revisar un análisis completado; el nivel se recalcula al guardar | ? no pude confirmar | la regla de «solo completado» para revisar no se localizó; el recálculo del nivel sí está en RevisionServiceImpl.java:285-290 | — |
| 06_desarrollo.tex:350-352 | Reiniciar: solo creador o administrador; borra resultados y revisión; la interfaz avisa antes | ✔ coincide | RevisionServiceImpl.java:369-380 (permiso y completado); el aviso en la interfaz no se verificó | — |
| 06_desarrollo.tex:356-358 | PDF con Sin ver y revisión humana; CSV con Grupo, Tanda, Espécimen, Tiempo (min:seg), Nado, Escalamiento, Inmovilidad, Conducta activa y Sin ver | ✔ coincide | service/TablaDeReporte.java:21 (ENCABEZADO con esos títulos); ExperimentoServiceImpl.java:756 (PDF, revisión humana) y :825 (encabezado del PDF por minuto) | — |
| 06_desarrollo.tex:358-360 | XLSX con Apache POI y estadísticas por grupo (media, DE, varianza); PDF de diagnóstico solo en error | ✔ coincide | service/ExcelDelExperimento.java:24; worker/diagnostico.py (solo en error) | — |
| 06_desarrollo.tex:363 | Un reporte sin resultados no se guarda en caché | ? no pude confirmar | no se localizó el código de esa regla | — |
| 06_desarrollo.tex:367 | El worker invalida la caché al terminar con resultados nuevos y al terminar con error | ✔ coincide | worker/worker.py:563 (invalidar_reportes); worker/cola.py:137 (error) | — |
| 06_desarrollo.tex:368-369 | El backend invalida al editar, eliminar experimento o tanda, corregir segundos, reiniciar y cambiar nombre o apellidos del creador | ✔ coincide | ExperimentoServiceImpl.java:522 (actualizar), :565 (eliminar), :617 (eliminarTanda); RevisionServiceImpl.java:295 (corregir), :399 (reiniciar); AuthServiceImpl.java:178; AdminServiceImpl.java:202 | — |
| 06_desarrollo.tex:372 | Pausa, cancelación y subida de video no invalidan la caché | ✔ coincide | worker/cola.py:61-77 y VideoServiceImpl.java no llaman a invalidar | — |
| 06_desarrollo.tex:380-382 | Correo con variables de servidor, puerto, usuario, contraseña y remitente; el worker usa un cliente SMTP de Python | ✔ coincide | EmailServiceImpl.java:23-24 (app.correo.remitente); worker/comun.py:152-185 (smtplib); docker-compose.yml:104-105 y portable/.env.example:98-104 | — |
| 06_desarrollo.tex:381 | Tiempo de espera de 10 segundos en el worker | ✔ coincide | worker/comun.py:152-185 (timeout 10) | — |
| 06_desarrollo.tex:383-384 | Sin reintentos; con SMTP_HOST vacío el worker no envía correos | ✔ coincide | worker/comun.py:155-160 (SMTP_HOST vacío); sin bucle de reintento | — |
| 06_desarrollo.tex:388-390 | Sin autorregistro; el administrador crea la cuenta; la contraseña temporal se muestra una vez y se envía por correo; la cuenta nace investigador, activa y con cambio obligatorio | ✔ coincide | AdminServiceImpl.java:128-150 (temporal, activo y cambio requerido = true; enviarTemporal) | — |
| 06_desarrollo.tex:392-393 | Mensajes de duplicado: «Ya existe una cuenta con ese identificador.» y «…con ese correo.» | ✔ coincide | AdminServiceImpl.java:36-37 (texto exacto) | — |
| 06_desarrollo.tex:395-397 | Desactivar: el usuario no vuelve a entrar; reactivar conserva contraseña; temporal solo en cuentas activas; no se borran cuentas | ✔ coincide | config/ConvertidorJwt.java:38 (cuenta inactiva rechazada); AdminServiceImpl.java:237 (temporal solo activas); no hay DELETE de usuario | — |
| 06_desarrollo.tex:399-401 | Siempre queda al menos un administrador activo; no se desactiva ni quita el rol a sí mismo; mensaje a Mi perfil | ✔ coincide | AdminServiceImpl.java:181, :231-232 (texto «Para cambiar tu propia cuenta usa Mi perfil.»); ReglaAdministradoresTest.java | — |
| 06_desarrollo.tex:405 | Token de sesión de 168 horas por defecto, configurable | ✔ coincide | application.properties:63 (app.jwt.horas=168); portable/.env.example:25 (JWT_EXPIRES_HOURS) | — |
| 06_desarrollo.tex:406-407 | Sin lista de tokens cancelados; cerrar sesión no escribe en la base | ✔ coincide | controller/AuthController.java:58-63 (logout solo responde 200) | — |
| 06_desarrollo.tex:409-410 | Enlace de recuperación que vence a los 60 minutos, con el vencimiento firmado dentro del token | ✔ coincide | application.properties:69 (app.reset.minutos=60); AuthServiceImpl.java:258 (expiresAt dentro del token) | — |
| 06_desarrollo.tex:414 | Backend: 162 pruebas JUnit en 16 clases | ≠ distinto | HEAD: 206 @Test en 23 archivos (06_pruebas.md §1; 218 con 12 pruebas sin commit en el árbol de trabajo). En 15489f4 había 163 @Test en 16 archivos: el número del documento ya era casi el de producción y HEAD creció después | posterior a 15489f4 |
| 06_desarrollo.tex:415 | Interfaz: 202 pruebas con Vitest, jsdom y Testing Library | ≠ distinto | HEAD: 331 casos en 27 archivos (06_pruebas.md §2); en 15489f4 había 16 archivos de prueba: el número creció después | posterior a 15489f4 |
| 06_desarrollo.tex:416 | Worker: 71 pruebas con unittest | ≠ distinto | HEAD: 93 métodos test_ en 8 archivos (06_pruebas.md §3). En 15489f4 había 71: el de HEAD añade test_modelo.py (22), posterior | posterior a 15489f4 |
| 06_desarrollo.tex:417 | Prueba de carga con cuatro sesiones simultáneas, sin subida de videos ni PDF | ? no pude confirmar | backend-spring/CHECKLIST.md:227 la describe; las cifras de respuesta no se verificaron | — |
| 06_desarrollo.tex:418 | El simulador worker_fake sirve para probar el sistema sin el pipeline real | ✘ no existe | no hay ningún archivo worker_fake en HEAD; solo se menciona en backend-spring/CHECKLIST.md, igual que en 15489f4 | ya estaba en 15489f4 |

**Conteo del bloque:** ✔ 94 · ≠ 4 · ✘ 1 · ? 12 (total 111)

## Bloque C · Requisitos no funcionales (`chapters/04_analisis.tex:373-483`, RNF-01 a RNF-14)

Lo comprobable con el código o con `06_pruebas.md`. Lo que depende de mediciones o pruebas que no están en el repositorio se marca como ? no pude confirmar.

| Referencia | Lo que afirma el documento | Resultado | Lo que hace el sistema (archivo:línea) | Origen (≠ / ✘) |
|---|---|---|---|---|
| 04_analisis.tex:373-378 | RNF-01: operaciones de interfaz responden en ≤ 3 s | ? no pude confirmar | no hay medición de tiempos de respuesta en el repositorio ni en 06_pruebas.md; la prueba de carga de 4 sesiones (backend-spring/CHECKLIST.md:227) no da tiempos aquí | — |
| 04_analisis.tex:380-391 | RNF-02: F1 ≥ 85 % por conducta, validación por video y conjunto de prueba propio | ? no pude confirmar | sin medición todavía; la validación en Colab está pendiente. La validación por video sí existe en el código (fst_cnn_seg.py:153, GroupKFold); estilos.js:21 dice que la exactitud todavía no está medida | — |
| 04_analisis.tex:393-397 | RNF-03: análisis en cola de noche terminan sin intervención | ? no pude confirmar | la cola secuencial y el sondeo cada 5 s existen (cola.py:95-113, comun.py:24); no hay prueba de larga duración que lo demuestre | — |
| 04_analisis.tex:400-405 | RNF-04: autenticación con JWT de expiración configurable; investigadores activos consultan experimentos de otros | ✔ coincide | application.properties:63 (168 h); ExperimentoServiceImpl.java:135 (listar no filtra por usuario) | — |
| 04_analisis.tex:404-405 | RNF-04: «verificada en pruebas de integración» | ? no pude confirmar | las pruebas de integración de autenticación (AdministracionYRecuperacionTest) dependen de FST_TEST_DB_URL y no se ejecutaron (06_pruebas.md §1) | — |
| 04_analisis.tex:408-412 | RNF-05: contraseñas con bcrypt y sal; temporales con cambio obligatorio | ✔ coincide | config/SeguridadConfig.java:45 (BCrypt); AdminServiceImpl.java:130 (temporal con cambioRequerido = true); FiltroCambioPendiente.java:43-47 | — |
| 04_analisis.tex:411-412 | RNF-05: «verificada en prueba funcional» | ? no pude confirmar | AdministracionYRecuperacionTest (gated por FST_TEST_DB_URL) no se ejecutó | — |
| 04_analisis.tex:415-421 | RNF-06: usuario sin experiencia completa el flujo y completa ≥ 90 % sin ayuda | ? no pude confirmar | es una prueba con personas; no hay registro en el repositorio | — |
| 04_analisis.tex:424-427 | RNF-07: funciona en Chrome, Firefox y Edge; sin instalar nada | ? no pude confirmar | sin instalación en el equipo: ✔ (aplicación web). Las pruebas en los tres navegadores no están en el repositorio | — |
| 04_analisis.tex:430-433 | RNF-08: videos se borran 30 días después del análisis exitoso; resultados se conservan | ✔ coincide | worker/tareas_programadas.py:68-78 (interval '30 days' desde fechaanalisis); resultados no se borran por tiempo | — |
| 04_analisis.tex:432-433 | RNF-08: aviso a menos de 7 días del borrado | ✔ coincide | worker/tareas_programadas.py:128-143 (avisar_vencimientos); el aviso visible lo calcula el backend (avisoVencimiento en GET /experiments, según backend-spring/CHECKLIST.md) | — |
| 04_analisis.tex:434-435 | RNF-08: tarea diaria a cargo del worker; aviso con indicador de días en la lista de experimentos | ? no pude confirmar | tarea diaria: tareas_programadas.py:43-51 y comun.py:25 (✔); el indicador visual en la lista no se verificó | — |
| 04_analisis.tex:438-440 | RNF-09: alerta al Administrador en el panel cuando el disco supera 80 % | ✔ coincide | worker/tareas_programadas.py:208-232 (alertar_disco a administradores); comun.py:27 (DISK_ALERT_PERCENT = 80) | — |
| 04_analisis.tex:439-440 | RNF-09: a 90 % se borran los videos más antiguos y se notifica al investigador | ✔ coincide | worker/tareas_programadas.py:235-268 (liberar_disco, ORDER BY fechacarga; notificar «video borrado»); comun.py:28 (DISK_CLEANUP_PERCENT = 90) | — |
| 04_analisis.tex:441-445 | RNF-09: 80/90 % son umbrales estándar según una referencia bibliográfica | ? no pude confirmar | afirmación bibliográfica; no es verificable con el código | — |
| 04_analisis.tex:448-451 | RNF-10: empaquetado con Docker Compose, sin dependencias en el anfitrión | ✔ coincide | docker-compose.yml; portable/iniciar.bat; portable/empaquetar.ps1 | — |
| 04_analisis.tex:451 | RNF-10: verificado en al menos dos equipos distintos | ? no pude confirmar | no hay registro en el repositorio | — |
| 04_analisis.tex:454-459 | RNF-11: módulo de clasificación modular y reconfigurable | ✔ coincide | separación en carpetas: backend-spring/, frontend/, worker/ y pipeline/ (pipeline/cnn/ con sus propios scripts) | — |
| 04_analisis.tex:458-459 | RNF-11: diseño modular verificado en revisión de arquitectura | ? no pude confirmar | es una revisión humana; no hay registro en el repositorio | — |
| 04_analisis.tex:462-466 | RNF-12: módulos independientes y código documentado | ✔ coincide | backend-spring/, frontend/, worker/ y pipeline/ separados (ver RNF-11) | — |
| 04_analisis.tex:463-464 | RNF-12: ESLint en JavaScript | ✔ coincide | frontend/eslint.config.js (script lint = eslint src en frontend/package.json) | — |
| 04_analisis.tex:463-464 | RNF-12: PEP 8 en Python | ? no pude confirmar | sin linter de Python configurado (no hay ruff, flake8, pylintrc ni setup.cfg en el repositorio) | — |
| 04_analisis.tex:465-466 | RNF-12: calidad revisada en cada cierre de bloque | ? no pude confirmar | proceso de revisión; no hay registro en el repositorio | — |
| 04_analisis.tex:469-471 | RNF-13: hasta 4 usuarios trabajando; análisis secuenciales uno por uno | ✔ coincide | cola.py:95-113 (un análisis a la vez, vuelta secuencial) | — |
| 04_analisis.tex:475-477 | RNF-13: prueba con 4 sesiones simultáneas y ≥ 1 análisis en cola | ✔ coincide | backend-spring/CHECKLIST.md:227 (prueba de carga con cuatro sesiones) | — |
| 04_analisis.tex:476-477 | RNF-13: sin degradación > 3 s en operaciones de interfaz | ? no pude confirmar | la cifra de tiempos de la prueba de carga no está en el repositorio | — |
| 04_analisis.tex:480-483 | RNF-14: la variación entre análisis repetidos del mismo video es de 5 a 10 % | ? no pude confirmar | no hay mediciones de repetibilidad en el repositorio; el pipeline no tiene una prueba que la compruebe | — |

**Conteo del bloque:** ✔ 12 · ≠ 0 · ✘ 0 · ? 15 (total 27)
