# Para Vanesa (y su Claude): conectar el frontend con el backend de Spring

Autor: Erick · Fecha: 15 oct 2026 · Rama: `feat/frontend-v2-servicios`

Vi tus tres commits de hoy (`af06ce5`, `d30dc69`, `a2f2235`): con eso queda cerrado todo lo que era solo del frontend. Lo que falta es **conectarlo con el backend**. Esta nota dice cómo, en qué orden y qué decidimos. Para leerla junto a `frontend/CHECKLIST.md` (secciones 1 y 9) y `PARA_ERICK.md`.

---

## 1. Dónde estamos

- El backend es **Spring Boot** (rama `poc/backend-spring`, carpeta `backend-spring/`). Ya no es Flask: donde `PARA_ERICK.md` y la guía dicen Flask, es Spring.
- **Hoy casi nada está listo.** Tiene `/health`, el CORS de `localhost:5173`, y de experimentos: crear, listar, actualizar y borrar (sin hijos), más el PDF de resultados. El resto se hará siguiendo el orden de la guía. Su avance está en `backend-spring/CHECKLIST.md`.
- Por eso **no puedes probar casi nada contra el backend todavía**. Lo útil ahora es dejar el frontend listo para que, cuando cada ruta exista, baste con encender el interruptor de esa parte.

## 2. Quién manda: la guía

El contrato entre los dos es **`INSTRUCCIONES-WEB.md` §6 y §6.1**. Donde tu §6 de `PARA_ERICK.md` difiere de la guía, **gana la guía** y el frontend se adapta. Yo no invento nombres por mi lado.

## 3. Lo que decidimos

| # | Tema | Decisión | Quién cambia |
|---|---|---|---|
| 1 | Envoltorio | Toda respuesta JSON es `{data, error, status}`. Los archivos (reportes, PDF de diagnóstico, imagen de pausa) van tal cual, sin envoltorio | Frontend lo desenvuelve |
| 2 | Error | El mensaje va en el campo `error` del envoltorio (§6). Ya no existe `mensaje` | Backend |
| 3 | Campos | `camelCase` de §6.1 | Frontend |
| 4 | Video de la revisión | La URL del `<video>` lleva un **token de video** (`?vt=`), nunca el JWT de sesión. Ver §6 de esta nota | Los dos |
| 5 | Notificaciones | La tabla no cambia. Traen `tipo`, `mensaje`, `leido`, `fechaCreacion` e `idExperimento`. El **título y el enlace los arma el frontend** con `tipo` e `idExperimento`. El aviso de pausa abre el progreso de ese experimento, y ahí `/status` dice cuál análisis está en pausa | Frontend |
| 6 | Progreso | `GET /experiments/{id}/status`, por experimento (§9.4). La cola global sale de `/admin/system`. El `/queue` del frontend desaparece | Frontend |
| 7 | **Agregado:** quién es quién | El login y el listado de experimentos devuelven **campos de más** sobre la guía (ver §4 de esta nota) | Backend |
| 8 | **Agregado:** sin video no se corrige | `PUT .../segundos` y `POST .../restart` responden **409** si el video ya se borró. Es tu propuesta (punto 40 de `PARA_ERICK.md`); queda aceptada | Backend |

## 4. El agregado: campos de más para identificar a las personas

La guía devuelve en el login solo `{token, rol, nombre, cambioRequerido}`. El frontend necesita más para decidir qué mostrar. Se agrega, sin quitar nada de la guía:

- **Login:** además, `idInstitucional`, `apellidos` y `correo`. `idInstitucional` pasa a ser el id de la cuenta en el frontend (reemplaza al `id` numérico que usa hoy).
- **Lista y detalle de experimentos:** `creador: {idInstitucional, nombre, apellidos}`. Sirve para la columna «Responsable» y para mostrar «Eliminar» solo a quien lo creó o a un Administrador (R-08). El backend sigue respondiendo 403 si no corresponde.
- **Videos del detalle:** `subidoPor` (el `idInstitucional` de quien lo subió) y `videoDisponible` (ya está en §6.1). Sirven para mostrar «Revisar segundo a segundo» solo a quien puede (D-57) y en gris si el video se borró.

## 5. Qué hacer en el frontend (en este orden)

