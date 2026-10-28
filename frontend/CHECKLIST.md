# Checklist del frontend FST en React

Sale de `INSTRUCCIONES-WEB.md` (guía del proyecto TT 2026-B066): la sección 9 (pantallas), la 6 (API y contrato de
§6.1), el paso 10 de la sección 12 y los criterios de aceptación. Entre paréntesis va la sección o regla de origen.

No cubre el backend, el worker ni el SQL y Docker de los anexos (ver `backend-spring/CHECKLIST.md`).

**Cómo leerlo:** `[x]` = hecho y visto en el código. `[ ]` = pendiente. Si una casilla vacía trae una nota "Hecho:",
ya existe una parte y la nota dice qué falta. Una casilla de pantalla marcada `[x]` significa que **la pantalla lo hace
con datos de ejemplo**; que funcione contra el backend real se cuenta aparte, en las secciones 1 y 13.

**Revisión del 23-oct-2026 (rama `poc/backend-spring`, versión 0.80.0):** se agregó la casilla de eliminar una tanda con la regla nueva del grupo (§5), y se actualizó el empaquetado (§0): el compose definitivo está en la raíz y ya no existen `docker-compose.prod.yml` ni el servicio de desarrollo en el 5173. El cambio del frontend de Vanesa que falta traer a esta rama (D-107 en la pantalla, commit `6067e4c`) no está contado aquí.

**Última revisión del estado (17-oct-2026, Vanesa):** se conectaron los servicios a las formas y rutas de la guía (pasos 1 a 10 de `PARA_VANESA_API.md`, puntos 41 a 51 de `PARA_ERICK.md`). Pruebas, lint y build pasan. Lo que queda abierto depende del backend real, de una confirmación de Erick o de pruebas con personas. Revisión anterior (15-oct-2026):
los tres commits de Vanesa del mismo día, que cerraron lo que era solo del frontend). Leí todo `frontend/src` y el diff
posterior. Todo corre con datos de ejemplo (`VITE_USE_MOCKS` es verdadero salvo que se ponga en `false`) y
**nada se ha probado contra el backend real**. Las pruebas, el lint y el build **no se ejecutaron en esta revisión**:
`vitest` y `eslint` no están instalados en el `node_modules` de esta carpeta (hace falta `npm install`).

## 0. Base y empaquetado
- [x] React 18, Vite 8, React Router 7 y axios; cada pantalla se descarga al abrirla con `React.lazy` (§2)
- [ ] `frontend/Dockerfile` de desarrollo y servicio `frontend` en `docker-compose.yml`, puerto 5173 (§2). Hecho: `frontend/Dockerfile` existe; desde el 23-oct el `docker-compose.yml` de la raíz es el definitivo y levanta la versión de producción (servicio `web`), así que ya no hay servicio de desarrollo en el 5173: en desarrollo se usa `npm run dev`
- [x] `Dockerfile.prod`, nginx con las rutas de la app y `/api/` hacia el backend. Desde el 23-oct es el servicio `web` del `docker-compose.yml` definitivo de la raíz (solo él publica puerto, el 8088; se levanta con `portable\iniciar.bat`); `docker-compose.prod.yml` se borró junto con el backend de Flask
- [x] Fuente Archivo incluida en la app: no depende de internet al usarla
- [x] Solo escritorio, sin versión móvil (§2)
- [x] `client_max_body_size` de nginx en 2048 MB, igual que `MAX_UPLOAD_MB` (§11.5)
- [x] `VITE_USE_MOCKS`, `VITE_API_BASE` y `VITE_MAX_UPLOAD_MB` documentadas en `frontend/.env.example`
- [x] Franja «Modo demo» solo con datos de ejemplo; con el backend real no hay franja y la versión se ve en el menú de usuario
- [ ] Chrome, Firefox y Edge en sus versiones actuales probados (§2)
- [x] Abrir el dashboard, filtrar y abrir resultados en 3 s o menos en el 95 % de los casos, con el backend real (§2). Medido el 20-oct en local: la pantalla completa tarda 0.25 s (lista) y 0.25–0.31 s (resultados) con el servidor de desarrollo; las peticiones, p95 de 0.05 s en 40 aperturas. Con pocos datos (3 experimentos): conviene repetirlo con el volumen real

