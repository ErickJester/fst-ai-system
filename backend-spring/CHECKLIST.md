# Checklist del backend FST en Spring Boot

Sale de `INSTRUCCIONES-WEB.md` (guía del proyecto TT 2026-B066), traducido de Flask a Spring Boot. Sigue el
orden de construcción de la sección 12 de la guía. Entre paréntesis va la sección o regla de origen.

No cubre el frontend ni el SQL y Docker de los anexos. Las decisiones de integración con el frontend del 15-oct-2026
están reflejadas abajo y explicadas en `PARA_VANESA_API.md` (rama `feat/frontend-v2-servicios`). Lo que Vanesa
propuso después (18-oct-2026) está en la sección 12.

**Cómo leerlo:** `[x]` = hecho y verificado en el código. `[ ]` = pendiente. Si una casilla vacía trae una nota
"hecho:", ya existe una parte y la nota dice qué falta.

**Última revisión del estado (23-oct-2026, versión 0.80.0):** el backend compila y corre en Docker junto con la base,
el worker real (modelo `fst_cnn_seg.pt`) y el frontend de Vanesa; el compose definitivo está en la raíz (proyecto
`fst-portable`, se levanta con `portable\iniciar.bat`). Probado con un video real (`AcuBN1.MOV`, 18-oct) y por Vanesa
en el navegador contra nuestro backend (20-oct, `e66ce40`). **En curso:** la pausa de R-15 (§6 y §10), detenida a pedido
de Erick el 23-oct con la parte de fst_auto a medias. **Sin hacer:** administración y recuperación de contraseña (§3),
reinicio (§6), bloques de 5 s y CSV y XLSX (§8) y el correo de cada notificación (§9).

## 0. Base y Docker
- [x] `docker compose up` levanta `db`, `backend`, `worker` y `mailhog` (dev), con volúmenes `videos_reportes` y `pgdata` (§2)
- [x] `.env.example` con todas las variables de §2, más `VIDEO_TOKEN_MINUTES` (120). Las de autenticación y `ADMIN_*` el backend las lee desde la sección 2
- [x] `application.properties` lee de variables de entorno lo que existe hoy (DB, SMTP, límite de subida)
- [x] Endpoint `/health`
- [x] Swagger con todas las rutas: sin `paths-to-match`, springdoc documenta también las que se agreguen (verificado en `/v3/api-docs`)
- [x] CORS para el frontend (`localhost:5173`)
- [x] Rutas guardadas en la BD relativas a la raíz del volumen, que en el contenedor es `/data/archivos` (§2): `videos/{idVideo}.{ext}`
- [x] Límite de subida configurable, y `413` si se excede (§11.5): `ManejadorErrores` responde `{data: null, error, status: 413}`; probado con `MAX_UPLOAD_MB=1` y un archivo de 3 MB
- [x] ffmpeg en el Dockerfile del backend (hace falta para §5); `ffprobe -version` corre en el contenedor
- [x] Puerto del backend `8084`, dentro y fuera de Docker: el `8080` lo ocupa Traefik de CVAT en esta máquina. Vanesa ya lo usa (`VITE_API_BASE=http://localhost:8084` en `PARA_ERICK.md`)
- [ ] `docker-compose.yml` definitivo en la raíz (commit `99a016d`, 23-oct): proyecto `fst-portable`, servicios `db`, `api`, `worker` y `web`, que se levanta con `portable\iniciar.bat`; el de desarrollo sigue en `backend-spring/`. Hecho: verificado solo con `docker compose config` (rutas, volúmenes y variables); falta probarlo en la otra PC

