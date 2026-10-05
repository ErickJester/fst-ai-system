# Para Erick (y su Claude): rama `feat/frontend-v2-servicios`

Documento de traspaso para que trabajemos organizados sobre el frontend. Está escrito para que lo lea una persona y también para dárselo como contexto a Claude Code («lee `PARA_ERICK.md` antes de empezar»).

Autora de la rama: Vanesa · Fecha: 4 oct 2026 · Base: `main` en `a57356b` (tu merge del frontend v2).

---

## 1. Reglas del equipo (léelas primero)

1. **No se toca `main`.** Nada de commits, merges, rebases ni push directo. Todo el trabajo va en ramas y entra por PR cuando el equipo lo decida.
2. **Todo en español**: interfaz, comentarios, mensajes de commit y respuestas de Claude.
3. **Terminología en la interfaz** (y en lo que se exporta, como el CSV):
   - Se dice **«Espécimen» / «Especímenes»**. «Rata» en singular se permite solo como especie (p. ej. «Rata Wistar»); **nunca «ratas»**.
   - Prohibido en la interfaz: «animal», «IA», «inteligencia artificial».
4. **Los mockups v2 (`mockup/v2/`) mandan en lo visual.** Las pantallas deben verse como el mockup; si algo se aparta, se documenta por qué.
5. Antes de hacer push o abrir un PR, avisar a la otra persona.

---

## 2. Qué es esta rama

Une dos trabajos que se hicieron por separado:

| Venía de | Qué aportó |
|---|---|
| Tu frontend v2 (`main`) | Todas las pantallas de los mockups v2, completas y fieles al diseño. |
| La fase 0 de Vanesa (`feat/frontend-v2-fase0`) | Organización interna: capa de servicios con datos de ejemplo con forma de API, `useAsync`, `usePolling`. |

**Resultado:** tus pantallas se ven igual, pero ya no importan datos fijos; piden todo a `src/services/`. Hoy esos servicios devuelven datos de ejemplo; cuando el backend tenga los endpoints, se cambia una variable y las pantallas llaman a la API sin tocarlas.

Encima se agregó: Nuevo experimento (paso 1), contraseña temporal, restablecer contraseña, manejo de 401/403, ajustes del documento de requisitos y una limpieza de código repetido. Detalle en la sección 7.

---

## 3. Cómo correrlo

```bash
npm --prefix frontend install
npm --prefix frontend run dev
```

Abre http://localhost:5173. Por defecto usa **datos de ejemplo** (`VITE_USE_MOCKS` distinto de `'false'`). Para llamar al backend real:

```bash
VITE_USE_MOCKS=false VITE_API_BASE=http://localhost:8000 npm --prefix frontend run dev
```

### Cuentas para probar (solo con datos de ejemplo)

| Correo | Rol | Contraseña |
|---|---|---|
| `mrivera@ipn.mx` | Investigador | cualquiera no vacía |
| `creyesl@ipn.mx` | Administrador | cualquiera no vacía |
| `jdominguez@ipn.mx`, `lortega@ipn.mx` | Investigador | cualquiera no vacía |
| Cuenta creada en Admin | Investigador | solo su contraseña temporal; luego la que ponga en el primer acceso |
| Cualquier otro correo `@ipn.mx` | Investigador | entra como cuenta nueva con contraseña temporal (atajo de demo) |

- **Recuperar contraseña**: como no hay correo, tras pedir el enlace aparece «Modo demo, sin correo: abrir el enlace que llegaría por correo». `/restablecer?token=vencido` muestra el caso de enlace vencido.
- **Al recargar (F5) los datos de ejemplo vuelven al estado inicial** (viven en memoria). La sesión dura hasta cerrar la pestaña.

---

## 4. Estructura del frontend

