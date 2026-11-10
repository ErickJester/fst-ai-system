# 09 · Resumen para el capítulo 7 (Pruebas y resultados)

Preparado por orquestador_web el 9-nov-2026 para orquestador_latex. Solo lectura: nada de esto edita el documento ni el sistema. Fuentes: `08_pruebas_programador.md`, `08_pruebas_pantallas.md`, `08_pruebas_worker_bd.md`, `06_pruebas.md`, `backend-spring/CHECKLIST.md` y `frontend/CHECKLIST.md`.

## 1. Cifras por componente

«Corrida» = la ejecuté el 9-nov en esta máquina. «Conteo» = leído del código, sin ejecutar.

| Componente | Cifra | Cómo se obtuvo |
|---|---|---|
| Backend (JUnit 5), con base PostgreSQL de pruebas | **268** pruebas: 268 corren, **todas pasan**, 0 fallos, 1 omitida | Corrida. La omitida solo genera un PDF de ejemplo y se activa con una variable |
| Backend, sin base | **95** corren y pasan; **173** se omiten (las de integración con la base) | Corrida (la cifra sale de restar: 268 − 173 omitidas) |
| Interfaz (Vitest), 29 archivos | **348** pruebas, **todas pasan** | Corrida (`npm test`). Lint y compilación sin errores |
| Worker (unittest), 8 archivos | **93** pruebas | **Conteo estático.** Corren dentro de Docker y no las corrí. Solo hubo una corrida parcial de `programador` con la base simulada (87 pasaron, 6 omitidas), que no cuenta como prueba real |

Dos versiones: la **publicada** (HEAD `ba903f4`, v1.9.2) tiene 206 pruebas de backend (122 con base, 84 sin) y 331 de interfaz. Las cifras de arriba incluyen RN-18 (aviso de privacidad), las gráficas del PDF y las pruebas nuevas del 9-nov (crear, eliminar y subir; plazo de recuperación; restricciones de la base; cerrar sesión), que todavía no tienen commit. Hay que elegir con cuál versión se escribe y decirlo.

Nota: `npm test` imprime un aviso de jsdom («Not implemented: navigation to another Document»), que no hace fallar nada. No sé qué prueba lo genera.

## 2. Áreas de prueba

**Aviso:** los conteos y marcas de esta tabla son anteriores a las pruebas nuevas del 9-nov (+49 de backend: 16 de crear, eliminar y subir; 4 del plazo de recuperación; 29 de restricciones de la base; y +4 de interfaz: cerrar sesión). Las marcas vigentes de RF y RNF están en la sección 3, y lo comprobado contra la base, en la sección 5. En particular, el área 2 suma 16 pruebas de backend y el área 8 suma 29; el área 1 gana la del plazo de 60 minutos.

Las áreas se solapan (una prueba puede cubrir dos), así que **los conteos por área no se suman**; los totales verdaderos son los de la sección 1. «BD» = necesita `FST_TEST_DB_URL`. Marcas: ✔ lo comprueba una prueba concreta; ◐ comprueba solo una parte.