## 1. Base de datos
- [x] SQL del Anexo B con los dominios cerrados, `UNIQUE` y llaves foráneas `NO ACTION` (§5), incluido `tipo_conducta`
- [x] `ddl-auto=validate`
- [x] 12 entidades, sin `Conducta`, `Intervalo` ni `Presenta` (§4.9, D-102)
- [x] `ANALISIS.idVideo` único
- [x] `rutaDiagnostico` solo se llena si `estado = 'error'` (`CHECK` en el SQL)
- [x] Cardinalidades validadas en el servicio, sin `CHECK` de conteo: tanda de 2 a 4 especímenes, grupo con 1 o más tandas, mínimo 2 especímenes por grupo (§5). Tanda de 2 a 4: `VideoServiceImpl` rechaza `nCilindros` fuera de ese rango y crea exactamente esos especímenes. Grupo con 1 o más tandas: un grupo solo nace dentro de la misma transacción que su primera tanda (`resolverDestino`; si algo falla se deshace todo, revisado en el código) y, desde el 23-oct, al eliminar su última tanda el grupo se borra en la misma transacción (probado en desarrollo: la única tanda de un grupo → `204` y el grupo desaparece; una de dos → el grupo se conserva). Mínimo 2 especímenes por grupo: sale de las dos anteriores
- [x] Contraseñas con bcrypt, nunca en texto plano: `BCryptPasswordEncoder` en el login y en `AdminInicial`, y el hash con `@JsonIgnore`
- [x] Valores de dominio como enums de Java en lugar de `String` (23-oct): los 9 dominios de la base tienen su enum en `domain.valores` (`TipoGrupo`, `SesionVideo`, `FormatoReporte`, `TipoNotificacion`, `EstadoAnalisis`, `EtapaAnalisis`, `NivelClasifAnalisis`, `OrigenSegundo`, `TipoConducta`), con el texto exacto de la base (`valor()`, `desdeValor`, `@JsonValue`) y un conversor JPA por enum en las entidades, que conservan su `columnDefinition`. Las listas que se validaban a mano salen de los enums. Probado en desarrollo: el backend arranca con `ddl-auto=validate`; `GET /experiments/{id}`, `/status`, `/results` y `/results/byminute` devuelven los mismos textos que la base; y borrar un experimento con reporte y notificaciones (lectura de entidades por los conversores) da `204`. No se probó la lectura de `Segundo.conducta` nula ni de `Analisis` por los conversores: ningún servicio lee esas entidades hoy (todo va por SQL directo)

## 2. Autenticación
- [x] Spring Security con JWT de larga duración, configurable con `JWT_EXPIRES_HOURS` (§11.1): Nimbus HS256 con `tipo = sesion`, token solo en la cabecera (en la URL o alterado da `401`), y `ConvertidorJwt` revisa en la base en cada petición si la cuenta sigue activa (desactivada da `401` aunque el token no haya vencido) y si es Administrador. No arranca con un `JWT_SECRET` de menos de 32 caracteres
- [x] `POST /auth/login` con error `401` genérico (R-01), el mismo con contraseña mala, correo inexistente o cuenta inactiva. La respuesta trae `token`, `idInstitucional`, `nombre`, `apellidos`, `correo`, `rol` y `cambioRequerido`, sin el hash
- [x] `POST /auth/logout`, sin lista de bloqueo: pide token (§6) y responde `200`
- [x] `PATCH /auth/password`, y el cambio es obligatorio si `cambioRequerido`: mientras no la cambie, todo salvo esta ruta y el logout responde `403`; mínimo 8 caracteres y máximo 72 bytes (límite de bcrypt)
- [ ] `POST /auth/recover` envía un enlace JWT firmado por correo, con vigencia `RESET_LINK_MINUTES`
- [ ] `POST /auth/reset`
- [x] `PATCH /users/me` (perfil): nombre, apellidos y correo, `409` si el correo es de otra cuenta
- [x] Todo exige JWT salvo login, recover, reset, `/health` y Swagger: sin token se responde `401` con envoltorio; CORS centralizado y el preflight de `localhost:5173` pasa
- [x] Respuesta con envoltorio `{data, error, status}`, salvo las rutas que devuelven archivo (§6): `Respuesta.ok/error` en `/experiments` y `/health`, y `401`/`403` de Spring Security escritos con la misma forma
- [x] Manejo de errores global con `@RestControllerAdvice` (`ManejadorErrores` + `ErrorApi`) en lugar de mapas armados a mano: 400, 404, 405, 409, 413, 415 y 500, con el mensaje en `error`, sin `trace` y sin la causa en los 500
- [x] Comando para crear el primer administrador con `ADMIN_*`, que ponga `cambioRequerido = true` y lo inserte en `ADMINISTRADOR` (§11.7): `AdminInicial` al arrancar; idempotente (al reiniciar dice "ya existe" y no duplica), sin escribir la contraseña en el log; con `ADMIN_ID` vacío solo avisa

## 3. Administración (§6, R-07, R-16)
- [ ] Verificación de rol Administrador en el backend en cada `/admin/*`, con `403` si no lo es
- [ ] `GET /admin/users` con filtros de rol y estado
- [ ] `POST /admin/users` genera una contraseña temporal, la envía por correo y la muestra una sola vez
- [ ] `409` si el identificador o el correo ya existen
- [ ] `PATCH /admin/users/{id}` modifica datos y rol, o desactiva con `activo = false`, nunca con `DELETE`
- [ ] `409` al desactivar la última cuenta de Administrador activa o quitarle el rol (R-16)
- [ ] `GET /admin/system` con disco y cola