```
frontend/src/
├── router.jsx              Rutas y protección (sin sesión → /login; temporal → /primer-acceso; /admin solo admin)
├── contexts/AuthContext.jsx  Sesión: login, logout, cambiarPassword, actualizarPerfil
├── services/               ÚNICO lugar que sabe de datos
│   ├── config.js           USE_MOCKS, TOKEN_KEY, AVISO_KEY
│   ├── api.js              axios: manda el token; 401 → login con aviso; 403 → Experimentos con aviso
│   ├── auth.js  admin.js  experiments.js  groups.js  queue.js  results.js  notifications.js
│   └── mocks/
│       ├── data.js         Datos de ejemplo con forma de API
│       └── delay.js        Simula latencia (reply) y errores (fail)
├── hooks/                  useAsync, usePolling, useErrorDeCampo, useAviso
├── components/
│   ├── Topbar.jsx          Barra superior, campana (polling 30 s) y menú de usuario
│   └── ui.jsx              Componentes compartidos (ver abajo)
├── lib/
│   ├── fst.js              Utilidades: fechas, estadísticos, CSV, etiquetas de valores de la API
│   └── estilos.js          Estilos en línea repetidos (MUT60, MUT70, btnLeft, mutedSub, textoTarjeta)
└── pages/                  Una página por pantalla del mockup
```

### Pantallas y rutas

| Mockup | Ruta | Página |
|---|---|---|
| 2h Login / recuperar | `/login` (`?recuperar=1` abre recuperar) | `LoginPage` |
| 2h Restablecer (nueva) | `/restablecer?token=…` | `RestablecerPage` |
| 2i Primer acceso | `/primer-acceso` | `PrimerAccesoPage` |
| 2a Experimentos | `/experimentos` | `ExperimentosPage` |
| Paso 1 (nueva, sin mockup) | `/experimentos/nuevo` | `NuevoExperimentoPage` |
| 2c Experimento | `/experimentos/:clave` | `ExperimentoPage` |
| 2d Grupo | `/experimentos/:clave/grupos/:gid` | `GrupoPage` |
| 2b Cargar video | `/experimentos/:clave/cargar` | `CargarVideoPage` |
| 2f Resultados | `/experimentos/:clave/resultados` (`?grupo=&tanda=`) | `ResultadosPage` |
| 2e Progreso | `/analisis` | `ProgresoPage` |
| 2j Perfil | `/perfil` | `PerfilPage` |
| 2g Administración | `/admin` | `AdminPage` |
| 2k Barra y campana | en todas | `Topbar` |

---

## 5. Convenciones para escribir código aquí

- **Las páginas nunca importan datos.** Piden a una función de `services/` con `useAsync` (o `usePolling` si se refresca sola). Cada función de servicio tiene la forma:
  ```js
  // GET /ruta → qué devuelve (pantalla 2x)
  export async function algo(args) {
    if (USE_MOCKS) return reply(db.algo)        // datos de ejemplo
    return (await api.get('/ruta')).data        // API real
  }
  ```
- **Los datos tienen forma de API, la apariencia vive en la página.** Los servicios devuelven valores como `QUEUED`, `DONE`, `CONTROL`, `fecha_inicio: '2026-02-18'`; las páginas los traducen a etiquetas y colores (`lib/fst.js`, `EstadoTag`, `TipoTag`, `fechaCorta`).
- **Componentes compartidos** (`components/ui.jsx`): `Campo`, `FieldError`, `Seg`, `EstadoTag`, `TipoTag`, `Migas`, `Pasos`, `Acceso`, `Cargando`, `NoEncontrado`, `rellenoTanda`. Úsalos antes de escribir markup nuevo.
- **Formularios**: `useErrorDeCampo(refs)` da `{ err, fail, clase, refs }` para marcar el campo con error y pasarle el foco; los errores del servidor se leen con `mensajeError(e, 'respaldo')`.
- **Avisos tras redirigir** (sesión caducada, sin permiso): `useAviso('tipo')`.
- Estilo: el de los mockups (estilos en línea + clases de `styles/fst.css`, `modernist.css`, `app.css`). Comentarios cortos en español explicando el porqué.

### Cómo verificar un cambio

1. `npm --prefix frontend run build` debe compilar sin errores.
2. Probarlo en el navegador con la app recién cargada y revisar que la consola no tenga errores.
3. Para refactors que no deben cambiar nada visible, se usó una «huella»: guardar el `innerHTML` de cada pantalla antes y comparar después (24 estados, salieron idénticos). Si cambias markup compartido, conviene repetirlo.

---

## 6. Contrato de API que asume el frontend

Son **propuestas** hechas desde el frontend; el backend tiene la última palabra. Si algo cambia, solo se ajustan `services/*.js` y `services/mocks/data.js`.