## 1. Capa de servicios y conexión con el backend real (§6, §6.1)
Las pantallas no importan datos: piden todo a `src/services/`. Hoy esa capa responde con datos de ejemplo.
- [x] Toda llamada pasa por `src/services/`, con el interruptor `USE_MOCKS` (`VITE_USE_MOCKS=false` llama a la API)
- [x] Token JWT en `localStorage`, enviado como `Authorization: Bearer` (§11.1)
- [x] Un 401 con sesión lleva al login con el aviso «Tu sesión caducó»; el 401 del propio login muestra su error sin redirigir
- [x] Un 403 lleva a Experimentos con el aviso «No tienes permiso para esa sección»
- [x] Rutas que ya coinciden con §6: `/auth/login`, `/auth/reset`, `/experiments` (GET, POST y DELETE), `/pause`, `/pause/image`, `/pause/adjust`, `/cancel`, `/diagnostic`, `/review`, `/segundos`, `/restart`, `/report/{formato}`, `/admin/users`, `/admin/system` y `/notifications`
- [x] Desenvolver la respuesta `{data, error, status}` (§6) en el interceptor de `services/api.js`; los archivos no se tocan
- [x] Leer el error solo del campo `error` del envoltorio, que es el formato acordado (§6)
- [x] Equivalencias guía ↔ pantallas en `src/lib/contrato.js`, en los dos sentidos
- [x] Rutas alineadas con §6 y §6.1. Hecho: las de arriba y la siguiente
  - [x] Recuperar contraseña: `POST /auth/recover` con `{correo}`
  - [x] Cambiar contraseña: `PATCH /auth/password` con `{contrasenaActual, contrasenaNueva}` (nombres por confirmar)
  - [x] Perfil: `PATCH /users/me` con `{nombre, apellidos, correo}`
  - [x] Login con `{correo, contrasena}` y respuesta `{token, rol, nombre, cambioRequerido}` más `idInstitucional`, `apellidos` y `correo`; `idInstitucional` es el id de la cuenta
  - [x] Subir video con `POST /experiments/{id}/groups/{grupo}/videos` y los campos `sesion`, `tipo`, `tratamiento`, `nCilindros` y `ordinal`; el grupo y la tanda se crean en la misma llamada
  - [x] Respuesta de la subida `{idVideo, idAnalisis, estadoAnalisis}`
  - [x] Pasar la letra de la tanda al `ordinal` entero de la base (`lib/contrato.js`)
  - [x] Detalle de grupo tomado del árbol de `GET /experiments/{id}`; el estado de cada tanda sale de `GET /experiments/{id}/status`
  - [x] Sugerir grupos y la siguiente tanda con `GET /experiments/{id}/groups?q=` (forma de la respuesta propuesta)
  - [x] Resultados con `/results`, `/results/byminute`, `/results/groups` y `/results/blocks`; el filtro por grupo o tanda, en el navegador (D-93)
  - [x] Progreso con `GET /experiments/{id}/status` (`/analisis?experimento=`; sin experimento junta los activos); Administración toma la cola de `/admin/system`. Ya no se usa `/queue`
  - [x] `POST /admin/users/:id/reset-temporal`: ruta oficial, aceptada por Erick el 28-oct (la guía no la trae; encargo 44). Responde `{passwordTemporal}` con 403 (no es Administrador), 404 (no existe) y 409 (cuenta desactivada o la propia cuenta). En «Editar cuenta», una cuenta desactivada no trae el botón sino «Reactiva la cuenta para darle una contraseña temporal nueva.», y la propia cuenta no se edita ahí (va a Mi perfil); el modo demo da los mismos 404 y 409. Probado con pruebas del servicio y de la pantalla, y en el navegador contra el backend de Spring (base desechable): la temporal nueva entra y lleva a `/primer-acceso`, y con la cuenta desactivada ya no sale el botón. (`POST /notifications/read-all` ya no se usa: un `PATCH` por notificación)
