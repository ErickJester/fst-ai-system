# Para Erick (y su Claude): rama `feat/frontend-v2-servicios`

Documento de traspaso para que trabajemos organizados sobre el frontend. Está escrito para que lo lea una persona y también para dárselo como contexto a Claude Code («lee `PARA_ERICK.md` antes de empezar»).

Autora de la rama: Vanesa · Fecha: 4 oct 2026 (actualizado el 5 oct, tras tu `PARA_VANESA.md`) · Base: `main` en `a57356b` (tu merge del frontend v2) · Versión: **0.3.0**.

---

## 1. Reglas del equipo (léelas primero)

1. **No se toca `main`.** Nada de commits, merges, rebases ni push directo. Todo el trabajo va en ramas y entra por PR cuando el equipo lo decida.
2. **Todo en español**: interfaz, comentarios, mensajes de commit y respuestas de Claude.
3. **Terminología en la interfaz** (y en lo que se exporta, como el CSV):
   - A cada sujeto se le nombra **por su cilindro: «Cilindro P1»**. Donde se junten varias tandas (vista por grupo, CSV combinado), **«Tanda A · Cilindro P1»**, porque cada tanda tiene su propio P1. Para conteos se dice «especímenes» (p. ej. «32 especímenes»). «Rata» en singular se permite solo como especie («Rata Wistar»); **nunca «ratas»**.
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
| Cualquier otro correo (de cualquier dominio, D-46) | Investigador | entra como cuenta nueva con contraseña temporal (atajo de demo) |

- **Recuperar contraseña**: como no hay correo, tras pedir el enlace aparece «Modo demo, sin correo: abrir el enlace que llegaría por correo». `/restablecer?token=vencido` muestra el caso de enlace vencido.
- **La cola de análisis no avanza sola:** la avanza el worker (equipo de backend). Sin backend queda fija; la pausa de ejemplo (502) empieza su plazo de 5 minutos la primera vez que se abre.
- **Los datos de ejemplo sobreviven a la recarga** (se guardan en `localStorage`, clave `fst.demo`). Para volver a los originales: **«Reiniciar datos»** en la franja de MODO DEMO (también cierra la sesión). La sesión dura hasta cerrar la pestaña.

---

### Contenedor de producción (propuesta)