| Área | Pruebas | Qué comprueban | RF | RNF | CU |
|---|---|---|---|---|---|
| 1. Cuentas y sesión | backend 43 (38 BD, 5 sin base); interfaz 46 | Login y 401, cuentas desactivadas, primer acceso con aceptación de términos, recuperar con enlace (el correo dice «60 minutos»), alta, edición y baja de cuentas por el Administrador, regla del último Administrador, 403 a un investigador en `/admin`, 409 por correo repetido, lista y filtros de cuentas | ✔ 03, 04, 05, 06, 28 · ◐ 01, 02, 07, 29 | ✔ 04, 05 | ✔ 03, 11, 12, 15, 16, 17, 18 · ◐ 01, 02, 13 |
| 2. Experimentos y carga de video | interfaz 57; backend 17 (BD) | Lista y filtros por estado, avisos de vencimiento a 7 días, 400 y 401, eliminar solo el creador o un Administrador con confirmación, subir video (extensión, Día 1 sin análisis, varias tandas), tanda fuera de A–Z rechazada | ✔ 24 · ◐ 08, 09, 10, 11, 12, 23, 27, 30 | ✔ 04 | ✔ 22, 23 · ◐ 04, 05, 19, 20, 24 |
| 3. Worker y pipeline | worker 72 (conteo); backend 28 (14 BD); interfaz ≈27 | Progreso (nunca llega a 1 ni baja), pausa a los tres segundos, cancelación, reinicio, diagnóstico en PDF, tiempos, modelo, tareas programadas | ◐ 15, 16, 29 | ◐ 08, 09 | ✔ 07, 08 · ◐ 13 |
| 4. Resultados y reportes | backend 109 (33 BD, 76 sin base); interfaz 127 | Totales y minutos con las cinco claves (suman 100 %), comparación por grupos con niveles mezclados, bloques de 5 s, gráficas y segundo a segundo, varianza muestral n − 1, CSV y XLSX (encabezado D-99, estadísticos), Excel del experimento, PDF (parte sin publicar) | ✔ 19, 20, 21, 22, 31 · ◐ 18 | | ✔ 09, 10, 27 |
| 5. Revisión segundo a segundo | backend 29 (BD); interfaz 13 | Corregir segundos (solo los que llegan), recálculo del nivel agrupado o preciso, 403 a otra cuenta, video por partes con permiso temporal (una URL con la sesión se rechaza), reinicio del análisis | ✔ 32 | | ✔ 25, 26 |
| 6. Notificaciones y correo | worker 16; backend 16 (BD); interfaz ≈14 | El worker deja el correo pendiente con el asunto del tipo, contador de no leídas, correo de cuenta nueva y de recuperación (simulado: no sale correo real) | ✔ 33 | | (ninguno propio) |
| 7. Accesibilidad y pantallas | interfaz 151 (80 de pantallas); backend 0 | axe-core sin fallos en 15 pantallas completas (login, experimentos, nuevo experimento, experimento, grupo, cargar video, análisis en curso, pausa, resultados, revisión, perfil, administración, resumen del experimento, aviso de privacidad y términos); pantallas completas con datos de ejemplo; contratos de los servicios | ✔ 01, 03, 05, 06, 19, 28, 33 · ◐ 02, 21, 32 | ✔ 04, 05 | ✔ 03, 09, 11, 12, 16, 25, 26, 27 · ◐ 01, 02, 07, 18 |
| 8. Base de datos (PostgreSQL real) | backend 122 (BD, 11 archivos); worker 5 simulados | Ver sección 5 | | ✔ 05 | |

Qué **no** cubre axe: el primer acceso, restablecer contraseña, las vistas segundo a segundo, minuto a minuto, cada 5 segundos y comparación, los diálogos y las notificaciones. Además, en las pruebas de las gráficas la regla de contraste de color está desactivada (jsdom no puede calcularla): el contraste no se verifica automáticamente.

## 3. RF y RNF sin prueba automática, o con una parcial

**RF con al menos una prueba: 28 de 33.**
- **Comprobados (19):** RF-02, 03, 04, 05, 06, 08, 10, 11, 12, 19, 20, 21, 22, 24, 27, 28, 31, 32, 33.
- **Parciales o solo con datos simulados (9):** RF-01 (falta el mensaje sin revelar el campo), 07 (no comprueba que no haya registro público), 09 (el Día 1 sin análisis y un video por sesión sí; «cada tanda al menos un video» no), 15 (la etapa activa no se verifica), 16, 18 (conductas cortas), 23 (las columnas no son las del texto), 29 (falta la alerta al 80 % y el tiempo estimado), 30 (el Administrador elimina; falta lo demás que gestiona).
- **Sin ninguna prueba automática (5):** RF-13 y RF-14 (las tres etapas del pipeline y la detección de cilindros), RF-17 (la regla de clasificación de conducta; el etiquetado de entrenamiento está fuera del repositorio), RF-25 (borrado automático a los 30 días) y RF-26 (conservar resultados). Los cinco se comprobaron **por inspección del código**, y la inspección coincide con el texto (`verificacion_pantallas_rf.md`).

**RNF (14):**
- **Comprobados por prueba:** RNF-04 (solo usuarios autenticados) y RNF-05 (contraseña con hash; temporal obligatoria de cambiar).
- **Parciales:** RNF-08 (solo el aviso a 7 días; el borrado a 30 días no tiene prueba) y RNF-09 (solo quién recibe la alerta de disco).
- **Medidos con la prueba de carga, fuera de la suite:** RNF-01 (3 s) y RNF-13 (4 usuarios simultáneos); ver sección 4.
- **Pendientes del clasificador:** RNF-02 (F1 de al menos 85 %, sin medición todavía) y RNF-14 (repetibilidad de 5 a 10 %, sin mediciones).
- **Sin prueba, por inspección o a mano:** RNF-03 (análisis de noche sin intervención; la cola secuencial existe, sin prueba de larga duración), RNF-06 (9 de cada 10 personas completan el flujo sin ayuda; es una prueba con personas y **no está hecha**), RNF-07 (Chrome, Firefox y Edge; no hay registro de pruebas en los tres), RNF-10 (empaquetado con Docker Compose coincide; sin registro de verificación en dos equipos distintos, aunque el sistema está desplegado en Hostinger), RNF-11 (módulos separados en carpetas; sin revisión de arquitectura registrada) y RNF-12 (ESLint sí; PEP 8 no, no hay linter de Python).