- [x] Nombres de campo y valores de §6.1 en todo lo que llega del backend (cuentas, experimentos, detalle, `/status`, subida, resultados, notificaciones, pausa y revisión). Las pantallas siguen con `QUEUED`, `DONE`… y la traducción está en `services/` con `lib/contrato.js`
- [x] Notificaciones con los campos de la guía (`idNotificacion`, `tipo`, `mensaje`, `leido`, `fechaCreacion`, `idExperimento`); el título y el enlace los arma el frontend, y la pausa abre el progreso de su experimento
- [ ] Probar cada pantalla con `VITE_USE_MOCKS=false` contra el backend de Spring y corregir lo que falle. Hecho (20-oct, backend `e66ce40` con el simulador del worker, retirado el 20-oct): inicio de sesión, primer acceso, perfil, lista, crear, detalle, grupo, cargar video, progreso, resultados, comparación, desglose por minuto, PDF, notificaciones y revisión segundo a segundo (cargar, video con token y guardar correcciones). Falta: probar en el navegador lo que el backend agregó después (administración, recuperar contraseña, pausa y bloques hasta la v0.86.1; CSV y XLSX en la v0.87.0, encargo 39) y probar error y pausa (el simulador, retirado el 20-oct, no leía el nombre original del video)

## 2. Acceso y cuenta (§9.1, §11.1)
- [x] Inicio de sesión con correo y contraseña, y error de credenciales (R-01)
- [x] Enlace «Olvidé mi contraseña» y pantalla de recuperar, que no revela si la cuenta existe
- [x] Pantalla `/restablecer?token=` con el caso de enlace vencido o ya usado, y la vigencia de 60 minutos en el texto
- [x] Primer acceso: con `cambioRequerido` el router no deja entrar a ninguna otra pantalla hasta cambiar la contraseña
- [x] Cerrar sesión desde cualquier pantalla con barra superior, sin llamar al servidor (la guía no pide llamarlo)
- [x] La sesión sobrevive a cerrar la pestaña y «Cerrar sesión» la borra
- [x] Sin sesión se va al login; con sesión, el login no se vuelve a mostrar
- [x] Validación de formularios en el cliente: correo con forma válida y contraseña nueva de 8 caracteres o más
- [x] Mensaje genérico en el login con el backend real: un correo que existe con contraseña mala y uno que no existe dan el mismo 401 «Correo o contraseña incorrectos» (R-01)
- [ ] Acordar la ruta del enlace de recuperación (`/restablecer?token=`) con quien arme el correo

## 3. Dashboard de experimentos (§9.2)
- [x] Tabla con nombre, fecha, grupos, estado y acciones, más especímenes, Día 2 analizados, responsable y retención
- [x] Los ocho estados calculados: sin videos, en espera del Día 2, en cola, procesando, en pausa, completado, con error y cancelado (§4.8)
- [x] Filtros por estado, fecha (año) y tratamiento, más una búsqueda por nombre
- [x] Aviso de video por vencer en 7 días o menos, con la fecha exacta (R-05)
- [x] Estado vacío «Aún no hay experimentos» con el botón de crear
- [x] Paginación de 10 en 10
- [x] «Eliminar» con confirmación explícita, solo para quien creó el experimento o un Administrador (R-08, D-63)
- [x] Los errores 403 y 409 de eliminar se muestran en el diálogo
- [x] Todos los investigadores activos ven todos los experimentos (R-07)
- [x] Filtros y paginación en el servidor. Hecho (encargo 43): la lista pide UNA página de 20 a `GET /experiments?pagina=&estado=&fecha=&tratamiento=&q=` y el servidor filtra, busca (sin acentos) y pagina; el «10 por página» de antes pasó a 20, como el servidor. `listExperiments({estado, anio, tratamiento, q, pagina})` devuelve `{pagina, total, porPagina, items, resumen}`; los conteos por estado, los años, los tratamientos, los avisos de vencimiento y el estado vacío («Aún no hay experimentos» / «Ningún experimento coincide») salen de `resumen`, que es de todos los experimentos. La búsqueda espera 300 ms tras la última tecla, un filtro nuevo vuelve a la página 1 y mientras llega una página se sigue viendo la anterior. El modo demo hace lo mismo en memoria. Quien necesite todos los experimentos (Análisis en curso) usa `listAllExperiments()`, que junta las páginas
- [x] La columna «Retención» dice «Videos borrados» y explica el motivo al pasar el mouse