## 4. Experimentos
- [x] `POST /experiments` con nombre único y fecha: `201`, `400`, `404`, `409` y `500`; el creador sale del JWT y se ignora el `idInstitucional` del cuerpo
- [ ] `GET /experiments` con filtros `estado`, `fecha` y `tratamiento`, paginado, con `nGrupos` y `avisoVencimiento`. Hecho: `{pagina, total, items}` de 20 en 20, con `nGrupos`, `avisoVencimiento`, `creador` y los campos de la sección 12, en 4 consultas por página. Falta: los filtros en el servidor (hoy el frontend filtra en el navegador)
- [x] `GET /experiments/{id}` con el árbol de grupos, tandas y videos; cada video trae `fecha`, `videoDisponible`, `idAnalisis` y `subidoPor`. Limitación: `VIDEO` no guarda quién lo subió, así que `subidoPor` es el creador del experimento
- [x] El estado del experimento se calcula, no se guarda: `error` > `cancelado` > `en pausa` > `procesando` > `en cola` > `completado`, además de "sin videos" y "en espera del Día 2" (§4.8)
- [x] `GET /experiments/{id}/groups?q=` sugiere grupos y la siguiente tanda (sin distinguir mayúsculas ni acentos)
- [x] `PUT /experiments/{id}` con `404` y `409` por nombre repetido
  - [x] Nombre, fecha y notas; el creador no cambia
  - [x] `404` si no existe y `409` si el nombre ya es de otro
- [x] `DELETE /experiments/{id}`: solo el creador o un Administrador (R-08); borra el experimento completo (D-87, 23-oct) y responde `204`
  - [x] `404` si no existe
  - [x] `403` si es otro usuario (probado con una segunda cuenta Investigador en desarrollo, 23-oct)
  - [x] `409` si hay un análisis `procesando` o `en pausa` (probado con los dos estados: no se borró ninguna fila ni archivo). Antes bloquea con `FOR UPDATE`, de arriba hacia abajo, el experimento, sus grupos y sus tandas, para que una subida simultánea espere y falle con `409` (commit `5de79cf`, 23-oct); la concurrencia se probó con dos sesiones de `psql`, no con la API
  - [x] Los `en cola` salen de la cola
  - [x] Borra de las hojas hacia arriba, en una transacción: `SEGUNDO`, `OBSERVACION`, `ANALISIS`, `VIDEO`, `ESPECIMEN`, `TANDA`, `GRUPO`, `REPORTE`, `NOTIFICACION`, `EXPERIMENTO`. Probado con 2 grupos, 3 tandas y 3 análisis en cola: los conteos de las 10 tablas quedaron iguales a los de antes de crearlo. Comparte los borrados con "Eliminar una tanda"
  - [x] Borra también la carpeta `analisis/{id}/` y los archivos `videos/{id}.*` (después del commit)
- [x] Todos los investigadores activos ven todos los experimentos (R-07)

## 5. Carga de video (§4.1, R-02, R-03, R-09)
- [x] `POST /experiments/{id}/groups/{grupo}/videos` en `multipart/form-data` (probado hasta con 940 MB: unos 2 min, sin cargar el archivo en memoria)
- [x] Extensión `.mp4` o `.mov` revisada en el servidor
- [x] Reproducible y en horizontal, validado con ffprobe (con la marca de giro), y `422` con mensaje claro si falla
- [x] `409` si la tanda ya tiene un video de esa sesión
- [x] Crea el grupo si es nuevo, con `tipo` y `tratamiento`
- [x] Crea la tanda si es nueva, con `nCilindros` de 2 a 4 y `ordinal`
- [x] Crea los especímenes
- [x] Guarda el archivo en `videos/{idVideo}.{ext}`; un rechazo o una falla no deja archivos ni filas
- [x] Solo el Día 2 crea `ANALISIS` con `estado='en cola'`, `etapa='preprocesamiento'`, `nivelClasif='preciso'` y `fechaAnalisis=ahora` (D-79), y el worker real lo toma solo
- [x] El Día 1 se guarda sin análisis
- [x] Respuesta `201` con `{idVideo, idAnalisis, estadoAnalisis}`
- [x] Nombre original del video (D-114, 20-oct): columna `VIDEO.nombreOriginal` (migración idempotente al arrancar), guardado sin la ruta del cliente; se ve en Resultados, en la Revisión y en el PDF (tabla "Videos analizados"); los videos anteriores dicen "nombre no registrado"
- [x] Eliminar una tanda (D-115, 20-oct): `DELETE /experiments/{id}/tandas/{idTanda}` → `204`; solo el creador o un Administrador (`403`), `409` si su análisis está procesando o en pausa (en cola sí se borra). Una transacción borra segundos, observaciones, análisis, videos, especímenes, la tanda y los reportes del experimento; los archivos se borran después del commit. La letra queda libre. El grupo se conserva si le quedan tandas; si era la última, se borra también (23-oct, decisión de Erick: un grupo nunca queda sin tandas; el grupo se bloquea con `FOR UPDATE` antes que la tanda). Botón "Eliminar tanda" con confirmación en el grupo; si es la única tanda, el diálogo avisa que el grupo también se elimina y la pantalla vuelve al experimento
- [x] D-107 (23-oct): solo el creador sube videos; `403` ("Solo quien creó el experimento sube sus videos.") a cualquier otra cuenta, también a un Administrador. El `404` va primero. Probado con otro Investigador y con otro Administrador: sin filas ni archivos nuevos. Motivo: `VIDEO` no guarda quién lo subió y `subidoPor` es el creador del experimento