`frontend/Dockerfile` sigue siendo el de desarrollo (servidor de Vite, ~420 MB). Para producción hay uno aparte, que **no reemplaza** al tuyo:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up --build
```

- `frontend/Dockerfile.prod`: compila con Node y sirve `dist/` con nginx (imagen de ~75 MB).
- `frontend/nginx.conf`: rutas de la app → `index.html` (recargar en `/experimentos/...` funciona), `/api/` se reenvía al contenedor `api` sin el prefijo (un solo origen, sin CORS), caché larga para `assets/`, `client_max_body_size 10g` para los videos, y `/salud` para el healthcheck.
- `docker-compose.prod.yml`: cambia el frontend a esa imagen en el puerto **8080**. `VITE_USE_MOCKS` y `VITE_API_BASE` son argumentos de construcción (se graban al compilar); con el backend listo, `VITE_USE_MOCKS: "false"`.
- La app ya no depende de internet al usarse: la fuente Archivo viene de `@fontsource/archivo` y el XLSX de SheetJS 0.20.3 (distribución oficial, sin las fallas de la 0.18.5 de npm), cargado solo al exportar. Construir la imagen sí necesita internet (npm).

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
- **Datos que cambian solos**: `useAsyncVivo(fn, deps, intervaloSegun)` en lugar de `useAsync` cuando la pantalla debe refrescarse; los intervalos son `CADA_ACTIVO_MS` y `CADA_INACTIVO_MS`.
- **Avisos tras redirigir** (sesión caducada, sin permiso): `useAviso('tipo')`.
- **Enlaces a resultados**: `enlaceResultados(clave, gid, letra)` y `primeraTandaLista(exp)` (`lib/fst.js`); nunca un enlace fijo al experimento de ejemplo.
- **Diálogos**: `useAtraparFoco(ref, activo)` mantiene el foco dentro y lo devuelve al cerrar.
- **Errores al dibujar**: `components/ErrorBoundary.jsx` envuelve las rutas y muestra «Algo salió mal en esta pantalla» en lugar de dejarla en blanco.
- Estilo: el de los mockups (estilos en línea + clases de `styles/fst.css`, `modernist.css`, `app.css`). Comentarios cortos en español explicando el porqué.

### Cómo verificar un cambio

1. `npm --prefix frontend test` (Vitest): las pruebas de `lib/fst.test.js`, `services/servicios.test.js` y `pantallas.test.jsx` (pantallas en jsdom) deben pasar. Si cambias una regla de los servicios (contraseñas, administradores, experimentos), agrega o ajusta su prueba.
2. `npm --prefix frontend run lint` (ESLint) sin errores: detecta variables sin definir, importaciones sin usar y hooks mal ordenados.
3. `npm --prefix frontend run build` debe compilar sin errores.
4. Probarlo en el navegador con la app recién cargada y revisar que la consola no tenga errores.
5. Para refactors que no deben cambiar nada visible, se usó una «huella»: guardar el `innerHTML` de cada pantalla antes y comparar después (24 estados, salieron idénticos). Si cambias markup compartido, conviene repetirlo.

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
| `PATCH /admin/users/:id` | `{ is_active }` o `{ nombre, apellidos, email, identificador, role }` | cuenta · 409 si el correo existe o si deja el sistema sin administradores |
| `POST /admin/users/:id/reset-temporal` | — | `{ password_temporal }` (una sola vez); la anterior deja de servir y vuelve `cambioRequerido` |
| `GET /admin/system` | — | `{ disco, conductas, modelo }` |
| `GET /experiments` | — | lista con estado calculado, `tratamientos` (lista), `creador_id` y `aviso_vencimiento` |
| `POST /experiments` | `{ titulo, fecha_inicio, notas }` | `{ clave }` (el responsable sale de la sesión); 409 si el nombre ya existe |
| `GET /experiments/:clave` | — | datos generales + grupos → tandas |
| `DELETE /experiments/:clave` | — (D-63: sin nombre ni contraseña) | `{ ok }`; 403 si no es quien lo creó ni Administrador; 409 con un análisis procesando o en pausa |
| `GET /experiments/:clave/groups/:gid` | — | grupo con tandas, Día 1/Día 2, progreso y error |
| `POST /experiments/:clave/groups` | `{ nombre, tipo, tratamiento }` | grupo |
| `POST /experiments/:clave/groups/:gid/batches/:letra/videos` | multipart `file`, `dia`, `n_cilindros` | `{ job_id, posicion_cola }`; con `dia=DAY1` ambos en `null` (el Día 1 no se analiza, D-54); 422 si el video es vertical o no se puede reproducir (D-64) |
| `GET /queue` | — | `{ cola, pausas, errores, cancelados }` (cada pausa trae `pausa` y `segundos_restantes`); cada error trae `stage` (etapa en que se detuvo), sin código ni mensaje |
| `GET /experiments/:clave/analyses/:id/diagnostic` | — | PDF de diagnóstico; 404 mientras el worker no lo escribe (la pantalla muestra «Preparando…» y reintenta cada 4 s) |
| `GET /experiments/:clave/analyses/:id/pause` | — | `{ cilindro, desdeSegundo, segundosRestantes, ancho, alto, recuadros }` (§6.1); 409 si ya no está en pausa, 403 si no subió el video ni es Administrador |
| `GET /experiments/:clave/analyses/:id/pause/image` | — | imagen fija (JPG) |
| `POST /experiments/:clave/analyses/:id/pause/adjust` | `{ cilindro, x0, y0, x1, y1 }` en píxeles de la imagen | 200, o 409 si ya no está en pausa |
| `POST /experiments/:clave/analyses/:id/cancel` | — | 200, o 409 si ya no está en pausa |
| `GET /experiments/:clave/groups/:gid/batches/:letra/results?day=` | — | `especimenes` (`numeroCilindro` y tiempos por conducta en s), línea de tiempo, nivel |
| `GET /experiments/:clave/group-comparison` | — | por grupo, `por_conducta` (media ± DE de cada conducta) o `por_nivel` si mezcla niveles |
| `GET /experiments/:clave/groups/:gid/batches/:letra/blocks` | — | `especimenes: [{ numeroCilindro, bloques: [{ inicio, conducta, matiz, segundos }] }]` (D-53) |
| `GET /experiments/:clave/report/:formato` | `csv`, `xlsx` o `pdf`; CSV y XLSX con `?grupo=` (grupo completo) o `?grupo=&tanda=` (una tanda) | el archivo tal cual (lo genera el backend; se regenera si hay un análisis más nuevo) |
| `GET /experiments/:clave/analyses/:id/review` | — | `{ puedeCorregir, videoUrl, duracion, ancho, alto, especimenes: [{ numeroCilindro, recuadro, segundos: [{ segundo, conducta, origen }] }] }` (§6.1; conductas con su nombre de la base); 403 sin permiso, 409 si no está completado |
| `PUT /experiments/:clave/analyses/:id/segundos` | `{ especimenes: [{ numeroCilindro, segundos: [{ segundo, conducta, origen }] }] }` solo lo que cambió | totales recalculados |
| `POST /experiments/:clave/analyses/:id/restart` | — | 202; borra el resultado al empezar y deja el análisis en cola |
| `GET /notifications` · `PATCH /notifications/:id` · `POST /notifications/read-all` | `{ is_read }` | — |

**Valores que usa el frontend:** estado de tanda `QUEUED | RUNNING | DONE | FAILED | SIN_VIDEO` (con `FAILED`, `error: { etapa }`); `dia1: { cargado: true } | null`; etapa `PREPROCESSING | ROI_DETECTION | CLASSIFICATION` (33, 67 y 100 %); estado de experimento `SIN_VIDEOS | ESPERA_DIA2 | QUEUED | RUNNING | PAUSED | DONE | FAILED | CANCELED`; tipo de grupo `CONTROL | REFERENCIA | EXPERIMENTAL`; rol `INVESTIGADOR | ADMIN`; día `DAY1 | DAY2`. Los primeros siguen a `backend/app/models.py`.

**Sesión:** el token va en `localStorage` (`fst.token`) y se manda como `Authorization: Bearer`. Cerrar sesión es solo del frontend (borra el token). 401 con sesión → login con «Tu sesión caducó»; el 401 del propio login muestra «Correo o contraseña incorrectos» sin redirigir. 403 → Experimentos con «No tienes permiso para esa sección».

---

## 7. Qué se hizo en esta rama

1. **Capa de servicios** en todas las pantallas: Experimentos, Experimento, Grupo, Progreso, Resultados, Cargar video, Admin, Login/Primer acceso/Perfil y notificaciones. Admin y el login comparten las mismas cuentas.
2. **Nuevo experimento, paso 1** (`/experimentos/nuevo`): nombre, fecha, especie, notas; sigue al paso 2. No había mockup: se hizo con el estilo de Cargar video.
3. **Limpieza**: componentes compartidos, `useErrorDeCampo`, `mensajeError`, `lib/estilos.js`; se eliminó `data/mock.js`. Verificado con la huella: el HTML no cambió.
4. **Ajustes del documento de requisitos**: acepta .mp4 y .mov; rechaza videos verticales; barra de progreso al subir; texto exacto de recuperación; aviso de sesión caducada; cerrar sesión sin llamar al servidor; Progreso consulta cada 5 s con análisis activos y cada 30 s sin ninguno.
5. **Corrección importante**: antes, cualquier 401 (incluida una contraseña incorrecta en el login) mandaba al login sin mostrar el error.
6. **Sin «Rata» en la interfaz**: primero se cambió a «Espécimen 1 · Cilindro P1» y después, por tu propuesta de `PARA_VANESA.md` §3, a **«Cilindro P1»** en Experimento, Grupo, Resultados y CSV. Los resultados de ejemplo traen `cilindros: [{ cilindro: 'P1', … }]`, sin número de espécimen.
7. **El enlace de recuperación vale 60 minutos** (como `RESET_LINK_MINUTES`); el mockup decía 30.
8. **Contraseñas y permisos**: modal en Admin que muestra la contraseña temporal una sola vez; pantalla `/restablecer` con caso de enlace vencido; los datos de ejemplo comprueban contraseñas de las cuentas creadas; 403 con aviso.
9. **Versión 0.3.0**, como pediste en `PARA_VANESA.md`.
10. **Datos de ejemplo persistentes** en `localStorage` y botón «Reiniciar datos» en la franja de MODO DEMO.
11. **Admin**: editar cuenta (nombre, apellidos, correo, identificador, rol), restablecer la contraseña temporal (mismo modal, se muestra una vez) y reactivar cuentas inactivas. La cuenta propia se edita en Mi perfil y el rol del único Administrador activo no se puede cambiar.
12. **Calidad**: pruebas automáticas con Vitest (`npm test`, 24 pruebas) y revisión con ESLint (`npm run lint`).
13. **Errores y rutas**: aviso «Algo salió mal» en lugar de pantalla en blanco; una ruta inexistente lleva a la pantalla de inicio de la cuenta, y con sesión abierta `/login` ya no muestra el formulario.
14. **Detalles de uso**: sugerencias de grupo con el teclado (flechas, Enter, Escape) en Cargar video; especie y notas en el encabezado de Experimento; cada enlace a resultados lleva a su tanda, y sin resultados se ve «Todavía no hay resultados».
15. **Seguridad**: axios 1.20.0 y react-router 6.30.6 (fallas de severidad alta en axios). Quedan avisos que exigen versiones mayores: react-router v7 (moderado, solo con enlaces armados con datos externos) y tinypool/esbuild/Vite, que afectan solo a las pruebas y al servidor de desarrollo.
16. **Modo demo vivo y accesibilidad**: la cola avanza sola; eliminar experimento comprueba la contraseña de la cuenta; los diálogos atrapan el foco y lo devuelven al cerrar; la navegación de Resultados es un `<nav>` con `aria-current`.
17. **Comparación entre grupos calculada** a partir de las tandas y sus resultados (se actualiza con la simulación), y **nivel agrupado** en Resultados («Conducta activa» e «Inmovilidad», también en el CSV).
18. **Pruebas de pantallas** con React Testing Library (`src/pantallas.test.jsx`): acceso, primer acceso, recuperar contraseña, crear cuenta, cargar video y resultados. Con las de servicios, 42 pruebas.
19. **Cancelar la subida** de un video (`signal` de un `AbortController` en `uploadBatchVideo`) y limpieza de código sin uso (`hooks/useApi.js`, `store`).
20. **Pantallas que se actualizan solas**: Experimento, Grupo y la cola de Admin, con `useAsyncVivo` (5 s con análisis activos, 30 s sin ellos, sin parpadeo de «Cargando…»).
21. **Contenedor de producción** (propuesta, sección 3): `Dockerfile.prod` + nginx + `docker-compose.prod.yml`; fuente incluida en la app.
22. **D-63**: eliminar un experimento pide solo confirmación, sin nombre ni contraseña (app, servicio, pruebas y `mockup/v2/experimento.html`).
23. **Ajustes de `INSTRUCCIONES-WEB.md`** (repo de LaTeX, rama `accesos-login`):
    - **Error del análisis según la etapa** (§9.4): «No se pudo abrir el video» o «No se encontraron los tubos en el video», sin código (`E-DET-070`) ni confianza de detección, porque R-10 se eliminó. El PDF de diagnóstico lo pide al backend y, si aún no existe, muestra «Preparando…» y lo descarga solo cuando está listo.
    - **El Día 1 no se analiza** (D-54): se guarda sin crear trabajo en la cola; Grupo lo muestra como «Cargado» y Cargar video dice «Guardar video».
    - **Correo de cualquier dominio** (D-46) en el inicio de sesión, recuperar contraseña, Mi perfil y Admin; la etiqueta ahora dice «Correo electrónico».
    - **Sin episodios de 3 s** en Resultados (RF-18: ninguna conducta se descarta por durar poco); se quitó la pestaña «Episodios».
24. **Primer bloque de `INSTRUCCIONES-WEB.md` del `main` del repo de LaTeX** (es ley, 12-oct):
    - **Orientación y reproducibilidad las valida el servidor** (D-64): el navegador solo revisa la extensión; el rechazo del servidor (422) se muestra en «El servidor rechazó el video». Con datos de ejemplo, un archivo con «vertical» o «dañado» en el nombre simula el rechazo.
    - **Tres etapas** (preprocesamiento, cilindros, clasificación) a 33, 67 y 100 %.
    - **Ocho estados del experimento**, calculados con la precedencia del documento: Sin videos, En espera del Día 2 (D-83), En cola, Procesando, En pausa, Completado, Con error y Cancelado. En la lista, filtro por estado.
    - **Al subir:** Día 2 → Progreso con el aviso «El análisis está en cola»; Día 1 → el experimento con «No se analiza». Tandas de **2 a 4 especímenes**.
    - **«Espécimen» en vez de «Cilindro P1»** (D-55): «Tanda A · Espécimen 1» en Resultados y CSV, «Espécimen 1» dentro de la tanda en Grupo. Los resultados traen `especimenes: [{ numeroCilindro: 1, … }]`.
25. **Segundo bloque de `INSTRUCCIONES-WEB.md`:**
    - **Experimentos:** filtros por estado, fecha (año) y tratamiento (basta con que un grupo lo tenga); aviso con la fecha exacta de los videos que se borran en 7 días o menos (`aviso_vencimiento: { fecha_borrado }`, R-05).
    - **Eliminar desde la lista y desde el detalle** (D-63), con el mismo diálogo (`components/EliminarExperimento.jsx`). Solo quien lo creó (`creador_id`) o un Administrador; el servicio responde 403 a los demás y 409 si algún análisis está procesando o en pausa; los que están en cola salen de la cola (R-08, D-87).
    - **Seis tipos de notificación:** `ANALYSIS_DONE`, `ANALYSIS_FAILED`, `ANALYSIS_PAUSED`, `ANALYSIS_CANCELED`, `DISK_ALERT` (solo Administradores) y `VIDEO_DELETED`.
26. **Pantalla de pausa** (R-15, D-89) en `/experimentos/:clave/analisis/:id/pausa` (`pages/PausaPage.jsx`): imagen fija del instante, recuadro del cilindro que se arrastra y redimensiona (`components/RecuadroEditable.jsx`, también con flechas y con campos en píxeles de la imagen), cuenta regresiva de 5 minutos, «Ajustar y continuar» y «Cancelar análisis» (con confirmación). Solo quien subió el video (`subido_por`) o un Administrador. Progreso muestra los análisis en pausa y los cancelados; Grupo y Experimento conocen los estados `PAUSED` y `CANCELED`.
    - **Modo demo:** el trabajo 502 (Control · Tanda B) se pausa unos 24 s después de abrir la app; un video con «pausa» en el nombre también se pausa. Sin respuesta en 5 minutos, se cancela y avisa.
27. **Resultados con las cuatro conductas** (R-12): cada espécimen trae `nado_s`, `inmovilidad_s`, `escalamiento_s` y `activa_s`; «Conducta activa» es siempre la cuarta columna. `nivel` (nivelClasif) es AGRUPADO si algún segundo quedó como conducta activa. Comparación entre grupos por conducta (`por_conducta`, con selector). CSV y XLSX con las columnas del documento (`Nado`, `Escalamiento`, `Inmovilidad`, `Conducta activa`) y el desglose por minuto con `Tiempo (min:seg)`.
    - **Pestaña «Bloques de 5 s»** (D-53): 60 bloques por espécimen con la conducta mayoritaria; al abrir uno se ven sus cinco segundos; los que no se vieron bien van rayados. La regla está en `bloqueDe()` de `lib/fst.js`.
28. **Revisión segundo a segundo** (R-14, §9.7) en `/experimentos/:clave/analisis/:id/revision` (`pages/RevisionPage.jsx`), con tema oscuro solo en esa pantalla (`.tema-oscuro` en `styles/app.css`). Un espécimen a la vez: video (o, sin backend, una escena simulada) con el tubo destacado por `cajas.json`, lupa de 40 s, línea de 300 s por espécimen, botones y teclas 1–4 y 0 (Dudosa), velocidades 0.5×–2×, Espacio, flechas, R, Tab, Ctrl+Z y Ctrl+S. Corriendo, la tecla cambia desde 0.4 s antes hasta el siguiente cambio de color; en pausa, solo ese segundo. «Visto» = lo que el cabezal dejó atrás a 2× o menos; solo eso se guarda (`confirmada`, `corregida` o `dudosa`, D-80); lo pintado sin verlo se descarta. Borrador en el navegador, Resumen, Guardar y «Reiniciar el análisis» (con confirmación). Solo de un análisis completado y para quien subió el video o un Administrador; se abre desde Resultados y Grupo. Resultados marca «Con revisión humana» y recalcula los totales con los segundos revisados.
29. **Administración según §9.6:** filtros por rol y estado, columna Experimentos (`n_experimentos` en `GET /admin/users`), espacio libre, tiempo estimado de la cola (10 min por video) y acceso a todos los experimentos. Se corrigió el aviso de disco (al pasar del 90 % sí se borran los videos más antiguos) y el catálogo ya no muestra duraciones mínimas (RF-18): son las cuatro conductas.
30. **Experimentos paginados** de 10 en 10, como el boceto del dashboard.
31. **Nuevo experimento solo pide nombre, fecha y notas** (§7 y §9.3): se quitó el campo «especie», que además traía «Rata Wistar» (§9.8 prohíbe «rata»). El nombre es único en todo el sistema: 409 si se repite.
32. **Los archivos los genera el backend, no el frontend.** Los botones CSV, XLSX y «PDF · reporte de resultados» piden `GET /experiments/:clave/report/:formato` y descargan lo que devuelve el servidor; el PDF de diagnóstico, `GET .../analyses/:id/diagnostic`. Sin backend, cada botón muestra un mensaje con lo que hará. Se quitaron del frontend el armado del CSV, SheetJS (`xlsx`), la impresión del PDF y el PDF de diagnóstico de ejemplo. **Le toca al backend:** generar los tres reportes (columnas `Nado`, `Escalamiento`, `Inmovilidad`, `Conducta activa`; desglose por minuto con `Tiempo (min:seg)`; media, DE y varianza por grupo; el PDF marca los fragmentos «no se ve») y regenerarlos si hay un análisis más nuevo.
33. **Tampoco simula el trabajo del worker ni calcula lo del backend.** Se quitaron `mocks/simulador.js` (la cola que avanzaba sola, los errores, la pausa y la cancelación por plazo) y `mocks/imagenPausa.js`. Sin backend, los datos de ejemplo quedan fijos: 501 procesando, 503 en cola, 502 en pausa y 498 con error. Ajustar o cancelar la pausa sí responde como lo haría el servidor. Donde falta algo que produce el backend, la pantalla lo explica: imagen fija de la pausa (`pausa.jpg`), video de la revisión (`web.mp4`), PDF de diagnóstico y bloques de 5 s.
    - **Le toca al backend:** que el worker avance la cola y escriba estados, etapas, errores y pausas (y cancele las pausas a los 5 minutos); guardar y entregar `pausa.jpg`, `web.mp4` y `diagnostico.pdf`; y calcular los bloques de 5 s con esta regla (D-53): gana la conducta con más de los cinco segundos; si empatan nado activo y escalamiento, conducta activa; si empata inmovilidad con una activa, conducta activa; los cinco sin ver, conducta activa con matiz (`matiz: true` también si se decidió con algún segundo sin ver).
34. **Resultados de una tanda o del grupo completo** (RF-19, D-93, maquetas del 14-oct). Grupo tiene «Ver resultados del grupo completo» con la nota «Reúne las tandas con análisis completado: X de Y», y cada tanda, «Ver resultados de esta tanda». En Resultados, el selector «Ver: Tanda A · Tanda B · Grupo completo» (`?grupo=G-02` sin `tanda` es el grupo completo): cada espécimen con su tanda, y la media, la DE y la varianza sobre lo que se ve; las tandas sin análisis completado se señalan como faltantes (RN-12). El CSV y el XLSX piden el reporte de esa vista (`?grupo=` o `?grupo=&tanda=`). También: el Día 1 dice «Guardado»; una tanda con solo Día 1 queda «En espera del Día 2» con «Cargar el video del Día 2 →»; se quitó el modelo de Administración y del encabezado de Resultados.
35. **Decisiones D-90 a D-102 del repo de LaTeX (14-oct), lo que toca al frontend:**
    - **D-52** (sin `INTERVALO` ni `PRESENTA`): los totales y el desglose por minuto los calcula el backend desde `SEGUNDO`; guardar correcciones solo actualiza `SEGUNDO`. Para el frontend no cambia la forma de las respuestas; solo el texto tras guardar en la revisión.
    - **D-78**: la red solo entrega la conducta, y el usuario debe saber que no es 100 % exacta. Se agregó ese aviso en Resultados, junto a «Revisar segundo a segundo» (el documento lo deja «sin aplicar» en pantallas; se puede mover o quitar).
    - **D-99 (M-10)**: el CSV y el XLSX llevan `Grupo`, `Tanda`, `Espécimen`, `Tiempo (min:seg)`, `Nado`, `Escalamiento`, `Inmovilidad` y `Conducta activa`, una fila por espécimen y por minuto, y al final media, DE y varianza. **Le toca al backend.**
    - **D-100** (maquetas al día): leyenda «En espera del Día 2» en Experimento; en Progreso, la pausa dice en qué segundo fue y el cancelado aclara que no se guardó ningún resultado; la etiqueta «Cancelado» va en guinda oscuro; la pausa explica qué pasa si vencen los 5 minutos y quién puede atenderla.
    - D-90 y D-93 ya estaban aplicados (punto 34). D-102 (`CONDUCTA` como dominio cerrado) no cambia nada aquí: los cuatro nombres son los mismos. D-91, D-94, D-95, D-97, D-98 y D-101 son del documento o del clasificador.
36. **Cinco ajustes más del documento:**
    - **Un video por sesión en cada tanda:** Cargar video avisa y no deja guardar si la tanda ya tiene el video de ese día; el servicio responde 409 (§6.1).
    - **Tamaño máximo** (`MAX_UPLOAD_MB`, 2048 por defecto; `VITE_MAX_UPLOAD_MB` en el frontend): se avisa antes de subir; el 413 del servidor también se muestra.
    - **Progreso, «Completados recientemente»** (§9.4): cada análisis completado con «Ver resultados» y, para quien subió el video o un Administrador, «Revisar segundo a segundo». `GET /queue` trae `terminados` (los 5 más recientes, con `job_id` = id del análisis, `clave`, `gid`, `tanda`, `subido_por` y `analisis_fecha`).
    - **La sesión dura:** el token y la cuenta se guardan en `localStorage` (sobreviven a cerrar la pestaña), como el JWT de una semana del documento (§11.1). «Cerrar sesión» los borra; un 401 lleva al login.
    - **Mensajes de error:** el frontend lee `mensaje` (como responde tu backend de Spring), luego la lista `errores` y por último `error`. Así no se muestra el detalle técnico de la base. Conviene acordar uno solo.
37. **Calidad:**
    - **Versiones nuevas:** React Router 7, Vite 8, Vitest 5 y jsdom 29. `npm audit` pasa de 8 avisos (2 críticos) a 0. Los Dockerfile usan Node 22 (Vite 8 pide 20.19 o más). El contenedor de producción se probó: compila y responde.
    - **GitHub Actions** (`.github/workflows/frontend.yml`): en cada push o PR que toque `frontend/` corre lint, pruebas y build. Una prueba intermitente (esperaba la lista antes de que cargara) se corrigió.
    - **Pantallas angostas:** a 1024 px nada se desborda; debajo de 1100 px, el panel lateral de Resultados y de Revisión pasa abajo.
    - **Maquetas** `mockup/v2` copiadas del main de LaTeX (14-oct), con `pausa.html` y `revision.html`.
38. **Versión 0.4.0** y más ajustes:
    - Experimentos sin ninguno muestra «Aún no hay experimentos» con el botón para crear el primero (§9.2).
    - Administración: crear y editar cuenta en un modal (§9.6), con el foco dentro y Escape para cerrar.
    - La interfaz ya no muestra códigos internos (RF-18, RF-31, T-06).
    - Cada pantalla se descarga al abrirla (`React.lazy` en `router.jsx`): la carga inicial baja de ~408 kB a ~282 kB.
    - Accesibilidad: `src/accesibilidad.test.jsx` pasa axe-core por las 12 pantallas (sin problemas); el contenido va en `<main>`.
39. **Lo que faltaba solo del frontend en la checklist (grupo A):**
    - La franja «Modo demo» solo aparece con datos de ejemplo; la versión (`FST v0.4.0`) se ve en el menú de usuario.
    - nginx acepta hasta 2048 MB, igual que `MAX_UPLOAD_MB` (§11.5); `frontend/.env.example` documenta `VITE_USE_MOCKS`, `VITE_API_BASE` y `VITE_MAX_UPLOAD_MB`.
    - Resultados: porcentaje de cada conducta junto a su tiempo, y el desglose por minuto de cualquier espécimen (selector y tabla de segundos por minuto). La respuesta trae `lineas_tiempo` por espécimen (equivale a `results/byminute`).
    - Video borrado (`video_disponible: false`, §11.13): Grupo lo avisa y la Revisión explica que ya no se reproduce; la lista explica el motivo al pasar el mouse por «Videos borrados». La revisión trae `videoDisponible`.
    - Revisión: con `puedeCorregir: false` queda de solo lectura; el borrador se guarda por cuenta y «Cerrar sesión» los borra.
    - Sin jerga técnica en pantalla («pipeline», «worker», «backend»): ahora dice «el sistema» y «modo demo».
    - Cargar video: «+ Agregar otra tanda» y «Terminar» (§9.3).
    - Con el video borrado, «Revisar segundo a segundo» queda en gris con «Video no disponible» (Resultados y Grupo). Los resultados de la tanda traen `video_disponible`.
40. **Propuesta para el documento (falta tu visto bueno): sin video no se corrige.** Revisar es comparar lo que asignó el sistema con lo que se ve en el video; sin video, corregir sería adivinar y el resultado quedaría marcado «Con revisión humana» sin serlo. Los resultados y reportes se conservan igual (R-05).
    - Frontend (ya hecho): con `videoDisponible: false` la revisión es de solo lectura (sin «Guardar correcciones» ni «Reiniciar el análisis…») y explica por qué.
    - Backend (propuesto): `PUT /experiments/:clave/analyses/:id/segundos` y `POST …/restart` responden **409** si el video del análisis ya se borró (`VIDEO.archivo` nulo). Reiniciar también lo necesita: vuelve a correr el análisis sobre el video. Así lo hace ya el modo demo.
    - Si te parece, conviene anotarlo como decisión nueva en §6 de `preguntas-doctor.md`.

### Diferencias visibles con tu v2 (a propósito)

- Las sugerencias de Cargar video dicen «Referencia · Fluoxetina 10 mg/kg» (nombre + tratamiento real) en vez de «Referencia · fluoxetina».
- Referencia muestra «tandas cargadas: A, B» y precarga la C, para coincidir con el detalle del experimento.
- El CSV exportado se llama `resultados_EXP-2026-02_referencia_tandaA_dia2.csv` (con la clave del experimento).
- Lo leído/no leído de las notificaciones ya no se guarda en el navegador; lo maneja el servicio.

---

## 8. Decisiones tomadas

| Tema | Decisión |
|---|---|
| Término para los sujetos | **«Espécimen 1»** dentro de una tanda; **«Tanda A · Espécimen 1»** al juntar tandas (D-55 de `INSTRUCCIONES-WEB.md`, que reemplaza a «Cilindro P1»); nunca «ratas». |
| Contraseña temporal | Se muestra una sola vez al crear la cuenta. Sin caducidad por ahora. |
| Dónde guardar el token | `localStorage`: la sesión sobrevive a cerrar la pestaña, como el JWT de una semana del documento (§11.1). |
| Revisión segundo a segundo | Va en este frontend, construida al final (§12 del documento). Mientras, se usa `tools/fst_labeler`. |
| Eliminar experimento (D-63) | Solo confirmación («¿Eliminar «…»?»), sin escribir el nombre ni la contraseña. El foco empieza en «Cancelar». Aplicado en la app y en `mockup/v2/experimento.html`. |
| Respuesta 403 | Mensaje «No tienes permiso para esa sección» y regreso a Experimentos. |
| Backend | Congelado hasta terminar el etiquetador (tu `PARA_VANESA.md` §1). El frontend sigue con datos de ejemplo. |
| Versiones | 0.x hasta que exista el clasificador; 1.0.0 cuando esté integrado. Esta rama: 0.3.0. |

---

## 9. Preguntas abiertas para el backend

**En espera**: se contestan cuando empiece el backend, después del etiquetador y el clasificador. Mientras, el frontend usa las propuestas de cada pregunta.

1. **Contraseña temporal en el 201**: ¿en qué campo? El frontend supone `password_temporal`.
2. **Restablecer una temporal perdida**: el frontend ya lo tiene con la propuesta `POST /admin/users/:id/reset-temporal` (devuelve otra temporal y vuelve a poner `cambioRequerido = true`). Falta que el backend lo confirme.
3. **Duración de la sesión**: si no debe sobrevivir al cerrar la pestaña, se queda `sessionStorage` y conviene bajar `JWT_EXPIRES_HOURS` (168 h hoy) a algo como 12 h. Si debe sobrevivir, cookie HttpOnly (Set-Cookie, SameSite, CORS con credenciales, CSRF, y logout que borre la cookie).
4. **Nombres de campos**: el documento dice `cambioRequerido`; el frontend usa `must_change_password`, `role`, `email`, `is_active`. Con la lista real de `/auth/login` y `/admin/users` se alinean los datos de ejemplo de una vez.
5. **Recuperación**: ¿la ruta del enlace es `/restablecer?token=…` y el enlace vencido responde `400 { error }`?
6. **Editar cuenta (§9.6)**: el frontend ya lo tiene con `PATCH /admin/users/:id` y los campos nombre, apellidos, correo, identificador y rol. Falta que el backend confirme campos y ruta.

---

## 10. Pendiente en el frontend

- **Quitar los códigos de trazabilidad** de la interfaz (RN-06, T-06, RF-08, RF-31): lo hace tu rama `feat/sin-codigos-cilindro` (ver sección 12).
- **PDF de diagnóstico desde el servidor** con reintento «preparando…» (hoy se arma en el navegador).
- **Revisión segundo a segundo** (§9.7): al final; necesita `GET .../review`, `GET .../video` con Range y `PUT .../segundos`. El borrador local debe guardarse por usuario y borrarse al cerrar sesión.
- **Actualizaciones mayores pendientes**: react-router 7, Vitest 5 y Vite 8 (avisos de seguridad que no afectan a la app publicada; requieren revisar cambios incompatibles).
- **Revisión automática en GitHub (CI)** y **pantallas angostas**: propuestas, pendientes de acordar.
- **Conectar al backend**: cuando existan los endpoints, probar cada pantalla con `VITE_USE_MOCKS=false` y ajustar nombres de campos.

---

## 11. Respuesta a tu `PARA_VANESA.md`

- **Backend congelado hasta terminar el etiquetador**: de acuerdo. Todo lo nuevo de esta rama es interfaz con datos de ejemplo; el contrato de la sección 6 y las preguntas de la sección 9 siguen siendo propuesta.
- **Versión**: ya está en 0.3.0 (`frontend/package.json`).
- **Orden de entrada**: de acuerdo con que esta rama entre primero a `main`. Avísame cuando la revises para abrir el PR.
- **Tu rama `feat/sin-codigos-cilindro`**: ver la sección 12.

---

## 12. Propuesta para `feat/sin-codigos-cilindro`

Tu rama hace dos cosas; propongo tratarlas por separado:

1. **Quitar los códigos de trazabilidad** (RN-06, T-06, RF-08, RF-31): de acuerdo, es tuyo y yo no los toco en esta rama para no chocar. Hoy siguen en Experimentos (aviso RN-06), Admin (T-06), Cargar video (RF-08) y Resultados (RF-31).
2. **Nombre de los sujetos**: **de acuerdo con tu propuesta: «Cilindro P1»**. Ya está aplicado en esta rama con tus mismos textos («Cilindros P1–P4», «Cilindros de la tanda», «Cilindro P1», «Por cilindro», columna «Cilindro» en el CSV). Al rehacer tu rama solo te queda quitar los códigos.

Orden sugerido:

1. Esta rama entra a `main` por PR.
2. Rehaces `feat/sin-codigos-cilindro` encima del `main` nuevo, solo con lo de los códigos, y le subes la versión a **0.3.1**.
3. Si al rehacerla hay conflictos en Experimento, Grupo, Resultados o los datos de ejemplo, gana lo de esta rama: ya tiene «Cilindro PN» y los datos con forma de API (`data/mock.js` ya no existe; los datos están en `services/mocks/data.js`).

---

## 13. Instrucciones para Claude Code

Si eres Claude trabajando en este repo:

- Responde y escribe en español. No modifiques `main` por ningún motivo; trabaja en la rama que te indiquen y no abras PR hacia `main` sin que lo pidan.
- Antes de tocar una pantalla, lee su mockup en `mockup/v2/` y la página en `frontend/src/pages/`. Respeta las convenciones de la sección 5: datos solo desde `services/`, componentes de `components/ui.jsx`, textos con la terminología de la sección 1.
- Si un dato o endpoint no existe en el backend, agrégalo primero a `services/mocks/data.js` con forma de API y documenta la ruta propuesta en el comentario de la función de servicio y en la tabla de la sección 6 de este archivo.
- Verifica cada cambio: `npm test`, `npm run lint` y build sin errores, prueba en el navegador y consola limpia. Para refactors, compara el HTML antes y después.
- Si cambias algo de este traspaso (contrato, decisiones, pendientes), actualiza este archivo en el mismo commit.

## Integración con Spring (siguiendo `PARA_VANESA_API.md`)

41. **Envoltorio y equivalencias (pasos 2 y 3).** `services/api.js` desenvuelve `{data, error, status}` en el interceptor (los archivos no se tocan) y `mensajeError` lee solo `error`. `src/lib/contrato.js` tiene las tablas de tu §7 en los dos sentidos (`ESTADO.aFront['en cola']` → `QUEUED`), más la letra de la tanda ↔ `ordinal`.
42. **Cuentas en el formato de la guía (pasos 1 y 4, parte 1 del orden de conexión).** Los datos de ejemplo de las cuentas ya tienen la forma de §6.1 (`idInstitucional`, `correo`, `rol: "Investigador" | "Administrador"`, `activo`, `cambioRequerido`, `nExperimentos`), y los servicios usan la misma traducción con o sin backend. `idInstitucional` es el id de la cuenta en todo el frontend (`creador_id` y `subido_por` de los datos de ejemplo también).
    - `POST /auth/login` `{correo, contrasena}` → `{token, rol, nombre, cambioRequerido, idInstitucional, apellidos, correo}`.
    - `POST /auth/recover` `{correo}`; `POST /auth/reset` `{token, contrasenaNueva}`.
    - `PATCH /auth/password` `{contrasenaActual, contrasenaNueva}` y `PATCH /users/me` `{nombre, apellidos, correo}`: **la guía no da los nombres del cuerpo ni la respuesta.** Propongo esos y que los dos devuelvan la cuenta como el login, sin `token`. Confírmalos.
    - `/admin/users`: `GET` con la lista de §6.1; `POST` `{idInstitucional, nombre, apellidos, correo}` → `{passwordTemporal}`; `PATCH /admin/users/{idInstitucional}` con `{nombre, apellidos, correo, idInstitucional, rol}` o `{activo}` → la cuenta; `POST .../reset-temporal` → `{passwordTemporal}`.
    - **Listo para probar contra el backend:** inicio de sesión, primer acceso, perfil y recuperar contraseña.
43. **Lista de experimentos en el formato de la guía (parte 2 del orden de conexión, primera mitad).** `GET /experiments?pagina=` con `{pagina, total, items}`; el servicio pide todas las páginas y las junta, así que los filtros, la búsqueda y las páginas de 10 siguen en el navegador y la pantalla no cambia. Crear y eliminar también usan las formas de la guía.
    - Cada `item` trae lo de §6.1 (`idExperimento`, `nombre`, `fecha`, `nGrupos`, `estado`, `avisoVencimiento`) y el `creador` de tu §4.
    - **Campos de más que pide la maqueta de la lista y la guía no trae (propuesta, confírmalos):** `nEspecimenes`; `tratamientos` (los de sus grupos, para el filtro); `videosDia2: {analizados, cargados, total}` (columna «Día 2 analizados»); `diasRetencion` (días que le quedan al video crudo, `null` sin videos o ya borrados) y `videosBorrados` (columna «Retención»). Si alguno falta, la pantalla muestra «—» o 0, no se rompe.
    - `POST /experiments` `{nombre, fecha, notas}`: **la guía no dice qué responde.** Propongo `{idExperimento}` (201), que es lo que la pantalla necesita para abrir el experimento nuevo.
    - `idExperimento` ya es la clave de las rutas del frontend; en los datos de ejemplo sigue valiendo `EXP-2026-02` hasta el paso 10. La maqueta muestra esa clave bajo el nombre; con el backend se vería el número.
    - El detalle (`GET /experiments/{id}`) va en la siguiente fase junto con `/status`: la guía no trae el estado de cada análisis en el detalle, así que se completará con `/status`.
44. **Detalle del experimento y del grupo con la guía (parte 2, segunda mitad).** `getExperiment` junta `GET /experiments/{id}` (grupos → tandas → videos, §6.1) y `GET /experiments/{id}/status`: el estado, la etapa y el avance de cada tanda salen del análisis de su video del Día 2. La pantalla de grupo ya no pide `GET .../groups/:gid` (la guía no la tiene): toma el grupo del mismo árbol.
    - **Lo que necesito de `/status`:** que traiga **todos** los análisis vigentes del experimento, también los `completado`, no solo los activos; si no, el detalle no sabe qué tandas terminaron.
    - **Campos de más (propuesta, confírmalos):** en cada video, `fecha` (para «18–19 feb 2026» de la tanda) y `subidoPor` (ya en tu §4); en cada análisis de `/status`, `fechaAnalisis` (para «Análisis vigente: …» y «Completados recientemente»).
    - Sin `fecha` se usa la del experimento; sin `fechaAnalisis` se lee «Sin análisis todavía».
45. **Progreso con `/status`; ya no existe `/queue` (paso 6).** «Análisis en curso» se arma con `GET /experiments/{id}/status`:
    - Con `/analisis?experimento={id}` muestra solo ese experimento (la vista por experimento de §9.4). A ella llevan «Ver progreso del análisis», la subida de un Día 2, la pausa y el reinicio.
    - Sin experimento (el enlace del menú), junta el `/status` de los experimentos que la lista marca `en cola`, `procesando`, `en pausa`, `error` o `cancelado`. «Completados recientemente» sale de esos mismos experimentos.
    - **Orden de la cola:** `/status` no da posición. Uso `fechaAnalisis`, que mientras el análisis no termina es la hora en que entró a la cola (D-79): un análisis ajustado tras una pausa conserva la suya y vuelve por delante de los que llegaron después; uno reiniciado entra con la hora del reinicio. **Confírmame que `fechaAnalisis` viene en `/status` con ese significado.**
    - **Campos de más en `/status` (propuesta):** `pausa.desdeSegundo` (la tarjeta dice «pausa en el segundo 142») y `motivoCancelacion` (`usuario` o `plazo`; §9.4 pide decir por qué se canceló). Sin ellos la tarjeta pierde ese dato, pero no se rompe.
    - **Administración** toma la cola de `GET /admin/system`. Propuesta de forma (no está en §6.1): `{disco, conductas, cola: [{idAnalisis, idExperimento, experimento, grupo, letra, sesion, estado}]}`, en orden de llegada y con el que se procesa primero.
    - La pausa toma su título del `/status` de su experimento.
46. **Subir video con una sola llamada (paso 5, parte 3 del orden de conexión).** `POST /experiments/{id}/groups/{grupo}/videos` (multipart) con `archivo`, `sesion` (`Dia 1` o `Dia 2`), `tipo` y `tratamiento` solo si el grupo es nuevo, `nCilindros` solo si la tanda es nueva y `ordinal` (A = 1). Ya no hay `POST .../groups` aparte. Respuesta `{idVideo, idAnalisis, estadoAnalisis}`.
    - **`{grupo}` va como la etiqueta del grupo** (codificada en la URL), como tú lo veías probable. Si decides otra cosa, se cambia en `services/groups.js`.
    - **Sugerencias:** `GET /experiments/{id}/groups?q=` mientras se escribe. **Forma propuesta** (no está en §6.1): `[{idGrupo, etiqueta, tipo, tratamiento, siguienteOrdinal}]`; busca en la etiqueta y el tratamiento, solo en ese experimento. Sin coincidencias, la pantalla trata el grupo como nuevo (tanda A, pide tipo y tratamiento).
    - **Listo para probar contra el backend:** cargar video (Día 1 y Día 2, grupo nuevo y existente, 409, 413 y 422).
47. **Resultados con las rutas de la guía (parte 5 del orden de conexión).** `GET /experiments/{id}/results`, `/results/byminute`, `/results/groups` y `/results/blocks`, todas del experimento completo; el filtro por grupo y tanda se hace en el navegador (D-93). Las peticiones simultáneas de una misma pantalla se comparten.
    - Cada espécimen trae `tanda` («Control · A»), `ordinal`, `numeroCilindro`, `nivelClasif`, `revisado` y `totales` con `{segundos, porcentaje}` por conducta, como §6.1. **Propongo además `idGrupo`**: con él se filtra sin depender del texto de la etiqueta. Sin él, se compara «etiqueta · letra».
    - `byminute`: cada espécimen con `minutos: [{minuto, "nado activo": s, inmovilidad: s, escalamiento: s, "conducta activa": s}]`.
    - **`/results/groups`, forma propuesta** (la guía dice qué trae, no la forma): `[{idGrupo, etiqueta, tipo, tratamiento, n, nTotal, faltantes: [{letra, nCilindros, estado}], nivelMixto, promedios: {"nado activo": {media, de}, …}, porNivel: [{letra, nivelClasif, n, promedios}] | null}]`.
    - **`/results/blocks`, forma propuesta:** cada espécimen (con `tanda`, `ordinal`, `numeroCilindro`) y `bloques: [{inicio, conducta, matiz, segundos: [5 conductas o null]}]`, con los nombres de la base.
    - **Listo para probar contra el backend:** resultados, comparación entre grupos y bloques (los reportes ya usaban la ruta de la guía).
48. **Notificaciones con los campos de la guía (paso 7).** `GET /notifications` con `{idNotificacion, tipo, mensaje, leido, fechaCreacion, idExperimento}`; el título y el enlace los arma el frontend con `tipo` e `idExperimento`. «Análisis completado» abre los resultados del experimento; error, pausa y cancelado abren su progreso (`/analisis?experimento=`), donde `/status` dice qué análisis fue; «video borrado», el experimento; «alerta de disco», Administración.
    - **`idNotificacion`:** la guía no nombra el id; propongo ese nombre, como `idExperimento`.
    - `PATCH /notifications/{id}` con `{leido}`. **«Marcar todas como leídas» ya no usa `POST /notifications/read-all`:** hace un `PATCH` por cada no leída, así no hace falta una ruta que la guía no tiene.
49. **Video de la revisión con token (paso 8, tu §6).** Con backend, `getRevision` pide `POST /experiments/{id}/analyses/{idAnalisis}/video-token` (con el JWT por header) y pone `<base de la API>{videoUrl}?vt=<token>` en el `<video>`; el JWT nunca va en la URL y la URL no se escribe en consola ni en pantalla. Si el video falla (token vencido), pide otro y sigue en el mismo segundo, a lo más una vez cada 10 s. Respuesta que espero: `{token}`. Falta probar `Range` con un `web.mp4` real.
50. **Interruptor por parte (paso 9).** Con `VITE_USE_MOCKS=true`, las partes que lista `VITE_API_REAL` ya llaman a tu backend y las demás siguen con datos de ejemplo. Partes, en tu orden de §9: `sesion`, `experimentos` (incluye `/status`, porque el detalle no funciona sin él), `videos`, `progreso`, `resultados`, `revision`, `admin` y `notificaciones`. Ejemplo para probar tu parte 1: `VITE_API_REAL=sesion`. Con la sesión real y lo demás de ejemplo, la cuenta de los datos de ejemplo sale de la sesión guardada. Documentado en `frontend/.env.example`.
51. **Ids numéricos (paso 10).** Los datos de ejemplo ya usan números como la base (`idExperimento` 7, `idGrupo` 1 a 4…), y las rutas de React conservan sus nombres (`/experimentos/:clave`, `?grupo=`). Los servicios pasan los ids a texto al traducir, para que coincidan con los de la URL; así da igual si tu backend los manda como número o texto. La pantalla muestra el código del grupo como en la maqueta («G-01»).
52. **Pruebas contra un backend simulado con la guía (`src/services/contrato.test.js`).** Con `VITE_USE_MOCKS=false`, los servicios hablan con un backend falso que responde los ejemplos de §6.1 dentro de `{data, error, status}`: login, lista, detalle con `/status`, resultados, revisión, notificaciones y subida. Así se prueban los caminos reales sin tu backend.
    - Encontró un hueco: **`GET .../review` de la guía no trae experimento, grupo, tanda, duración ni tamaño del video**, y la pantalla los usa. Ahora el frontend los toma del detalle del experimento. **Propongo agregar `duracion`, `ancho` y `alto` a la respuesta de review**, como ya los trae la pausa; sin ellos uso 300 s y 1920 × 1080, y si el video real es de otro tamaño los recuadros quedarían corridos.
53. **Primera prueba contra tu backend real (20-oct, `9e3a874`, con el simulador del worker, retirado ese mismo día).** Funcionan con el front: inicio de sesión (mensaje genérico, R-01), primer acceso, perfil (y su 409), cambio de contraseña (400 si la actual no es correcta), lista, crear, detalle con `/status`, grupo, sugerencias, subida (Día 1, Día 2, 409 y los dos 422), progreso sin recargar, resultados, comparación, desglose por minuto, PDF y notificaciones (con «marcar todas»). Arreglé en el front: fechas con hora y zona (salía «NaN oct 2026»), la duración del video (ya no supone 300 s), los bloques cuando la ruta da 404, «1 tanda» y el responsable con apellidos como «De la Cruz».
    **Lo que vi de tu lado:**
    - **El simulador del worker ya no reconocía «falla» ni «pausa»** (hoy está retirado: solo queda el worker real, que analiza con el modelo): el backend guarda el archivo con otro nombre, así que el simulador no veía la palabra y todo terminaba `completado`.
    - **Eliminar:** responde 409 «tiene grupos» con cualquier experimento con grupos; la guía pide borrar en cascada y solo rechazar con un análisis `procesando` o `en pausa` (D-87).
    - Faltan (el front ya está listo y muestra un aviso mientras): `/admin/*`, `/auth/recover` y `/auth/reset`, la pausa (`/pause`, `/pause/image`, `/pause/adjust`, `/cancel`), `/review`, `/segundos`, `/restart`, el video y su token, `/results/blocks` y los reportes CSV y XLSX.
54. **Tus cambios del frontend (19 y 20-oct) ya están en esta rama.** Traje lo de `frontend/` de `poc/backend-spring` (`e66ce40`) sobre lo nuestro posterior a `e7921e5`: banner BETA y modo desarrollador, progreso por etapa con tiempos, nombre original del video, botón «Segundos», imagen de control, eliminar tanda, los arreglos del ciclo de recargas y de Cargar video, y tus pruebas. No traje `src/data/mock.js` ni `src/hooks/useApi.js`, que son de la fase 0 y ya no se usan. Las 152 pruebas pasan (las 117 nuestras y las 35 tuyas) y la versión queda en 0.25.0, como la tuya. **Para tu versión portable, toma el front de esta rama**; y si vas a cambiar algo del front, mejor aquí, para no tener dos copias que se separen.
    - **Probado contra tu backend `e66ce40`:** la revisión segundo a segundo funciona con el front (carga, video con `?vt=` → 206 con `Range`, y `PUT .../segundos` → 200).
    - **El simulador del worker seguía sin reconocer «falla» ni «pausa»** (ya retirado el 20-oct): D-114 guarda el nombre original, pero miraba el nombre del archivo guardado.