## 4. Crear experimento y cargar video (§9.3, R-02, R-03, R-09)
- [x] Paso 1: nombre único, fecha y notas opcionales, con el 409 de nombre repetido marcado en el campo
- [x] Barra de pasos y un campo con error por cada dato obligatorio vacío
- [x] Paso 2: grupo con autocompletado y teclado, solo de este experimento
- [x] Tanda sugerida (la siguiente letra), editable, y confirmación explícita «¿Esta es la tanda…?» antes de poder guardar
- [x] Grupo nuevo: pide tipo (control, referencia o tratamiento experimental) y tratamiento, y su primera tanda es la A
- [x] Especímenes por tanda, de 2 a 4
- [x] Sesión Día 1 o Día 2, con el texto de que solo el Día 2 se analiza
- [x] Extensión `.mp4` o `.mov` revisada en el navegador, con mensaje específico, antes de subir (R-09)
- [x] Tamaño revisado antes de subir contra `MAX_UPLOAD_MB`, y el 413 del servidor también se muestra
- [x] Rechazos del servidor mostrados: 422 (vertical o no reproducible) y 409 (la tanda ya tiene video de esa sesión)
- [x] Aviso y bloqueo del botón cuando la tanda ya tiene el video de esa sesión
- [x] Barra de progreso de la subida y botón para cancelarla
- [x] Día 2: redirige a Progreso con el aviso de que el análisis está en cola. Día 1: vuelve al experimento con el aviso de que no se analiza
- [x] Botones «+ Agregar otra tanda» y «Terminar» en el paso 2 (§9.3)
- [x] Confirmación del paso 3 (§9.3): Día 2 lleva a Progreso de ese experimento con «Video almacenado. El análisis está en cola»; Día 1 vuelve al experimento con «Video del Día 1 almacenado. No se analiza»

## 5. Detalle de experimento y de grupo (§9.2, maquetas `experimento` y `grupo`)
- [x] Detalle: datos generales, notas, grupos con sus tandas y el estado de cada una, barra de avance y leyenda
- [x] «Cargar videos pendientes» y «Ver resultados» (desactivado si ninguna tanda tiene el Día 2 analizado)
- [x] Eliminar el experimento desde su detalle, con la misma confirmación
- [x] Se actualiza solo: cada 5 s si hay análisis activos y cada 30 s si no
- [x] Grupo: especímenes de cada tanda, video del Día 1 (guardado, no se analiza) y del Día 2 con su estado
- [x] Enlaces de cada tanda según su estado: revisar, resultados, ajustar o cancelar, cargar el Día 2 o ver el progreso
- [x] «Ver resultados del grupo completo» y «Ver resultados de esta tanda» (RF-19, D-93)
- [x] Una tanda con solo el Día 1 se muestra «En espera del Día 2» (D-83)
- [x] Indicar que el video ya se borró (§11.13): Grupo lo avisa y deja «Revisar segundo a segundo» en gris con «Video no disponible», igual que Resultados
- [x] Eliminar una tanda (D-115) desde el grupo, con confirmación, solo para quien creó el experimento o un Administrador. Si es la única tanda del grupo, el diálogo avisa «Es la única tanda de este grupo: el grupo también se eliminará.» y, al eliminarla, la pantalla vuelve al experimento (23-oct, un grupo nunca queda sin tandas; `eliminarTanda.test.jsx`, 157 pruebas pasan)