## 6. Contrato con el worker (§4.2 a §4.8)
- [ ] El backend nunca escribe `estado`, `etapa` ni `nivelClasif`, salvo al encolar, al reiniciar y en la pausa (§4.7)
- [x] `GET /experiments/{id}/status` con porcentaje de 0, 33, 67 o 100 por etapa. Es la única fuente del progreso: el frontend ya no usa una cola global
- [ ] Pausa. Hecho: diseño aprobado por Erick el 23-oct, siguiendo §4.3 de la guía, en dos encargos. El 31 (fst_auto acepta `--correccion` para medir un cilindro con el recuadro que ajusta el usuario desde el segundo de la pausa) quedó a medias, detenido a pedido de Erick, en la rama `wip/correccion-recuadro` de fst-etiquetador (commit `958eeea`, que explica lo hecho y lo que falta; `main` de fst_auto sigue en `9844e57`, igual que `pipeline/`). El 32 (el worker detecta 3 s seguidos sin ver al espécimen y pausa, estas rutas y la pantalla) no se empezó. Medido el 23-oct en los 15 videos de `fst_auto/etiquetas`: ningún espécimen dejó de verse 3 s seguidos (máximo 1 s, dos veces), así que hace falta un video fabricado para probarlo
  - [ ] `GET .../pause` lee `pausa.json` y calcula `segundosRestantes`
  - [ ] `GET .../pause/image`
  - [ ] `POST .../pause/adjust` escribe `ajuste` en el JSON y cambia `en pausa` a `en cola`, solo si sigue `en pausa` (`409` si no)
  - [ ] `POST .../cancel` cambia a `cancelado` con la misma condición
  - [ ] Ambas solo para quien subió el video o un Administrador
  - [ ] Actualización condicional atómica (`UPDATE ... WHERE estado='en pausa'`)
- [ ] `POST .../restart` (R-04):
  - [ ] Solo desde `completado`
  - [ ] Solo para quien subió el video o un Administrador
  - [ ] En una transacción: borra `OBSERVACION` y `SEGUNDO`, borra los reportes y deja `en cola` con los valores de arranque
  - [ ] `409` en `error`, `en pausa`, `cancelado` o en curso
  - [ ] `409` si el video ya se borró (`VIDEO.archivo` nulo), porque reiniciar vuelve a correr el análisis sobre el video
- [x] `GET .../diagnostic` entrega el PDF de diagnóstico