La regla de siempre: **solo se tocan `services/`, `services/mocks/` y las pruebas; las pantallas no cambian** (sección 5 de `PARA_ERICK.md`).

1. **Reformar los datos de ejemplo al formato final** (`camelCase`, ids numéricos, valores de la guía). Es lo más útil ahora: cuando el backend exista, cambiar un interruptor ya no rompe nada.
2. **Desenvolver `{data, error, status}` en un solo lugar:** el interceptor de respuestas de `services/api.js`. No desenvolver lo que llega como archivo. `mensajeError` lee solo `error`.
3. **Un archivo de equivalencias** (por ejemplo `lib/contrato.js`) con los valores de la tabla de la sección 7, en los dos sentidos, para que las pantallas sigan usando `QUEUED`, `DONE`, etc.
4. **Alinear las rutas** (tabla de la sección 8).
5. **Subir video:** un solo `POST /experiments/{id}/groups/{grupo}/videos` con los campos de §6.1 (`archivo`, `sesion`, `tipo`, `tratamiento`, `nCilindros`, `ordinal`). Ya no se crea el grupo aparte. La letra de la tanda se convierte en el `ordinal` (A = 1). Respuesta: `{idVideo, idAnalisis, estadoAnalisis}`; con el Día 1, los dos últimos son `null`.
6. **Progreso por experimento** con `/status`, y la cola de Administración con `/admin/system`.
7. **Notificaciones:** armar título y enlace por `tipo` (equivalencias en la sección 7).
8. **Video de la revisión** con el token (sección 6).
9. **Interruptor por servicio** en vez de uno global: así se conecta una parte a la vez, a medida que el backend la termina.
10. **Identificadores:** `clave` (`EXP-2026-02`) y `gid` (`G-02`) pasan a ser `idExperimento` y `idGrupo` numéricos. Las rutas de React pueden conservar el nombre del parámetro.

## 6. El video de la revisión

Problema: la etiqueta `<video>` no puede mandar el `Authorization`, y poner el JWT de sesión en la URL sería peligroso si se filtra (dura 168 h y vale para todo).

1. Antes de abrir la revisión, el frontend pide `POST /experiments/{id}/analyses/{idAnalisis}/video-token` con su JWT normal. El backend verifica el permiso (quien subió el video o un Administrador), que el análisis esté `completado` y que el video exista.
2. Responde un token de **solo lectura de ese análisis**, vigencia 2 horas (`VIDEO_TOKEN_MINUTES`).
3. El frontend pone `.../video?vt=<token>` en el `<video>`. El navegador pide trozos con `Range`.
4. Si el video responde 401 o 403 por token vencido, pide otro y sigue **en el mismo segundo**.
5. No escribir esa URL en consola ni en pantalla.

Es una [PROPUESTA] mía, la guía no la trae. Si el video ya se borró, la revisión es de solo lectura (ya lo hiciste).

## 7. Equivalencias de valores

| Qué | Guía (backend) | Frontend hoy |
|---|---|---|
| Estado del experimento | `sin videos`, `en espera del Día 2`, `en cola`, `procesando`, `en pausa`, `completado`, `error`, `cancelado` | `SIN_VIDEOS`, `ESPERA_DIA2`, `QUEUED`, `RUNNING`, `PAUSED`, `DONE`, `FAILED`, `CANCELED` |
| Etapa | `preprocesamiento`, `cilindros`, `clasificacion` | `PREPROCESSING`, `ROI_DETECTION`, `CLASSIFICATION` |
| Tipo de grupo | `control`, `referencia`, `tratamiento experimental` | `CONTROL`, `REFERENCIA`, `EXPERIMENTAL` |
| Sesión | `Dia 1`, `Dia 2` (sin acento) | `DAY1`, `DAY2` |
| Rol | `Investigador`, `Administrador` | `INVESTIGADOR`, `ADMIN` |
| Tipo de notificación | `analisis completado`, `error del pipeline`, `analisis en pausa`, `analisis cancelado`, `alerta de disco`, `video borrado` | `ANALYSIS_DONE`, `ANALYSIS_FAILED`, `ANALYSIS_PAUSED`, `ANALYSIS_CANCELED`, `DISK_ALERT`, `VIDEO_DELETED` |
| Conductas | `nado activo`, `inmovilidad`, `escalamiento`, `conducta activa` | ya coinciden en la revisión |
| Totales por conducta | `totales: {"nado activo": {segundos, porcentaje}, …}` | `nado_s`, `inmovilidad_s`, … (el porcentaje ya viene calculado) |