## 6. Progreso y pausa (§9.4, R-04, R-15)
- [x] Trabajo en curso con sus tres etapas (preprocesamiento, cilindros y clasificación) y el porcentaje 33, 67 o 100
- [x] Cola con el orden de llegada, un trabajo a la vez
- [x] Se actualiza sola: cada 5 s con análisis activos y cada 30 s sin ninguno
- [x] Error: el mensaje se arma con la etapa, y el botón del PDF de diagnóstico muestra «Preparando…» y reintenta cada 4 s
- [x] Cancelado: dice si fue por el usuario o por pasar 72 horas sin respuesta (D-112)
- [x] Sin botón de cancelar ni de reiniciar en la pantalla de progreso (R-04)
- [x] «Completados recientemente» con «Ver resultados» y, para quien subió el video o un Administrador, «Revisar segundo a segundo»
- [x] Sin log de ejecución (§11.10)
- [x] Pantalla de pausa: imagen fija, recuadro que se arrastra, se redimensiona o se mueve con el teclado, y campos en píxeles de la imagen
- [x] Cuenta regresiva de 72 horas (en horas y minutos, D-112), que se vuelve a pedir al servidor cada 5 s
- [x] «Ajustar y continuar» y «Cancelar análisis» (con confirmación), solo para quien subió el video o un Administrador
- [x] 409 («ya no está en pausa») y 403 mostrados con su mensaje
- [x] Progreso por experimento (`/analisis?experimento=`), con una barra por video y «Experimento · Grupo · Tanda» y el día de cada uno; sin experimento, junta los activos
- [ ] Descargar el PDF de diagnóstico real. Hecho: el botón y el reintento. Falta: sin backend solo muestra un mensaje
- [ ] Mostrar la imagen de la pausa real. Hecho: la pantalla la pide como archivo. Falta: sin backend se ve un aviso

## 7. Resultados (§9.5, R-12, R-13)
- [x] Selector «Ver»: una tanda o el grupo completo, con cada espécimen rotulado con su tanda (D-55, D-93)
- [x] Tabla por espécimen con las cuatro conductas y «conducta activa» como cuarta columna, con su nota (R-12)
- [x] Media, desviación estándar y varianza de lo que se ve (RF-31)
- [x] Comparación entre grupos con selector de conducta, media ± desviación, especímenes pendientes señalados y aviso si el grupo mezcla niveles
- [x] Bloques de 5 s (D-53): una fila por espécimen, bloques rayados si no se vio bien y los cinco segundos al abrir uno
- [x] Nivel «preciso» o «agrupado», «Con revisión humana» y el aviso de que la conducta del sistema no es exacta (D-78)
- [x] Botones PDF, CSV y XLSX que piden el reporte al servidor, con el grupo o la tanda como alcance
- [x] Aviso «Todavía no hay resultados» cuando no hay tandas completadas
- [x] Porcentaje de cada conducta junto a su tiempo (§9.5)
- [x] Desglose por minuto de cualquier espécimen, con selector y tabla de segundos por minuto
- [x] Descargas reales de PDF, CSV y XLSX. Hecho: el PDF de resultados (el backend responde `application/pdf`). El backend ya responde CSV y XLSX (encargo 39, v0.87.0: `GET /experiments/{id}/report/{csv|xlsx}?grupo=&tanda=`, probado con JUnit). Probado en Chrome el 27-oct (nginx de la v0.88.0, puerto 8099, inicio de sesión real): en Resultados · Tanda A, los botones CSV y XLSX bajan `reporte_1_tandaA.csv` (660 bytes) y `reporte_1_tandaA.xlsx` (4286 bytes); el nombre lo pone el frontend (el `Content-Disposition` del servidor trae otro: `reporte_experimento_1_grupo-1_tanda-A.csv`). Los dos archivos se abrieron antes en Excel 16 por COM; el PDF no se repitió en el navegador esta vez
- [ ] Bloques de 5 s reales. Hecho: la pantalla. Falta: sin backend explica qué mostrará