## 4. Prueba de carga con cuatro sesiones

Existe y la hizo `tools/carga_4_sesiones.py` (solo biblioteca estándar; el comentario inicial explica cómo correrla). Evidencia: `backend-spring/CHECKLIST.md` líneas 240 y 242. **Corrigo mi `verificacion_bd_desarrollo.md`, que decía que no había mediciones:** sí las hay.

- **Corrida A (encargo 47, 28-oct según el checklist):** 4 clientes con su propia sesión, 25 rondas sincronizadas, base desechable con 1 experimento de 48 especímenes y 14 400 segundos. Rutas: login, lista, detalle, estado, resultados, minutos, bloques, grupos, CSV y XLSX. Todo respondió 200. Mayor p95: 0.086 s (`/results/blocks`). En frío: CSV máx 0.155 s, XLSX máx 0.229 s. Máximo de toda la sesión: 0.888 s (primera ronda, con la JVM sin calentar). Sin nginx ni red de por medio.
- **Corrida B (encargo 46):** contra el compose de producción con nginx, 34 experimentos y uno completo con 2 especímenes de 5 minutos, 4 cuentas distintas, 120 s en bucle: **1474 peticiones, sin errores, p95 de todas 0.041 s, máximo 0.962 s** (la primera generación del XLSX). Medianas de 0.022 a 0.026 s por ruta.
- **Resultado:** se cumple RNF-01 (3 s) y RNF-13, con mucho margen.
- **Límites a citar:** se hizo con el worker quieto (con el worker procesando un video real **no** se hizo, por memoria: 15.7 GB frente a hasta 3.5 GB del análisis); no incluye la subida de videos ni la primera generación del PDF (0.59 s en una corrida anterior); solo hay mediana, p95 y máximo, **no promedio**.
- **Inconsistencias de fechas:** los checklists dicen 20 y 28-oct, el código de la corrida B era la v0.91.1 (anterior a la renumeración a 1.0.0) y el commit de producción es del 21-oct. No afirmar una fecha exacta sin confirmarla con el usuario. El checklist de la interfaz (§13) cita otra medición, con 80 peticiones y p95 de 0.03 s «con un análisis procesando»; el del backend aclara que con el worker procesando un video real no se hizo, así que ese «procesando» fue un estado sembrado.

## 5. Qué se verificó contra PostgreSQL real

Las pruebas de integración levantan la aplicación completa contra una base **vacía** creada desde `db/init/001_esquema.sql` y usan SQL directo para preparar y comprobar. Como `ddl-auto=validate`, cada arranque comprueba que las entidades coinciden con las tablas.

**Comprobado de verdad:**
- **Restricciones de la base, intentando violarlas** (29 pruebas de `RestriccionesDeLaBaseTest`, todas pasan): los CHECK de `ncilindros` (2 a 4), `ordinal`, `numerocilindro` (1 a 4), `segundo` (1 a 300), `duracion` y `rutadiagnostico` (solo con estado «error»); los dominios `tipo_grupo`, `estado_analisis` y `tipo_conducta`; las únicas (correo, orden de tanda por grupo, sesión por tanda, número de cilindro por tanda, nombre de experimento, un análisis por video); y las llaves foráneas con NO ACTION (no se borra un usuario, experimento, grupo, tanda ni video mientras tenga hijos). Cada prueba comprueba que la base rechaza el dato malo y que la fila no queda.
- Unicidad del identificador institucional y del correo (409), crear con nombre repetido (409) y eliminar sin dejar grupos, tandas ni notificaciones.
- Los candados de cuenta (`FOR UPDATE`, `FOR NO KEY UPDATE`) y `lock_timeout`: un cambio de perfil y una notificación del worker a la vez no se traban ni pierden datos.
- La regla del último Administrador, también bajo concurrencia (solo pasa una de dos desactivaciones a la vez).
- El reinicio de un análisis, también concurrente (de dos solo pasa uno), con resultados, reportes y archivos viejos borrados.
- El contador de notificaciones no leídas y el aviso de disco.
- Que el esquema y las entidades JPA coinciden (`ddl-auto=validate`).