## 7. Resultados y revisión
- [x] `GET .../results`, con totales y porcentaje por conducta y la bandera `revisado`
- [x] `GET .../results/byminute`: cada espécimen con sus minutos (las cuatro conductas siempre), desde `SEGUNDO`
- [x] `GET .../results/groups`, con promedio (no suma), datos faltantes y aviso si mezcla `preciso` y `agrupado`
- [ ] `GET .../results/blocks`, bloques de 5 s con la mayoritaria y el desempate entre nado y escalamiento por "conducta activa" (R-12)
- [x] Los totales se calculan desde `SEGUNDO`, nunca se guardan (D-52)
- [x] `GET .../review`, con `puedeCorregir`, recuadros desde `cajas.json` y segundos: la revisión carga con el front (probado por Vanesa en el navegador, 20-oct, backend `e66ce40`; `PARA_ERICK.md`, punto 54). Sin probar: `puedeCorregir` en falso con otra cuenta
- [ ] `GET .../video` con soporte de `Range` y token de video (decidido el 15-oct). Hecho: el video con `?vt=` responde `206` con `Range` y un `web.mp4` real (probado por Vanesa en el navegador, 20-oct, backend `e66ce40`); las reglas de abajo siguen sin probar
  - [ ] `POST .../analyses/{idAnalisis}/video-token`: token firmado de solo lectura de ese análisis, con vigencia `VIDEO_TOKEN_MINUTES`; solo para quien subió el video o un Administrador, con el análisis `completado` y el video disponible [PROPUESTA]. Hecho: responde `200` con `{token}` cuando lo pide el front (Vanesa, 20-oct, backend `e66ce40`); sin probar las reglas de quién lo pide y de qué análisis
  - [ ] `GET .../video` acepta `?vt=` solo en esta ruta y solo para ese análisis; el JWT de sesión no se acepta en la URL y el token de video no sirve en otras rutas. Hecho: con un `vt` válido responde `206` (Vanesa, 20-oct, `e66ce40`); sin `vt` o con uno inválido da `401` (probado sin sesión); sin probar que el token de otro análisis o el JWT de sesión en la URL se rechacen
  - [ ] Sin query string en los logs de nginx ni de Spring. Hecho: el `access_log` de nginx ya no guarda `?vt=` y Spring no la registra. Falta: el `error_log` de nginx sí escribe la petición completa, con `?vt=`, cuando falla el backend (hallazgo del 19-oct, pendiente)
  - [ ] Si el video ya se borró, responde que ya no existe (`404`). Sin probar
- [ ] `PUT .../segundos`: Hecho: responde `200` al guardar desde el front (probado por Vanesa en el navegador, 20-oct, backend `e66ce40`); las reglas de abajo están implementadas (encargo 15) y ninguna se ha probado
  - [ ] Solo los segundos que cambiaron. Sin probar
  - [ ] `origen` en `confirmada`, `corregida` o `dudosa`. Sin probar
  - [ ] Solo el que subió el video o un Administrador (`403` a otra cuenta). Sin probar
  - [ ] Solo sobre un análisis `completado`. Sin probar
  - [ ] `409` si el video ya se borró (`VIDEO.archivo` nulo): sin video no se corrige. Sin probar

## 8. Reportes (§7, R-06)
- [ ] CSV con las columnas `Grupo, Tanda, Espécimen, Tiempo, Nado, Escalamiento, Inmovilidad, Conducta activa`
- [ ] Estadísticas por grupo: media, desviación estándar y varianza
- [ ] XLSX (Apache POI)
- [x] PDF de resultados con iText (`GET /experiments/{id}/report/pdf`), con los minutos contados desde `SEGUNDO`
- [ ] Se guardan en `REPORTE`
- [ ] Vigencia: si no existe, se genera; si es más viejo que el último análisis, se regenera y reemplaza; si no, se devuelve el guardado
- [ ] Indican si hubo revisión humana
- [ ] Los reportes se conservan aunque el video se borre

## 9. Notificaciones y correo (§11.2)
- [x] `EmailService` genérico (destinatario, asunto, texto)
- [x] Si el correo falla, no se detiene la operación
- [x] `GET /notifications` y `PATCH /notifications/{id}` para marcar como leída. Cada notificación trae `tipo`, `mensaje`, `leido`, `fechaCreacion` e `idExperimento`; el frontend arma el título y el enlace
- [x] Solo las 6 notificaciones del dominio: las crea solo el worker; el backend no agrega otras
- [ ] Cada notificación se envía también por correo
- [x] Quitar el correo de demostración al crear un experimento, que no está en la guía