## 8. Administración (§9.6, R-16)
- [x] Lista de cuentas con filtros de rol y estado, y la columna de experimentos
- [x] Modal de crear cuenta: identificador institucional, nombre, apellidos y correo; se crea como Investigador
- [x] La contraseña temporal se muestra una sola vez, con botón de copiar, y el diálogo no se cierra por accidente
- [x] Modal de editar cuenta, con el rol de Investigador o Administrador
- [x] Desactivar y reactivar cuentas (nunca borrar)
- [x] El último Administrador activo no se puede desactivar ni quitarle el rol, y el 409 se muestra (R-16)
- [x] Almacenamiento con alerta al pasar del 80 % y el texto del borrado al 90 %
- [x] Cola de trabajos con el tiempo estimado para vaciarla
- [x] Acceso a la lista de todos los experimentos
- [x] Un usuario que no es Administrador no entra a `/admin` (el router lo manda a Experimentos)
- [x] Restablecer la contraseña temporal de una cuenta (propuesta del frontend)
- [ ] El backend responde 403 en `/admin/*` aunque se conozca la URL. Hecho: el frontend oculta y redirige. Falta: depende del backend (§6)

## 9. Revisión segundo a segundo (§9.7, R-14)
- [x] Solo se abre con el análisis `completado`, y muestra una pantalla propia en 403 y 409
- [x] Tema oscuro, un espécimen a la vez y el recuadro del tubo destacado con `cajas.json`
- [x] Botones y teclas: 1 nado, 2 inmovilidad, 3 escalamiento, 4 activa, 0 dudosa
- [x] Espacio, flechas (Shift para 5 s), R, Tab, Ctrl+Z, Ctrl+S, velocidades de 0.5× a 2× y clic en la línea para ir a ese segundo
- [x] Lupa de 40 s y línea de 300 s por espécimen
- [x] Con el video corriendo la tecla cambia desde 0.4 s antes hasta el siguiente cambio de color; en pausa, solo ese segundo
- [x] «Visto» a 2× o menos, y solo lo visto se guarda como `confirmada`, `corregida` o `dudosa` (D-80)
- [x] Borrador en el navegador, resumen antes de guardar y «Guardar correcciones»
- [x] «Reiniciar el análisis» con confirmación, solo en esta pantalla (R-04, D-60)
- [x] Reproducir el video real con un token de video (la URL lleva `?vt=`, nunca el JWT de sesión). Probado el 20-oct con el backend `e66ce40`
  - [x] Pedir `POST .../video-token` con el JWT por header antes de abrir la revisión y poner `?vt=` en el `<video>` [PROPUESTA]
  - [x] Si el video responde 401 o 403 por token vencido, pedir otro y seguir en el mismo segundo
  - [x] No escribir la URL del video con su token en consola ni en pantalla
  - [x] Probar `Range` (adelantar y retroceder): el video con `?vt=` responde `206 Partial Content`
- [x] Borrador guardado por cuenta, y «Cerrar sesión» borra los de la revisión
- [x] Sin video (ya se borró) la revisión es de solo lectura: explica el motivo y quita «Guardar correcciones» y «Reiniciar el análisis»
- [x] Con `puedeCorregir: false` la revisión es de solo lectura
- [ ] El backend responde 409 en `PUT .../segundos` y `POST .../restart` si el video ya se borró (`VIDEO.archivo` nulo). Hecho: el modo demo ya lo hace y la pantalla ya se comporta así. Falta: es una propuesta de Vanesa, sin visto bueno en la guía