## 8. Rutas que cambian

| Hoy en el frontend | Debe ser (§6) |
|---|---|
| `POST /auth/forgot` | `POST /auth/recover` |
| `POST /auth/change-password` | `PATCH /auth/password` |
| `PATCH /me` | `PATCH /users/me` |
| `POST /auth/login` con `{email, password}` y respuesta `{token, user}` | `{correo, contrasena}` y `{token, rol, nombre, cambioRequerido}` más lo de la sección 4 |
| `POST .../groups` y `POST .../groups/:gid/batches/:letra/videos` | un solo `POST /experiments/{id}/groups/{grupo}/videos` |
| `GET .../groups/:gid` | sale del árbol de `GET /experiments/{id}` |
| sugerencia de grupos (se calcula en el navegador) | `GET /experiments/{id}/groups?q=` |
| `GET .../batches/:letra/results`, `/group-comparison`, `.../blocks` | `/results`, `/results/byminute`, `/results/groups`, `/results/blocks`, todos de `/experiments/{id}` (el filtro por grupo o tanda se sigue haciendo en el frontend, D-93) |
| `GET /queue` | `GET /experiments/{id}/status` |
| filtros y páginas de la lista, en el navegador | `GET /experiments?estado=&fecha=&tratamiento=&pagina=` con `{pagina, total, items}` |

Ya coinciden: `/auth/login`, `/auth/reset`, `/pause`, `/pause/image`, `/pause/adjust`, `/cancel`, `/diagnostic`, `/review`, `/segundos`, `/restart`, `/report/{formato}`, `/admin/users`, `/admin/system`, `/notifications` y `/experiments` (GET, POST y DELETE).

Filtros de la lista (v0.88.0, encargo 41): `GET /experiments` ya acepta `estado` (el mismo texto que trae cada item, por ejemplo `en cola` o `en espera del Día 2`), `fecha` (`AAAA` o `AAAA-MM-DD`) y `tratamiento` (el de algún grupo, sin distinguir mayúsculas ni acentos), todos opcionales y combinables; un valor que no es de esos formatos da `400`, y `total` son los que pasan el filtro. Tu código actual, que pide todas las páginas y filtra en el navegador, sigue funcionando sin cambios; si quieres que el servidor filtre, ya puedes usarlos.

Dos rutas son propuestas tuyas que la guía no trae: `POST /admin/users/:id/reset-temporal` y `POST /notifications/read-all`. Las dejo por ahora y las vuelvo a revisar al implementar.

## 9. Orden para conectar

1. Inicio de sesión, primer acceso y perfil.
2. Lista y detalle de experimentos (crear, eliminar, filtros).
3. Subir video.
4. Progreso y pausa.
5. Resultados y reportes.
6. Revisión segundo a segundo (con el token de video).
7. Administración.
8. Notificaciones.

Cuando termines una, **avísame** para que pruebe esa parte contra el backend, y apunta el cambio en `PARA_ERICK.md` (tu regla de la sección 13).

## 10. Confirmaciones pendientes del lado del backend

- **`{grupo}` en la ruta de subir video.** El grupo puede no existir todavía, así que lo más probable es que sea su etiqueta (nombre), no un id. Lo confirmo al implementarlo.
- Cuándo exista cada ruta, te aviso con la fecha y los campos exactos que devuelve, para ajustar tus datos de ejemplo.

## 11. Instrucciones para Claude

Si eres Claude trabajando en este repo:

- Responde y escribe en español. No toques `main`; trabaja en la rama indicada y no abras PR hacia `main` sin que lo pidan.
- Lee primero esta nota y la guía (`INSTRUCCIONES-WEB.md` §6, §6.1, §9, §11). Si algo falta o parece contradictorio, **pregunta, no inventes**.
- Las pantallas no importan datos. Todo cambio de forma de datos va en `services/`, en `services/mocks/data.js` y en las pruebas.
- Verifica cada cambio: `npm test`, `npm run lint` y el build sin errores.
- Marca lo hecho en `frontend/CHECKLIST.md` **solo si está verificado en el código**.