## 10. `worker` y tareas programadas
El worker está en `../worker` y es Python, no Spring (servicio `worker` del compose). Revisado punto por punto contra el
código el 23-oct: `cola.py` (la cola, las pausas vencidas y la reconexión), `worker.py` (el análisis con el modelo),
`diagnostico.py` (el PDF de error), `tareas_programadas.py`, `progreso.py`, `tiempos.py` y `vigilancia.py`. Usa el modelo
`fst_cnn_seg.pt` con el código de `fst_auto` copiado sin cambios en `../pipeline`; escribe `OBSERVACION` y `SEGUNDO` (los
últimos 300 s de cada espécimen) con el modelo de 12 tablas, más `web.mp4`, `cajas.json` y la notificación.
Probado con `AcuBN1.MOV` (18-oct): completado, 4 × 300 segundos en `SEGUNDO`, `web.mp4`, `cajas.json`, PDF y
notificación, y el camino de error con un archivo que no es video. Guarda en la base exactamente lo que da el modelo
(100 %), pero el modelo en Docker coincide en 95.75 % de los segundos con el mismo modelo corrido en la PC (la diferencia
nace al medir los fotogramas, antes de la red). Pruebas automáticas de `worker/` (`test_tiempos`, `test_vigilancia` y
`test_diagnostico`): 23 pasan dentro de la imagen del worker (23-oct).
**Lo que NO hace:** (1) la pausa de R-15: el worker no revisa los segundos en que la segmentación de fst_auto no
encontró al espécimen (`cx` vacío en `_cuadros.csv`), así que ningún análisis pasa a `en pausa`, no escribe `pausa.json`
ni `pausa.jpg`, no crea la notificación `analisis en pausa`, no lee ningún `ajuste` y guarda la conducta de la red
también en los segundos «no se ve» (§4.3 pide conducta nula); solo atiende las pausas que ya existan. Está en curso:
ver la casilla «Pausa» de la sección 6. (2) El aviso a 7 días no crea notificación: la guía (§8) dice que no se guarda
nada y que el backend lo calcula (`avisoVencimiento` de `GET /experiments`); el worker solo lo deja en el log. El respaldo
de la base con `pg_dump` (D-18) ya está hecho desde el 23-oct (casilla de abajo).
Hasta el 20-oct hubo además un simulador (`worker_fake`, con conductas inventadas); se retiró ese día y su cola, pausas
y tareas diarias pasaron a `../worker`.
Agregado el 19-oct (versión 0.22.0): **progreso real** (encargo 14: `progreso.json`, `/status` con `porcentajeEtapa`
y el porcentaje general, pesos medidos 1/9/90); **tiempos del análisis** (encargo 16: `tiempos.json` con segundos
por etapa y el equipo, en Progreso, Resultados y el PDF); **`MODO_DESARROLLADOR`** (encargo 17: con `false` se
apagan los tiempos y el banner rojo "BETA vX.Y.Z"). Probado con pruebas automáticas y sin sesión; falta una corrida
real con sesión (la hará el usuario en otra PC).
- [x] Toma un análisis a la vez, el más antiguo primero (`cola.py`: `tomar_siguiente`, `ORDER BY fechaanalisis, idanalisis` con `FOR UPDATE SKIP LOCKED`). Probado en desarrollo (23-oct): con dos análisis `en cola` de fechas del año 2000, `tomar_siguiente` toma el más antiguo aunque el otro tenga id menor; con ese bloqueado sin confirmar por otra conexión, la segunda conexión toma el otro y no el mismo; y dos vueltas reales de la cola (`cola.vuelta`) con un procesamiento simulado de 6 s muestran un solo análisis `procesando` a la vez
- [x] Caso exitoso: pasa por las etapas (`preprocesamiento`, `cilindros`, `clasificacion`), escribe `OBSERVACION` y `SEGUNDO` (últimos 300 s, renumerados) y crea la notificación `analisis completado`. Probado con `AcuBN1.MOV` (18-oct); en el portable hay dos análisis completados (19-oct) con su notificación
- [x] Caso de error (el video no se abre, un paso del pipeline falla o pasa de una hora, no coinciden los cilindros, o un fallo imprevisto, D-88): PDF de diagnóstico, estado `error` y notificación. Probado con el video que no se puede abrir; los demás motivos comparten `marcar_error` y no se provocaron a mano
  - [x] PDF de diagnóstico y estado `error`: probado con un archivo que no es video (18-oct), con `worker/test_diagnostico.py` (6 pruebas, 23-oct) y en desarrollo el 23-oct (video sustituido por basura: `error` en `preprocesamiento`, `diagnostico.pdf` de 80 KB válido, `rutaDiagnostico` guardada, `/status` en `error` y `GET .../diagnostic` con `200` `application/pdf`)
  - [x] Notificación `error del pipeline`: probado en desarrollo (23-oct), una fila para el creador con la etapa y el motivo («No se pudo leer el video con ffprobe…»). El correo no salió porque MailHog no estaba levantado; el aviso queda en la campanita
- [ ] Pausa (R-15): el worker real no la crea (ver arriba). Hecho: solo atiende las pausas que ya existan. En curso desde el 23-oct (encargos 31 y 32, ver la sección 6)
  - [x] Vencimiento a las 72 horas (D-112), `cola.py`: `revisar_pausas`. Probado en desarrollo (23-oct) con `pausa.json` escritos a mano: el de 96 h pasa a `cancelado` con la notificación `analisis cancelado`; el que no tiene `pausa.json` también se cancela (solo un aviso en el log, sin notificación); el de 96 h con `ajuste` y el de 1 h siguen `en pausa`
  - [ ] Ajuste y cancelación del usuario: no existen. El backend no tiene `/pause/adjust` ni `/cancel` y el worker no lee `ajuste`