## 10. Notificaciones, perfil y barra superior (§9, §11.2)
- [x] Barra superior con el nombre del sistema, campana y menú de usuario con «Cerrar sesión» (§9)
- [x] Campana con las notificaciones, que se consultan cada 30 s, con marcar como leída o no leída y marcar todas
- [x] Los seis tipos, cada uno con su enlace: análisis completado, error, pausa, cancelado, alerta de disco (solo Administradores) y video borrado
- [x] Mi perfil: nombre, apellidos y correo (con el 409 de correo repetido), identificador de solo lectura y cambio de contraseña
- [ ] Notificaciones reales con el correo de copia (§11.2). Hecho: la campana. Falta: el correo lo envía el backend

## 11. Textos y reglas de la interfaz (§9.8)
- [x] Se dice «espécimen» y no aparecen «rata» ni «animal» en ningún archivo
- [x] No aparecen «inteligencia artificial» ni «IA»
- [x] Conductas con su nombre: «Nado activo», «Inmovilidad», «Escalamiento» y «Conducta activa»
- [x] Todo el texto en español, y el nombre de la institución en el login
- [x] Sin códigos internos en pantalla (RF-18, RF-31, T-06)
- [x] Sin jerga técnica en pantalla («pipeline», «worker», «backend»): dice «el sistema» y «modo demo» (§9)

## 12. Calidad y accesibilidad
- [x] Pruebas con Vitest en 5 archivos: servicios, pantallas, utilidades, el hook `useAsync` y accesibilidad (92 pruebas por conteo de `it(`)
- [x] Revisión con axe-core sobre las 12 pantallas, y diálogos que atrapan el foco
- [x] `ErrorBoundary` que evita la pantalla en blanco
- [x] GitHub Actions en `.github/workflows/frontend.yml` con lint, pruebas y build
- [x] Pruebas (110), lint y build pasando en esta rama, también en el CI de GitHub (`.github/workflows/frontend.yml`)
- [x] Pruebas de pantalla para Progreso, Pausa, Revisión y Administración (`src/pantallas.test.jsx`), y revisión de accesibilidad con axe-core en 12 pantallas (`src/accesibilidad.test.jsx`)

## 13. Criterios de aceptación finales (§12)
En cada una, "Hecho" es lo que la pantalla ya hace con datos de ejemplo; falta verlo de punta a punta.
- [ ] Un administrador crea una cuenta y esa cuenta debe cambiar la contraseña al entrar. Hecho: ambas pantallas
- [x] Subir un `.mp4` o `.mov` en horizontal crea un análisis `en cola`, el worker lo lleva a `completado` (entonces con el simulador, retirado el 20-oct) y Progreso lo refleja sin recargar (probado con el backend real)
- [x] Un archivo que no es `.mp4` ni `.mov` se rechaza en el cliente; uno vertical o no reproducible lo rechaza el servidor con 422 y su mensaje (probado con el backend real)
- [ ] Un Administrador no puede desactivarse ni quitarse el rol si es el último. Hecho: el aviso y el bloqueo
- [ ] Un investigador que no es Administrador recibe 403 en `/admin/*`. Hecho: la redirección
- [x] «Cancelar análisis» solo existe en la pausa y «Reiniciar» solo en la revisión de un análisis `completado` (lo cubren las pruebas de pantalla de Pausa y Revisión)
- [x] Un video del Día 1 se guarda sin análisis y el experimento muestra «En espera del Día 2»; un segundo Día 1 en la misma tanda da 409 (probado con el backend real)
- [ ] Una pausa se ajusta (vuelve a la cola), se cancela o vence a las 72 horas (D-112), y ajustar o cancelar fuera de la pausa muestra el 409. Hecho: la pantalla
- [ ] Eliminar un experimento con un análisis `procesando` o `en pausa` muestra el 409. Hecho: el mensaje
- [x] 4 sesiones simultáneas con un análisis en cola responden en 3 s o menos. Medido el 20-oct en local: 80 peticiones de 4 sesiones a la vez con un análisis procesando, p95 de 0.03 s y máximo de 0.07 s
- [ ] Una persona sin conocimientos de programación crea un experimento, sube un video, espera y descarga el reporte sin ayuda: 9 de cada 10 (§9)