| Método y ruta | Envía | Responde |
|---|---|---|
| `POST /auth/login` | `{ email, password }` | `{ token, user }` · 401 genérico si falla |
| `POST /auth/forgot` | `{ email }` | `{ ok }`, igual exista o no la cuenta |
| `POST /auth/reset` | `{ token, new_password }` | `{ ok }` · **400** si el enlace venció o ya se usó |
| `POST /auth/change-password` | `{ current_password, new_password }` | usuario · 400 si la actual no es correcta |
| `PATCH /me` | `{ nombre, apellidos, email }` | usuario · 409 si el correo ya existe |
| `GET /admin/users` | — | lista de cuentas (sin contraseñas) |
| `POST /admin/users` | `{ nombre, apellidos, email, identificador }` | **201** con `password_temporal` (una sola vez) · 409 si el correo existe |
| `PATCH /admin/users/:id` | `{ is_active }` | cuenta · 409 si deja el sistema sin administradores |
| `GET /admin/system` | — | `{ disco, conductas, modelo }` |
| `GET /experiments` | — | lista con estado agregado |
| `POST /experiments` | `{ titulo, fecha_inicio, especie, notas }` | `{ clave }` (el responsable sale de la sesión) |
| `GET /experiments/:clave` | — | datos generales + grupos → tandas |
| `DELETE /experiments/:clave` | `{ titulo, password }` | `{ ok }` |
| `GET /experiments/:clave/groups/:gid` | — | grupo con tandas, Día 1/Día 2, progreso y error |
| `POST /experiments/:clave/groups` | `{ nombre, tipo, tratamiento }` | grupo |
| `POST /experiments/:clave/groups/:gid/batches/:letra/videos` | multipart `file`, `dia`, `n_cilindros` | `{ job_id, posicion_cola }` |
| `GET /queue` | — | `{ cola, errores }` |
| `GET /experiments/:clave/groups/:gid/batches/:letra/results?day=` | — | tiempos por espécimen (s), línea de tiempo, nivel, confianza |
| `GET /experiments/:clave/group-comparison` | — | inmovilidad media ± DE por grupo |
| `GET /notifications` · `PATCH /notifications/:id` · `POST /notifications/read-all` | `{ is_read }` | — |

**Valores que usa el frontend:** estado de tanda `QUEUED | RUNNING | DONE | FAILED | SIN_VIDEO`; etapa `PREPROCESSING | ROI_DETECTION | TRACKING | CLASSIFICATION`; tipo de grupo `CONTROL | REFERENCIA | EXPERIMENTAL`; rol `INVESTIGADOR | ADMIN`; día `DAY1 | DAY2`. Los primeros siguen a `backend/app/models.py`.

**Sesión:** el token va en `sessionStorage` (`fst.token`) y se manda como `Authorization: Bearer`. Cerrar sesión es solo del frontend (borra el token). 401 con sesión → login con «Tu sesión caducó»; el 401 del propio login muestra «Correo o contraseña incorrectos» sin redirigir. 403 → Experimentos con «No tienes permiso para esa sección».

---

## 7. Qué se hizo en esta rama (16 commits)

1. **Capa de servicios** en todas las pantallas: Experimentos, Experimento, Grupo, Progreso, Resultados, Cargar video, Admin, Login/Primer acceso/Perfil y notificaciones. Admin y el login comparten las mismas cuentas.
2. **Nuevo experimento, paso 1** (`/experimentos/nuevo`): nombre, fecha, especie, notas; sigue al paso 2. No había mockup: se hizo con el estilo de Cargar video.
3. **Limpieza**: componentes compartidos, `useErrorDeCampo`, `mensajeError`, `lib/estilos.js`; se eliminó `data/mock.js`. Verificado con la huella: el HTML no cambió.
4. **Ajustes del documento de requisitos**: acepta .mp4 y .mov; rechaza videos verticales; barra de progreso al subir; texto exacto de recuperación; aviso de sesión caducada; cerrar sesión sin llamar al servidor; Progreso consulta cada 5 s con análisis activos y cada 30 s sin ninguno.
5. **Corrección importante**: antes, cualquier 401 (incluida una contraseña incorrecta en el login) mandaba al login sin mostrar el error.
6. **«Espécimen» en lugar de «Rata»** en Experimento, Grupo, Resultados y CSV. El campo de resultados pasó de `rata` a `especimen`.
7. **El enlace de recuperación vale 60 minutos** (como `RESET_LINK_MINUTES`); el mockup decía 30.
8. **Contraseñas y permisos**: modal en Admin que muestra la contraseña temporal una sola vez; pantalla `/restablecer` con caso de enlace vencido; los datos de ejemplo comprueban contraseñas de las cuentas creadas; 403 con aviso.