- [x] Al arrancar, reencola los `procesando` huérfanos (D-84), `cola.py`: `reencolar_huerfanos`. Probado en desarrollo (23-oct), llamándola como lo hace `main()` al conectarse: los análisis `procesando` vuelven a `en cola` en la etapa `preprocesamiento` y conservan su `fechaAnalisis`
- [x] Reemplaza `fechaAnalisis` por `ahora` al terminar (`completar_real` y `marcar_error`). Probado en el portable (19-oct): los análisis 1 y 3, completados, tienen `fechaAnalisis` 4 min 57 s y 4 h 21 min después de subir el video; el análisis cancelado (el 2) conserva la hora de entrada
- [x] Tareas diarias en un módulo aparte, disparables a mano: `tareas_programadas.py` (`python tareas_programadas.py`, o `--disco` / `--respaldo` para una sola). Corrieron completas en desarrollo (23-oct) y en el portable
  - [x] Borrar videos vencidos a los 30 días (R-05), `borrar_videos_vencidos`. Probado en desarrollo (23-oct) con `python tareas_programadas.py`: un análisis completado con `fechaAnalisis` de hace 31 días pierde `videos/{id}.mp4` y `web.mp4`, `VIDEO.archivo` queda en nulo y sus 5 `SEGUNDO` se conservan. No crea notificación (la de `video borrado` solo sale en la limpieza por disco)
  - [x] Borrar `pausa.json` y `pausa.jpg` huérfanos (D-89, regla c), `borrar_pausas_huerfanas`. Probado en desarrollo (23-oct): se borran los de un análisis cancelado y uno completado; se conservan los de dos análisis `en pausa`
  - [x] Aviso a 7 días: resuelto (D-45). La guía §8 dice que no se guarda nada y que el panel calcula el aviso con la misma regla, así que no hay notificación. Lo calcula el backend en `avisoVencimiento` de `GET /experiments` (`ExperimentoServiceImpl`): solo análisis `completado` con el video todavía disponible (`archivo` no nulo) y días restantes = 30 − días desde `fechaAnalisis` (con `FLOOR`) ≤ 7, que es lo mismo que `fechaAnalisis + 30 días ≤ ahora + 7 días`; trae `fechaBorrado`. Verificado en el código (23-oct), sin prueba con datos de ese rango. El worker solo lo deja en el log
  - [x] Vigilar el disco: alerta al 80 %, borrado al 90 % (`vigilar_disco`; también corre antes de cada análisis, D-86). Probado en desarrollo (23-oct) con `DISK_SIMULATED_PERCENT`: al 85 %, una sola `alerta de disco` al administrador y la segunda corrida del mismo día no la repite; al 95 %, `liberar_disco` borra un video por corrida del más antiguo al más nuevo (49 y luego 50), salta el video más antiguo de todos porque su análisis estaba `en cola` y avisa al creador con `video borrado`. Con el porcentaje real (4.1 %) no hace nada
  - [x] Respaldar la base con `pg_dump` cada día, conservar los últimos 7 y borrar los más viejos (§8, D-18, agregado a la guía el 20-oct). Respalda la base, no los videos. Hecho el 23-oct: `respaldar_base()` en `tareas_programadas.py` (primera de las tareas diarias, o `--respaldo` sola), `pg_dump -Fc` 17.11 de la imagen del worker contra el servidor 16.15, con la contraseña solo en `PGPASSWORD` del subproceso; archivo `fst-AAAAMMDD.dump` en `respaldos/` (carpeta del equipo fuera de los volúmenes, bind mount en los dos compose; `.gitignore`), escrito a un temporal y renombrado al final. Probado en desarrollo: crea el archivo y `pg_restore --list` lo lee sin errores; con 8 respaldos falsos viejos y un `.tmp` quedan 7 y el `.tmp` se borra; el de hoy se reemplaza; con contraseña equivocada o sin `pg_dump` solo se registra el error (código 1, sin tocar los respaldos buenos) y las demás tareas siguen; ni la contraseña real ni una falsa aparecen en el log; 5 pruebas automáticas (`test_respaldo.py`). Restauración probada en una base temporal: mismos conteos, restricciones, índices y dominios, también una segunda vez encima (`--clean --if-exists`); hay que usar el `pg_restore` 17 de la imagen del worker, porque el 16 del contenedor de la base no lee archivos de `pg_dump` 17, y termina con un aviso benigno por `SET transaction_timeout`. Cómo restaurar: `portable/LEEME.md`
- [x] `ultima_corrida_tareas.txt`: se escribe al terminar las tareas diarias y evita repetirlas el mismo día. Probado en el portable (23-oct): `worker/ultima_corrida_tareas.txt` en el volumen, con la fecha de ese día

