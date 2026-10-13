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
- **La cola de análisis avanza sola** (`services/mocks/simulador.js`): 8 s por etapa, un trabajo a la vez; al terminar, la tanda queda Completada (o con Error, uno de cada tres) y llega la notificación. No usa temporizadores: avanza según el tiempo transcurrido en cada lectura.
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
| `POST /experiments` | `{ titulo, fecha_inicio, especie, notas }` | `{ clave }` (el responsable sale de la sesión) |
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
| `GET /experiments/:clave/analyses/:id/review` | — | `{ puedeCorregir, videoUrl, duracion, ancho, alto, especimenes: [{ numeroCilindro, recuadro, segundos: [{ segundo, conducta, origen }] }] }` (§6.1; conductas con su nombre de la base); 403 sin permiso, 409 si no está completado |
| `PUT /experiments/:clave/analyses/:id/segundos` | `{ especimenes: [{ numeroCilindro, segundos: [{ segundo, conducta, origen }] }] }` solo lo que cambió | totales recalculados |
| `POST /experiments/:clave/analyses/:id/restart` | — | 202; borra el resultado al empezar y deja el análisis en cola |
| `GET /notifications` · `PATCH /notifications/:id` · `POST /notifications/read-all` | `{ is_read }` | — |

**Valores que usa el frontend:** estado de tanda `QUEUED | RUNNING | DONE | FAILED | SIN_VIDEO` (con `FAILED`, `error: { etapa }`); `dia1: { cargado: true } | null`; etapa `PREPROCESSING | ROI_DETECTION | CLASSIFICATION` (33, 67 y 100 %); estado de experimento `SIN_VIDEOS | ESPERA_DIA2 | QUEUED | RUNNING | PAUSED | DONE | FAILED | CANCELED`; tipo de grupo `CONTROL | REFERENCIA | EXPERIMENTAL`; rol `INVESTIGADOR | ADMIN`; día `DAY1 | DAY2`. Los primeros siguen a `backend/app/models.py`.

**Sesión:** el token va en `sessionStorage` (`fst.token`) y se manda como `Authorization: Bearer`. Cerrar sesión es solo del frontend (borra el token). 401 con sesión → login con «Tu sesión caducó»; el 401 del propio login muestra «Correo o contraseña incorrectos» sin redirigir. 403 → Experimentos con «No tienes permiso para esa sección».

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
21. **Contenedor de producción** (propuesta, sección 3): `Dockerfile.prod` + nginx + `docker-compose.prod.yml`; fuente y XLSX incluidos en la app.
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
| Dónde guardar el token | `sessionStorage` (falta decidir si la sesión debe sobrevivir al cerrar la pestaña). |
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