### Diferencias visibles con tu v2 (a propósito)

- Las sugerencias de Cargar video dicen «Referencia · Fluoxetina 10 mg/kg» (nombre + tratamiento real) en vez de «Referencia · fluoxetina».
- Referencia muestra «tandas cargadas: A, B» y precarga la C, para coincidir con el detalle del experimento.
- El CSV exportado se llama `resultados_EXP-2026-02_referencia_tandaA_dia2.csv` (con la clave del experimento).
- Lo leído/no leído de las notificaciones ya no se guarda en el navegador; lo maneja el servicio.

---

## 8. Decisiones tomadas

| Tema | Decisión |
|---|---|
| Término para los sujetos | «Espécimen / Especímenes»; nunca «ratas». |
| Contraseña temporal | Se muestra una sola vez al crear la cuenta. Sin caducidad por ahora. |
| Dónde guardar el token | `sessionStorage` (falta decidir si la sesión debe sobrevivir al cerrar la pestaña). |
| Revisión segundo a segundo | Va en este frontend, construida al final (§12 del documento). Mientras, se usa `tools/fst_labeler`. |
| Respuesta 403 | Mensaje «No tienes permiso para esa sección» y regreso a Experimentos. |

---

## 9. Preguntas abiertas para el backend

1. **Contraseña temporal en el 201**: ¿en qué campo? El frontend supone `password_temporal`.
2. **Restablecer una temporal perdida**: ¿habrá algo como `POST /admin/users/:id/reset-temporal` que devuelva otra temporal y vuelva a poner `cambioRequerido = true`? Sin esto, una cuenta cuya temporal se perdió no puede entrar.
3. **Duración de la sesión**: si no debe sobrevivir al cerrar la pestaña, se queda `sessionStorage` y conviene bajar `JWT_EXPIRES_HOURS` (168 h hoy) a algo como 12 h. Si debe sobrevivir, cookie HttpOnly (Set-Cookie, SameSite, CORS con credenciales, CSRF, y logout que borre la cookie).
4. **Nombres de campos**: el documento dice `cambioRequerido`; el frontend usa `must_change_password`, `role`, `email`, `is_active`. Con la lista real de `/auth/login` y `/admin/users` se alinean los datos de ejemplo de una vez.
5. **Recuperación**: ¿la ruta del enlace es `/restablecer?token=…` y el enlace vencido responde `400 { error }`?
6. **Editar cuenta (§9.6)**: ¿qué campos puede cambiar el administrador (nombre, apellidos, correo, rol) y con qué ruta (`PATCH /admin/users/:id`)?

---

## 10. Pendiente en el frontend

- **Editar cuenta** en Admin (espera la pregunta 6).
- **Restablecer temporal** desde Admin (espera la pregunta 2; el modal ya existe y se reutiliza).
- **PDF de diagnóstico desde el servidor** con reintento «preparando…» (hoy se arma en el navegador).
- **Revisión segundo a segundo** (§9.7): al final; necesita `GET .../review`, `GET .../video` con Range y `PUT .../segundos`. El borrador local debe guardarse por usuario y borrarse al cerrar sesión.
- **Conectar al backend**: cuando existan los endpoints, probar cada pantalla con `VITE_USE_MOCKS=false` y ajustar nombres de campos.

---

## 11. Instrucciones para Claude Code

Si eres Claude trabajando en este repo:

- Responde y escribe en español. No modifiques `main` por ningún motivo; trabaja en la rama que te indiquen y no abras PR hacia `main` sin que lo pidan.
- Antes de tocar una pantalla, lee su mockup en `mockup/v2/` y la página en `frontend/src/pages/`. Respeta las convenciones de la sección 5: datos solo desde `services/`, componentes de `components/ui.jsx`, textos con la terminología de la sección 1.
- Si un dato o endpoint no existe en el backend, agrégalo primero a `services/mocks/data.js` con forma de API y documenta la ruta propuesta en el comentario de la función de servicio y en la tabla de la sección 6 de este archivo.
- Verifica cada cambio: build sin errores, prueba en el navegador y consola limpia. Para refactors, compara el HTML antes y después.
- Si cambias algo de este traspaso (contrato, decisiones, pendientes), actualiza este archivo en el mismo commit.