## 11. Criterios de aceptación finales (§12)
- [ ] `docker compose up` arranca en una máquina limpia, y en una segunda máquina. Hecho: el portable se probó desde el zip el 18-oct y Erick lo usa en otra PC; falta probar el compose definitivo de la raíz
- [ ] Un investigador recibe `403` en `/admin/*`
- [ ] No se puede quitar al último Administrador
- [x] Un Día 1 se guarda sin análisis
- [ ] Con dos análisis en cola, el worker los procesa uno a la vez. Hecho: probado en desarrollo (23-oct) con dos vueltas reales de la cola (`cola.vuelta`) y un procesamiento simulado de 6 s: el más antiguo primero, un solo `procesando` a la vez y el segundo espera. Falta repetirlo con dos videos reales y el modelo (varios minutos y unos 3.5 GB de memoria)
- [ ] Un reporte se regenera si hay un análisis más nuevo
- [ ] Cuatro sesiones simultáneas responden en 3 s o menos
- [ ] El resumen final anota los puntos [ABIERTO] que se resolvieron con el valor por defecto

## 12. Decisiones por tomar con el frontend (18-oct-2026)
Vanesa conectó el frontend a la guía de la API (`PARA_ERICK.md`, puntos 41 a 52, rama `feat/frontend-v2-servicios`).
Lo que sigue son formas que la guía no define y que ella propone. Cada casilla se marca cuando se decide y el
endpoint correspondiente queda hecho. Sin respuesta, el frontend usa su propuesta y no se rompe.
- [x] `PATCH /auth/password` y `PATCH /users/me`: cuerpo `{contrasenaActual, contrasenaNueva}` y `{nombre, apellidos, correo}`; ambos devuelven la cuenta como el login, sin `token`
- [x] `POST /experiments` responde `201` con `{idExperimento}`
- [x] `GET /experiments`: campos extra `nEspecimenes`, `tratamientos`, `videosDia2: {analizados, cargados, total}`, `diasRetencion` (`null` sin videos o ya borrados) y `videosBorrados`
- [x] `GET /experiments/{id}/status`: incluye todos los análisis vigentes, también los `completado`
- [x] `/status`: `fechaAnalisis` es la hora de entrada a la cola mientras el análisis no termina (D-79); el frontend ordena la cola con ella
- [x] `/status`: campo extra `motivoCancelacion` (`usuario` o `plazo`). Decidido (D-112): no se guarda; el backend manda `null` y el frontend dice «El análisis se canceló.» (commit `3d9ccec` de Vanesa)
- [ ] `/status`: campo extra `pausa.desdeSegundo`. Hecho: el backend lo lee de `pausa.json` (`ResultadosServiceImpl.pausa`); no se ha visto con un análisis real porque el worker todavía no crea pausas (ver la casilla «Pausa» de la sección 6)
- [x] `GET /experiments/{id}`: cada video trae `fecha`; `/status` trae `fechaAnalisis` por análisis
- [ ] `GET /admin/system`: forma `{disco, conductas, cola: [{idAnalisis, idExperimento, experimento, grupo, letra, sesion, estado}]}`, en orden de llegada
- [x] `GET /experiments/{id}/groups?q=`: forma `[{idGrupo, etiqueta, tipo, tratamiento, siguienteOrdinal}]`
- [x] `{grupo}` en la URL de subida es la etiqueta del grupo, codificada (ver `services/groups.js` del frontend)
- [x] `/results`: cada espécimen trae además `idGrupo`
- [x] `/results/groups`: forma `[{idGrupo, etiqueta, tipo, tratamiento, n, nTotal, faltantes, nivelMixto, promedios, porNivel}]`
- [ ] `/results/blocks`: cada espécimen con `bloques: [{inicio, conducta, matiz, segundos}]`
- [x] `GET .../review`: agregar `duracion`, `ancho` y `alto` (sin ellos el frontend asume 300 s y 1920 × 1080): los trae (`duracion` = segundos guardados; `ancho` y `alto` de `cajas.json`) y la revisión carga con el front (probado por Vanesa en el navegador, 20-oct, backend `e66ce40`)
- [x] Notificaciones: el id se llama `idNotificacion`; «marcar todas» se hace con un `PATCH` por notificación, sin `POST /notifications/read-all`
- [x] `POST .../video-token` responde `{token}` y el video con `?vt=` admite `Range` con un `web.mp4` real: `200` con el token y `206` con `Range` (probado por Vanesa en el navegador, 20-oct, backend `e66ce40`)
