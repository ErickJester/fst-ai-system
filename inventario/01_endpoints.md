# 01 · Endpoints de la API

Fuente: el código de **HEAD (ba903f4, v1.9.2)**, leído con `git show HEAD:ruta` y `git ls-tree`. Para `ExperimentoServiceImpl.java` y `ReporteAlmacen.java`, que tienen cambios sin commit de M1B, se leyó HEAD y se ignoró `service/graficas/`. La columna «Desde» es `base` si la misma combinación de método y ruta existía en **15489f4** (producción v1.0.0), y `nuevo` si no.

**Conteo:** 48 endpoints en HEAD; 44 `base` y **4 `nuevo`** (ver al final).

Generado con un extractor que lee cada `@…Mapping` de los controllers, su firma, los campos del servicio que llama y los `HttpStatus` y mensajes `ErrorApi` del método del servicio. Los códigos generales de seguridad (401, 403) y del manejo global (400, 404 de ruta, 405, 413, 415, 500) están en su propia sección y **no** se repiten en cada fila.

## Tabla de endpoints

| ID | Desde | Método | Ruta | Quién puede llamarla | Parámetros | Cuerpo | Respuesta (éxito) | Códigos de error (y mensaje de ejemplo) | Controller:línea |
|---|---|---|---|---|---|---|---|---|---|
| 1 | base | `GET` | `/admin/users` | Administrador (rol ADMINISTRADOR, 403 a los demás); con contraseña temporal responde 401; regla del servicio: El rol debe ser Investigador o Administrador. | rol (String; query); estado (String; query) | — | envoltorio {data, error, status}; data: List<Map<String, Object>> | 200, 400 — ej.: El rol debe ser Investigador o Administrador.; El estado debe ser activa o inactiva. | `AdminController.java:33` |
| 2 | base | `POST` | `/admin/users` | Administrador (rol ADMINISTRADOR, 403 a los demás); con contraseña temporal responde 401 | peticion (CrearCuentaPeticion; cuerpo JSON) | JSON @Valid | envoltorio {data, error, status}; data: Map<String, Object> | 201, 409 — ej.: Ya existe una cuenta con ese identificador.; Ya existe una cuenta con ese correo.; correoRepetido / Ya existe una cuenta con ese correo. / Ya existe una cuenta con ese ident | `AdminController.java:48` |
| 3 | base | `PATCH` | `/admin/users/{id}` | Administrador (rol ADMINISTRADOR, 403 a los demás); con contraseña temporal responde 401 | id (String; ruta); peticion (ModificarCuentaPeticion; cuerpo JSON) | JSON @Valid | envoltorio {data, error, status}; data: Map<String, Object> | 200, 400, 404, 409 — ej.: El identificador institucional no se puede cambiar.; La cuenta  | `AdminController.java:53` |
| 4 | base | `POST` | `/admin/users/{id}/reset-temporal` | Administrador (rol ADMINISTRADOR, 403 a los demás); con contraseña temporal responde 401 | id (String; ruta) | — | envoltorio {data, error, status}; data: Map<String, Object> | 200, 404, 409 — ej.: La cuenta ; Para cambiar tu propia contraseña usa Mi perfil. | `AdminController.java:72` |
| 5 | base | `GET` | `/admin/system` | Administrador (rol ADMINISTRADOR, 403 a los demás); con contraseña temporal responde 401 | — | — | envoltorio {data, error, status}; data: Map<String, Object> | 200 | `AdminController.java:77` |
| 6 | base | `POST` | `/auth/login` | pública | peticion (LoginPeticion; cuerpo JSON) | JSON @Valid | envoltorio {data, error, status}; data: Map<String, Object> | 200, 401 — ej.: Correo o contraseña incorrectos | `AuthController.java:30` |
| 7 | base | `POST` | `/auth/recover` | pública | peticion (RecuperarPeticion; cuerpo JSON) | JSON @Valid | envoltorio {data, error, status}; data: void | 200 | `AuthController.java:36` |
| 8 | base | `POST` | `/auth/reset` | pública | peticion (RestablecerPeticion; cuerpo JSON) | JSON @Valid | envoltorio {data, error, status}; data: void | 200, 400, 422 — ej.: El enlace venció o ya se usó. | `AuthController.java:43` |
| 9 | base | `PATCH` | `/auth/password` | cualquier cuenta con sesión; también con contraseña temporal | peticion (CambioContrasenaPeticion; cuerpo JSON) | JSON @Valid | envoltorio {data, error, status}; data: Map<String, Object> | 200, 400, 401 — ej.: Sesión no válida o vencida; La contraseña actual no es correcta.; La contraseña nueva debe ser distinta de la actual. | `AuthController.java:52` |
| 10 | base | `POST` | `/auth/logout` | cualquier cuenta con sesión; también con contraseña temporal | — | — | envoltorio {data, error, status}; data: según el servicio | 200 | `AuthController.java:61` |
| 11 | nuevo | `GET` | `/dev/modelo-actual` | cualquier cuenta con sesión; con contraseña temporal responde 401 | — | — | envoltorio {data, error, status}; data: según el servicio | 200 | `DevModeloController.java:21` |
| 12 | nuevo | `GET` | `/experiments/{id}/report/excel` | cualquier cuenta con sesión; con contraseña temporal responde 401 | id (Integer; ruta) | — | archivo binario (sin envoltorio) | solo los generales | `ExcelExperimentoController.java:26` |
| 13 | base | `GET` | `/experiments` | cualquier cuenta con sesión; con contraseña temporal responde 401 | pagina (int; query; obligatorio); estado (String; query); fecha (String; query); tratamiento (String; query); q (String; query) | — | envoltorio {data, error, status}; data: Map<String, Object> | 200, 400 — ej.: El parámetro 'pagina' no tiene un valor válido; El parámetro 'estado' no tiene un valor válido; puede ser / ; El parámetro 'fecha' debe ser AAAA o AAAA-MM-DD | `ExperimentoController.java:68` |
| 14 | base | `GET` | `/experiments/{id}/report/pdf` | cualquier cuenta con sesión; con contraseña temporal responde 401 | id (Integer; ruta) | — | archivo binario (sin envoltorio) | solo los generales | `ExperimentoController.java:79` |
| 15 | base | `GET` | `/experiments/{id}/report/csv` | cualquier cuenta con sesión; con contraseña temporal responde 401 | id (Integer; ruta); grupo (Integer; query); tanda (String; query) | — | archivo binario (sin envoltorio) | 400, 404, 409 — ej.: Falta el grupo / grupo=<idGrupo> (y tanda=<letra> para una sola).; El grupo ; La tanda debe ser una letra, por ejemplo A. | `ExperimentoController.java:101` |
| 16 | base | `GET` | `/experiments/{id}/report/xlsx` | cualquier cuenta con sesión; con contraseña temporal responde 401 | id (Integer; ruta); grupo (Integer; query); tanda (String; query) | — | archivo binario (sin envoltorio) | 400, 404, 409 — ej.: Falta el grupo / grupo=<idGrupo> (y tanda=<letra> para una sola).; El grupo ; La tanda debe ser una letra, por ejemplo A. | `ExperimentoController.java:111` |
| 17 | base | `GET` | `/experiments/{id}` | cualquier cuenta con sesión; con contraseña temporal responde 401 | id (Integer; ruta) | — | envoltorio {data, error, status}; data: Map<String, Object> | 200, 404 — ej.: El experimento  | `ExperimentoController.java:134` |
| 18 | base | `GET` | `/experiments/{id}/groups` | cualquier cuenta con sesión; con contraseña temporal responde 401 | id (Integer; ruta); q (String; query) | — | envoltorio {data, error, status}; data: List<Map<String, Object>> | 200, 404 — ej.: El experimento  | `ExperimentoController.java:141` |
| 19 | base | `POST` | `/experiments` | cualquier cuenta con sesión; con contraseña temporal responde 401 | experimento (Experimento; cuerpo JSON) | JSON @Valid | envoltorio {data, error, status}; data: Experimento | 201, 404, 409 — ej.: El usuario ; Ya existe un experimento con el nombre ' | `ExperimentoController.java:149` |
| 20 | base | `PUT` | `/experiments/{id}` | cualquier cuenta con sesión; con contraseña temporal responde 401; regla del servicio: Solo quien creó el experimento o un Administrador puede editarlo | experimento (Experimento; cuerpo JSON); id (Integer; ruta) | JSON @Valid | envoltorio {data, error, status}; data: Experimento | 200, 403, 409 — ej.: Solo quien creó el experimento o un Administrador puede editarlo; Ya existe otro experimento con el nombre ' | `ExperimentoController.java:157` |
| 21 | base | `DELETE` | `/experiments/{id}/tandas/{idTanda}` | cualquier cuenta con sesión; con contraseña temporal responde 401; regla del servicio: Solo quien creó el experimento o un Administrador puede eliminar una tanda | id (Integer; ruta); idTanda (Integer; ruta) | — | envoltorio {data, error, status}; data: void | 403, 404 — ej.: La tanda ; Solo quien creó el experimento o un Administrador puede eliminar una tanda | `ExperimentoController.java:166` |
| 22 | base | `DELETE` | `/experiments/{id}` | cualquier cuenta con sesión; con contraseña temporal responde 401; regla del servicio: Solo quien creó el experimento o un Administrador puede eliminarlo | id (Integer; ruta) | — | envoltorio {data, error, status}; data: void | 403 — ej.: Solo quien creó el experimento o un Administrador puede eliminarlo | `ExperimentoController.java:175` |
| 23 | base | `GET` | `/health` | pública | — | — | envoltorio {data, error, status}; data: según el servicio | 200 | `HealthController.java:27` |
| 24 | base | `GET` | `/notifications` | cualquier cuenta con sesión; con contraseña temporal responde 401 | — | — | envoltorio {data, error, status}; data: List<Map<String, Object>> | 200 | `NotificacionController.java:28` |
| 25 | base | `GET` | `/notifications/unread-count` | cualquier cuenta con sesión; con contraseña temporal responde 401 | — | — | envoltorio {data, error, status}; data: Map<String, Object> | 200 | `NotificacionController.java:36` |
| 26 | base | `PATCH` | `/notifications/{id}` | cualquier cuenta con sesión; con contraseña temporal responde 401 | id (Integer; ruta); cuerpo (Map<String, Object>; cuerpo JSON) | JSON Map<String, | envoltorio {data, error, status}; data: Map<String, Object> | 200, 400, 404 — ej.: No existe esa notificación. | `NotificacionController.java:44` |
| 27 | base | `GET` | `/experiments/{id}/analyses/{idAnalisis}/pause` | cualquier cuenta con sesión; con contraseña temporal responde 401 | id (Integer; ruta); idAnalisis (Integer; ruta) | — | envoltorio {data, error, status}; data: Map<String, Object> | 200 | `PausaController.java:28` |
| 28 | base | `GET` | `/experiments/{id}/analyses/{idAnalisis}/pause/image` | cualquier cuenta con sesión; con contraseña temporal responde 401 | id (Integer; ruta); idAnalisis (Integer; ruta) | — | archivo binario (sin envoltorio) | 404 — ej.: La imagen de la pausa del análisis  | `PausaController.java:34` |
| 29 | base | `POST` | `/experiments/{id}/analyses/{idAnalisis}/pause/adjust` | cualquier cuenta con sesión; con contraseña temporal responde 401 | id (Integer; ruta); idAnalisis (Integer; ruta); recuadro (Map<String, Object>; cuerpo JSON) | JSON Map<String, | envoltorio {data, error, status}; data: void | 200, 400, 409, 422 — ej.: El cilindro ; El recuadro no es válido / debe caer dentro de la imagen con x0 < x1 y y0 < y1; El recuadro casi no cambió / ningún borde se movió más de  | `PausaController.java:41` |
| 30 | base | `POST` | `/experiments/{id}/analyses/{idAnalisis}/pause/continue` | cualquier cuenta con sesión; con contraseña temporal responde 401 | id (Integer; ruta); idAnalisis (Integer; ruta) | — | envoltorio {data, error, status}; data: void | 200, 409 — ej.: El análisis ya no está en pausa | `PausaController.java:48` |
| 31 | base | `POST` | `/experiments/{id}/analyses/{idAnalisis}/cancel` | cualquier cuenta con sesión; con contraseña temporal responde 401 | id (Integer; ruta); idAnalisis (Integer; ruta) | — | envoltorio {data, error, status}; data: void | 200, 409 — ej.: El análisis ya no está en pausa | `PausaController.java:55` |
| 32 | base | `GET` | `/experiments/{id}/status` | cualquier cuenta con sesión; con contraseña temporal responde 401 | id (Integer; ruta) | — | envoltorio {data, error, status}; data: Map<String, Object> | 200 | `ResultadosController.java:38` |
| 33 | base | `GET` | `/experiments/{id}/results` | cualquier cuenta con sesión; con contraseña temporal responde 401 | id (Integer; ruta) | — | envoltorio {data, error, status}; data: Map<String, Object> | 200 | `ResultadosController.java:43` |
| 34 | base | `GET` | `/experiments/{id}/results/byminute` | cualquier cuenta con sesión; con contraseña temporal responde 401 | id (Integer; ruta) | — | envoltorio {data, error, status}; data: Map<String, Object> | 200 | `ResultadosController.java:48` |
| 35 | base | `GET` | `/experiments/{id}/results/blocks` | cualquier cuenta con sesión; con contraseña temporal responde 401 | id (Integer; ruta) | — | envoltorio {data, error, status}; data: Map<String, Object> | 200 | `ResultadosController.java:53` |
| 36 | base | `GET` | `/experiments/{id}/results/groups` | cualquier cuenta con sesión; con contraseña temporal responde 401 | id (Integer; ruta) | — | envoltorio {data, error, status}; data: List<Map<String, Object>> | 200 | `ResultadosController.java:58` |
| 37 | nuevo | `GET` | `/experiments/{id}/results/charts` | cualquier cuenta con sesión; con contraseña temporal responde 401 | id (Integer; ruta) | — | envoltorio {data, error, status}; data: Map<String, Object> | 200 | `ResultadosController.java:64` |
| 38 | base | `GET` | `/experiments/{id}/dev/salida-modelo` | cualquier cuenta con sesión; con contraseña temporal responde 401 | id (Integer; ruta); grupo (Integer; query); tanda (String; query) | — | archivo binario (sin envoltorio) | 400, 404 — ej.: No encontrado; Falta el grupo / grupo=<idGrupo> (y tanda=<letra> para una sola).; El grupo  | `ResultadosController.java:75` |
| 39 | base | `GET` | `/experiments/{id}/analyses/{idAnalisis}/control` | cualquier cuenta con sesión; con contraseña temporal responde 401 | id (Integer; ruta); idAnalisis (Integer; ruta) | — | archivo binario (sin envoltorio) | 404 — ej.: El análisis  | `ResultadosController.java:100` |
| 40 | base | `GET` | `/experiments/{id}/analyses/{idAnalisis}/diagnostic` | cualquier cuenta con sesión; con contraseña temporal responde 401 | id (Integer; ruta); idAnalisis (Integer; ruta) | — | archivo binario (sin envoltorio) | 404 — ej.: El análisis ; El archivo de diagnóstico del análisis  | `ResultadosController.java:110` |
| 41 | base | `GET` | `/experiments/{id}/analyses/{idAnalisis}/review` | cualquier cuenta con sesión; con contraseña temporal responde 401 | id (Integer; ruta); idAnalisis (Integer; ruta) | — | envoltorio {data, error, status}; data: Map<String, Object> | 200 | `RevisionController.java:32` |
| 42 | base | `POST` | `/experiments/{id}/analyses/{idAnalisis}/video-token` | cualquier cuenta con sesión; con contraseña temporal responde 401; regla del servicio: Solo quien subió el video o un Administrador puede verlo. | id (Integer; ruta); idAnalisis (Integer; ruta) | — | envoltorio {data, error, status}; data: Map<String, Object> | 200, 403, 409 — ej.: Solo quien subió el video o un Administrador puede verlo.; El video ya no está disponible. | `RevisionController.java:38` |
| 43 | base | `GET` | `/experiments/{id}/analyses/{idAnalisis}/video` | pública (sin cabecera; exige token de video ?vt=) | id (Integer; ruta); idAnalisis (Integer; ruta); "vt" ((name =; query; obligatorio) | — | archivo binario (sin envoltorio) | 404 — ej.: El video ya no existe. | `RevisionController.java:47` |
| 44 | base | `PUT` | `/experiments/{id}/analyses/{idAnalisis}/segundos` | cualquier cuenta con sesión; con contraseña temporal responde 401; regla del servicio: Solo quien subió el video o un Administrador puede corregir este análisis. | id (Integer; ruta); idAnalisis (Integer; ruta); cuerpo (Map<String, Object>; cuerpo JSON) | JSON (required | envoltorio {data, error, status}; data: Map<String, Object> | 200, 403, 409 — ej.: Solo quien subió el video o un Administrador puede corregir este análisis.; Sin el video no se puede corregir. | `RevisionController.java:57` |
| 45 | base | `POST` | `/experiments/{id}/analyses/{idAnalisis}/restart` | cualquier cuenta con sesión; con contraseña temporal responde 401; regla del servicio: Solo quien subió el video o un Administrador puede reiniciar este análisis. | id (Integer; ruta); idAnalisis (Integer; ruta) | — | envoltorio {data, error, status}; data: Map<String, Object> | 202, 403, 409 — ej.: Solo quien subió el video o un Administrador puede reiniciar este análisis.; Solo se puede reiniciar un análisis completado; este está ; Sin el video no se puede reiniciar el análisis. | `RevisionController.java:64` |
| 46 | nuevo | `GET` | `/experiments/{id}/results/seconds` | cualquier cuenta con sesión; con contraseña temporal responde 401 | id (Integer; ruta); grupo (Integer; query); tanda (String; query) | — | envoltorio {data, error, status}; data: según el servicio | 200 | `SegundosController.java:24` |
| 47 | base | `PATCH` | `/users/me` | cualquier cuenta con sesión; con contraseña temporal responde 401 | peticion (PerfilPeticion; cuerpo JSON) | JSON @Valid | envoltorio {data, error, status}; data: Map<String, Object> | 200, 401, 409 — ej.: Sesión no válida o vencida; Ya existe una cuenta con ese correo. | `UsuarioController.java:29` |
| 48 | base | `POST` | `/experiments/{id}/groups/{grupo}/videos` | cualquier cuenta con sesión; con contraseña temporal responde 401; regla del servicio: Solo quien creó el experimento sube sus videos. | id (Integer; ruta); grupo (String; ruta); archivo (MultipartFile; query); sesion (String; query); tipo (String; query); tratamiento (String; query); nCilindros (Integer; query); ordinal (Integer; query) | multipart/form-data: @RequestParam(required = false) MultipartFile archivo | envoltorio {data, error, status}; data: Map<String, Object> | 201, 400, 403, 404, 409, 500 — ej.: El experimento ; Solo quien creó el experimento sube sus videos.; La etiqueta del grupo no puede estar vacía. | `VideoController.java:30` |

## Endpoints `nuevo` (posteriores a producción)

- **11** `GET /dev/modelo-actual`, en `DevModeloController.java:21`
- **12** `GET /experiments/{id}/report/excel`, en `ExcelExperimentoController.java:26`
- **37** `GET /experiments/{id}/results/charts`, en `ResultadosController.java:64`
- **46** `GET /experiments/{id}/results/seconds`, en `SegundosController.java:24`

## Envoltorio de respuesta

Toda respuesta JSON de la API tiene la forma `{"data": …, "error": null, "status": 200}` (`utilerias/Respuesta.java`). Con error, `data` es null y `error` lleva el mensaje. Las respuestas que son un archivo (PDF, CSV, XLSX, JPEG y PNG de la pausa) van sin envoltorio, y también las que sirven el video. El mensaje de error que llega al cliente es el texto de la regla (`ErrorApi`), nunca el de la excepción.

## Errores generales (fuera del código de cada endpoint)

| Código | Cuándo | Dónde (HEAD) |
|---|---|---|
| 401 | Sin cabecera `Authorization`, token vencido o mal firmado, cuenta inactiva o borrada, o contraseña temporal (solo se dejan `PATCH /auth/password`, `POST /auth/logout` y `GET /health`). Textos: «Sesión no válida o vencida» y «Debes cambiar tu contraseña antes de continuar». | `config/SeguridadConfig.java:121,124`; `config/FiltroCambioPendiente.java:36`; `config/ConvertidorJwt.java:39` |
| 403 | Ruta `/admin/**` sin rol ADMINISTRADOR, o regla de dueño del servicio. Texto: «No tienes permiso para esta acción». | `config/SeguridadConfig.java:115,122,125`; `controller/ManejadorErrores.java:113` |
| 400 | Campo inválido en `@Valid` («El campo 'x' …»); JSON mal formado; parámetro con tipo incorrecto (p. ej. `/experiments/abc`); parámetro que falta; codificación no UTF-8. | `controller/ManejadorErrores.java:48,57,63,71`; y los `ErrorApi` de cada servicio |
| 404 | Ruta inexistente («La ruta no existe»). Los ids que no existen los da cada servicio. | `controller/ManejadorErrores.java:76` |
| 405 | Método no permitido en la ruta. | `controller/ManejadorErrores.java:81` |
| 409 | Choque de datos (llave única o foránea) que ningún servicio revisó antes. | `controller/ManejadorErrores.java:99` |
| 413 | Archivo o petición mayor que `MAX_UPLOAD_MB` («El archivo excede el límite de N MB»). | `controller/ManejadorErrores.java:92` |
| 415 | Tipo de contenido no soportado. | `controller/ManejadorErrores.java:86` |
| 422 | Contraseña nueva inválida en `POST /auth/reset` (también `PATCH /auth/password` la usa con 400). | `service/AuthServiceImpl.java:239-241` |
| 500 | Excepción no prevista. **No** se envía el mensaje al cliente. Un error de base sale como 500 con «Error de la base de datos». | `controller/ManejadorErrores.java:105` y el catch-all del mismo archivo |

## CORS (`config/SeguridadConfig.java:83-96`)

- Orígenes: `app.cors.origenes`, desde la variable **`CORS_ORIGINS`** (lista separada por comas); por omisión `http://localhost:5173`.
- Métodos: GET, POST, PUT, PATCH, DELETE, OPTIONS. Cabeceras permitidas: `Authorization`, `Content-Type`. Cabecera expuesta: `Content-Disposition`.
- Se aplica a `/**`. En producción la web y la API salen por el mismo nginx, así que las peticiones no son de otro origen.

## Límites de subida

- `spring.servlet.multipart.max-file-size` y `max-request-size` = **`MAX_UPLOAD_MB`** MB (por omisión 2048), en `application.properties`. Al pasarse, 413.
- nginx de producción: `client_max_body_size 2048m` (`frontend/nginx.conf:26`). **Este tope no sale de la variable**: está escrito en el archivo y se cambia a mano.
- En `/api/` el buffer de la petición está apagado (`proxy_request_buffering off`), para que el video se envíe al backend según llega.

## Gateway: nginx del contenedor web (`frontend/nginx.conf`, usado por `Dockerfile.prod`)

- Escucha en el puerto 80 dentro del contenedor. Raíz `/usr/share/nginx/html` (la web compilada).
- **`/api/`** → `http://api:8000`, quitando el prefijo `/api` (`rewrite ^/api/(.*)$ /$1 break`). Resolver de Docker `127.0.0.11`, `valid=30s`.
  - `Host $http_host` (conserva el puerto), `X-Real-IP`, `X-Forwarded-For`, `X-Forwarded-Proto`.
  - Oculta `X-Content-Type-Options` y `X-Frame-Options` del backend (los pone nginx).
  - `proxy_read_timeout 300s` y `proxy_send_timeout 300s`.
- **Expresión regular `^/api/experiments/\d+/analyses/\d+/video$`**: misma configuración de proxy, pero con `error_log … crit`, para que el token `?vt=` del video no quede en el registro de errores. Como es expresión regular, gana a `/api/`.
- Registro de acceso sin cadena de consulta (`log_format sin_args`): no se registran `$args` ni `$request`.
- `location = /salud` → 200 «ok» (healthcheck del contenedor, sin registro).
- `/assets/` → caché de un año (`immutable`).
- `/` → `try_files $uri /index.html`, con `Cache-Control: no-cache`.
- Cabeceras de seguridad en todas las respuestas (`always`): `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: same-origin`. Sin Content-Security-Policy. `server_tokens off`.
- gzip para CSS, JavaScript, JSON y SVG.

## Variables de configuración que afectan a los endpoints (solo nombres y valores por omisión no secretos)

| Variable | Por omisión (`application.properties`) | Efecto |
|---|---|---|
| `JWT_EXPIRES_HOURS` | 168 | Vigencia de la sesión (`app.jwt.horas`) |
| `VIDEO_TOKEN_MINUTES` | 120 | Vigencia del permiso de video (`app.video.minutos`) |
| `RESET_LINK_MINUTES` | 60 | Vigencia del enlace de restablecer (`app.reset.minutos`) |
| `MAX_UPLOAD_MB` | 2048 | Tamaño máximo de subida |
| `CORS_ORIGINS` | http://localhost:5173 | Orígenes permitidos |
| `MODO_DESARROLLADOR` | false | Activa `/experiments/{id}/dev/salida-modelo` y `/dev/modelo-actual`; sin él responden 404 |
| `SWAGGER_HABILITADO` | true | Documentación en `/documentacion/swagger-ui.html` |
| `ARCHIVOS_DIR` | /data/archivos | Raíz del volumen de archivos |
| `WEB_URL` | (vacío) | Base de los enlaces de los correos (`app.web.url`) |
| `PAUSA_UMBRAL_RECUADRO_PX` | 3 | Umbral de la pausa |

## Qué no pude confirmar

- Los códigos de error por fila salen del método del servicio que llama el controller. Si un controller llama a más de un servicio, se toman los de todos los métodos llamados. «solo los generales» significa que el método no tiene `ErrorApi` propio.
- El texto de «Quién puede llamarla» para los endpoints de dueño (creador, o quien subió el video) se toma de los mensajes de error del servicio. No hay una regla separada que lo confirme.
- No revisé `poc-backend-java/`, que es otro proyecto y queda fuera de alcance.
- No confirmé valores en tiempo de ejecución: el inventario es de código, no de un despliegue.