**Sin prueba que lo verifique:** `ON UPDATE NO ACTION`, los valores por defecto, los cinco índices y las migraciones. La prueba de CHECK y llaves foráneas depende de que Spring traduzca los errores de la base (SQLState 23xxx) a `DataIntegrityViolationException`.

## 6. Cosas que el capítulo debe decir con cuidado

- **Las pruebas de crear, eliminar y subir videos usan un `ffprobe` simulado:** no se probó con un video real. Cubren permisos, 409, 403 y 422; no cubren un video que `ffprobe` no pueda leer, el tipo de grupo inválido en la subida ni que se borren los archivos de video al eliminar.
- **Cerrar sesión:** la prueba comprueba que el token se borra del navegador y que la ruta protegida vuelve al login. La cabecera de autorización se comprueba llamando al interceptor, no con una petición real, porque el modo de ejemplo no hace peticiones.
- **Plazo de recuperación:** se comprueba que el enlace dura lo que dice `app.reset.minutos` (60) y que a los 59 minutos sirve y a los 62 no; no se prueba el instante exacto de los 60. Los enlaces vencidos se firman con las mismas reglas del servicio y la fecha adelantada, sin esperar una hora.
- La mayoría de las pruebas de la interfaz usan la capa de datos de ejemplo, así que no prueban el backend real; es el diseño de esa suite.
- Las pruebas del worker corren dentro de Docker: no hay una corrida reciente confirmada.
- La exactitud del clasificador no está medida todavía; RNF-02 y RNF-14 quedan en blanco por eso.
- Las pruebas que generan el PDF con gráficas (M1B), las de RN-18 y todas las nuevas del 9-nov están en el árbol de trabajo sin publicar.

## 7. Cómo se contó (para quien revise)

Backend: anotaciones `@Test` (no hay pruebas parametrizadas) y corrida de `./mvnw -o test`. Interfaz: corrida de `npm test` (Vitest). Worker: `def test_` en los archivos de `worker/`. Los RF, RNF y CU se marcan solo si una prueba concreta, leída por uno de mis tres programadores, comprueba el requisito; el nombre de esa prueba está en los tres archivos `08_*`.

## 8. Clasificación por lo que usa cada prueba (decisión del usuario, 9-nov)

El usuario pidió separar, en el documento, lo que se prueba **con el sistema real** de lo que se prueba con datos de ejemplo o con piezas simuladas. No se borra ni se oculta nada: las pruebas de interfaz siguen existiendo y se describen como lo que son. Los números son los reales de las corridas (backend 268, interfaz 348, worker 93; total 709).

| Grupo | Qué usa | Pruebas | Corridas el 9-nov |
|---|---|---|---|
| **A. Con el sistema real** | La aplicación completa (backend) contra una base PostgreSQL real | **172** (123 anteriores + 49 nuevas: crear, eliminar y subir, plazo de recuperación, restricciones de la base) | Sí, todas pasan |
| **B. Con el sistema desplegado** | API, base y nginx reales, con cuatro sesiones | **2 corridas** de la prueba de carga (no son pruebas automáticas de la suite) | Evidencia de los checklists, sin repetir |
| **C. Lógica del backend sin base** | Funciones y clases aisladas, sin base de datos (bloques, estadísticos, CSV, XLSX, PDF, ajuste del recuadro, modelo) | **95** | Sí, todas pasan (más 1 que solo corre con una variable) |
| **D. Interfaz con datos de ejemplo** | La aplicación sin backend: capa de datos de ejemplo, contrato simulado o componente aislado; incluye axe-core en 15 pantallas | **348** | Sí, todas pasan |
| **E. Worker con piezas simuladas** | Base, correo, `pg_dump` y modelo simulados; corren dentro de Docker | **93** | No: conteo estático |

Suma: 172 + 95 + 1 + 348 + 93 = **709** (el 1 es la prueba de PDF de ejemplo, que solo corre con una variable).

**Lo que el documento debe decir con claridad:**
- **No hay ninguna prueba automática de punta a punta** (navegador, nginx, API, base y worker juntos). El sistema completo se verifica con la prueba de carga y con el despliegue en Hostinger, que son manuales.
- Las pruebas de interfaz (grupo D) verifican la interfaz, no el backend real; el backend se verifica por separado (grupo A).
- Las del worker (grupo E) no pasan por PostgreSQL real.
