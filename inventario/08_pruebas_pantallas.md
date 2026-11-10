# 08 · Pruebas de las áreas 2 y 7

Base: `inventario/06_pruebas.md` (HEAD ba903f4). Lo que está aquí se leyó del árbol de trabajo (los archivos de prueba del frontend y del backend), y el cuerpo de cada prueba citada, no solo su nombre. No se corrió ninguna suite: ni `npm test`, ni Maven, ni Docker.

**Qué cubre cada área**
- **Área 2 · Experimentos y carga de video:** crear, listar y filtrar, detalle, eliminar experimento, tandas (eliminar tanda), subida de video y sus reglas, varias tandas en un envío, validación del archivo.
- **Área 7 · Accesibilidad y pantallas:** axe-core sobre las pantallas, las pruebas de pantalla (`pantallas.test.jsx`), las páginas legales, y los contratos de servicios del frontend (guía y mock).

**Dónde se solapan:** 28 pruebas de `pantallas.test.jsx` son del área 2 y también aparecen en el área 7, porque ese archivo es «las pruebas de pantalla». Las cuento una vez en cada área y lo marco. Las 7 pruebas axe de componentes de gráficas (no pantallas) se cuentan aquí solo como accesibilidad; sus archivos ya están en el inventario de gráficas.

---

## Área 2 · Experimentos y carga de video

| Sub-área | Frontend | Backend | Worker | Archivos | Qué comprueban (leído en el cuerpo) | RF | RNF | CU |
|---|---|---|---|---|---|---|---|---|
| Carga de video (bloque completo: un formulario y varias tandas) | 19 | 0 | 0 | `pantallas.test.jsx` (líneas 200–502) | Solo el creador sube (un Administrador no, 403 en mock); elegir grupo con teclado y encolar; Día 1 se guarda sin análisis y vuelve al experimento; avisa si la tanda ya tiene video de esa sesión y no deja guardar; rechaza antes de subir si pasa 2048 MB; «Terminar» sin subir nada; el navegador revisa solo la extensión y el servidor (mock) rechaza el vertical. | RF-08 (parcial), RF-09 (parcial), RF-12 (parcial) | — | CU-05 (parcial), CU-19 (parcial), CU-20 (parcial) |
| Carga de video, varias tandas | (incluidas arriba) | 0 | 0 | `pantallas.test.jsx` (líneas 278–500) | «Agregar otra tanda» conserva el primer formulario, hereda el grupo, propone la tanda siguiente ya confirmada, no propone la misma tanda dos veces, «Quitar este formulario», un solo botón sube C y D en orden, si una tanda falla la otra no se sube. | — | — | CU-05 (parcial) |
| Eliminar experimento | 2 | 0 | 0 | `pantallas.test.jsx` (583–610) | Desde el detalle: solo confirmación, sin contraseña ni nombre (el foco empieza en «Cancelar»); no se puede con análisis procesando o en pausa (409). Desde la lista: solo los propios o como Administrador. | RF-27 | — | CU-23 |
| Eliminar experimento (reglas en servicio) | 12 (de `servicios.test.js`) | 0 | 0 | `services/servicios.test.js` (267–381) | Crear asigna id y empieza sin grupos; nombre único (409, mock); subir el primer video crea la tanda, la encola y actualiza la lista (mock); Día 1 se guarda sin analizar; un solo video por sesión (409, mock); vertical o no reproducible rechazado (422, mock); solo el creador sube; grupo nuevo exige tipo y tratamiento; precedencia del estado; eliminar solo el creador o admin, sin nombre ni contraseña; no se elimina con análisis procesando; aviso de 7 días con fecha exacta. | RF-10 (mock), RF-11 (mock), RF-12 (mock), RF-27 (mock), RF-30 (mock), RF-24 (mock) | — | CU-04 (mock), CU-19 (mock), CU-23 (mock), CU-24 (mock) |
| Lista y filtros (pantalla) | 5 | 0 | 0 | `pantallas.test.jsx` (751–823) | Sin experimentos invita a crear el primero; pagina de 20 en 20 con el total del servidor; filtra por estado, fecha y tratamiento y avisa de videos por borrarse (fecha exacta); busca sin acentos esperando a que termine de teclear; con filtros que no dejan nada lo dice. | RF-23, RF-24 | — | CU-22 |
| Lista y filtros (mock) | 5 (de `servicios.test.js`) | 0 | 0 | `services/servicios.test.js` (39–89) | Forma de la fila; pagina de 20 en 20 con resumen; filtra por estado, año y tratamiento y busca sin acentos; el resumen trae años, tratamientos sin repetir y avisos con clave; una respuesta sin resumen se tolera. | RF-23 (mock) | — | CU-22 (mock) |
| Detalle de experimento (mock) | 1 | 0 | 0 | `services/servicios.test.js` (91–107) | El ejemplo de la guía se pasa a la forma de las pantallas (grupos, tandas, letra, estado, análisis). | — | — | — |
| Tandas (eliminar tanda) | 10 | 0 | 0 | `eliminarTanda.test.jsx` | Quién puede eliminar (creador o admin; sin id de tanda no se ofrece); pide confirmación y no borra hasta confirmar; foco en «Cancelar»; llama al servicio con el grupo y la tanda; muestra 409 y 403 del servidor (mock de respuesta); si es la última tanda avisa que el grupo también se elimina; con una tanda navega al experimento, con más recarga el grupo. | RF-27 (parcial, tanda) | — | — |
| Cancelar subida | 1 (de `servicios.test.js`) | 0 | 0 | `services/servicios.test.js` (576–588) | Cancelar la subida no registra la tanda ni la encola, y falla como cancelación (`ERR_CANCELED`). | RF-08 (no) | — | CU-05 (parcial) |
| **Lista de experimentos (backend, con base)** | 0 | 14 | 0 | `backend-spring/…/service/ListaDeExperimentosTest.java` | Sin filtros la respuesta es la de siempre; cada estado filtra sus experimentos (incluye «sin videos» y «en espera del Día 2»); la fecha puede ser año o día; el tratamiento coincide con cualquier grupo sin acentos ni mayúsculas; filtros combinados; paginado de 20 en 20 sin repetir; campos iguales con y sin filtro; un valor no válido da 400 en cada filtro (y sin sesión, 401); búsqueda `q` por nombre o tratamiento; el resumen cuenta todos y trae los avisos de vencimiento; sin experimentos el resumen viene vacío. **Todas gated: necesitan `FST_TEST_DB_URL`.** | RF-23, RF-24, RNF-04 | RNF-04 (401 sin sesión, en `un_valor_que_no_es_valido_da_400_en_cada_filtro`) | CU-22, CU-14 (solo el aviso) |
| **Subida de video (backend, con base)** | 0 | 3 | 0 | `backend-spring/…/service/ArreglosE50Test.java` (líneas 121–140) | Solo tres pruebas del camino de subida: la tanda fuera de A–Z da 422 («La tanda debe ir de A a Z.»); una etiqueta de 101 caracteres da 422; una de 100 pasa la revisión de largo. **Gated, necesita base.** | — | — | CU-05 (parcial) |
| **Total área 2** | **57** | **17** | **0** | | | | | |

**Conteos del área 2:** frontend 57 (`pantallas` 28 + `servicios` 19 + `eliminarTanda` 10); backend 17 (`ListaDeExperimentosTest` 14 + `ArreglosE50Test` 3), **de los cuales 17 corren solo con `FST_TEST_DB_URL`** y 0 sin base. Worker 0.

---

## Área 7 · Accesibilidad y pantallas

| Sub-área | Frontend | Backend | Worker | Archivos | Qué comprueban (leído en el cuerpo) | RF | RNF | CU |
|---|---|---|---|---|---|---|---|---|
| Axe-core, pantallas de la lista | 12 | 0 | 0 | `accesibilidad.test.jsx` | axe sobre: inicio de sesión, Experimentos, Nuevo experimento, Experimento, Grupo, Cargar video, Análisis en curso, Pausa, Resultados (grupo=2, tanda=A, vista por defecto), Revisión, Mi perfil, Administración. Desactiva el contraste de color (jsdom no calcula estilos). | — | — | — |
| Axe-core, resumen del experimento | 1 | 0 | 0 | `pantallas.test.jsx` (1005–1009) | axe sobre el resumen del experimento (sin tanda). | — | — | — |
| Axe-core, páginas legales | 2 | 0 | 0 | `pages/AvisosLegales.test.jsx` | axe sobre el aviso de privacidad y los términos. | — | — | — |
| Axe-core, componentes de gráficas | 7 | 0 | 0 | `GaleriaGraficas.test.jsx`, `G2`…`G6`, `SegundoASegundo.test.jsx` | Un axe por componente (no por pantalla). Contados aquí por ser accesibilidad; sus archivos son del inventario de gráficas. | — | — | — |
| Pantallas: acceso y recuperación | 6 | 0 | 0 | `pantallas.test.jsx` (68–123) | Entra con una cuenta de ejemplo y llega a Experimentos; marca un correo sin forma de correo; sin sesión cualquier ruta lleva al login; cuenta nueva pasa por el primer acceso; el login ofrece el aviso de privacidad y los términos; recuperar contraseña con el enlace y entrar con la nueva. | RF-01, RF-03 (parcial) | RNF-04 | CU-01, CU-03, CU-16 |
| Pantallas: primer acceso | 7 | 0 | 0 | `pantallas.test.jsx` (825–908) | Mensajes de formulario de primer acceso: temporal que no coincide marca su campo; error del servidor distinto de «actual»; contraseña de más de 72 bytes se rechaza antes de enviarla; sin marcar la casilla no se envía; la casilla enlaza el aviso y los términos en pestaña nueva; el aviso y los términos se abren sin sesión y con temporal pendiente. | — | — | CU-16 |
| Pantallas: administración | 7 | 0 | 0 | `pantallas.test.jsx` (125–198, 922–944) | Crear y editar cuenta en un modal que se cierra con Escape; filtra cuentas por rol y estado con cuántos experimentos creó cada una; restablecer la temporal pide confirmación y la muestra una vez; cuenta desactivada no trae el botón; crear cuenta muestra la temporal una sola vez; identificador repetido marca su campo; identificador de máximo 10 caracteres. | RF-05 (parcial), RF-28 | — | CU-11, CU-12 |
| Pantallas: cargar video, experimentos, grupo | (incluidas en área 2) | 0 | 0 | `pantallas.test.jsx` | Ver área 2. | | | |
| Pantallas: resultados | 17 | 0 | 0 | `pantallas.test.jsx` (516–582, 966–1041) | Muestra especímenes y comparación entre grupos; botones de reporte no generan archivos (demo); grupo completo con estadísticos recalculados; faltantes señalados; porcentaje por conducta y desglose por minuto; bloques de 5 s sin backend lo explican; resumen dice qué se logró y qué falta; ¿Qué pasó? dice en palabras comunes el nivel mixto y los sin datos; descargas en demo; «Ver el detalle» abre la primera tanda en la vista elegida; el segundo a segundo con grupo completo pide elegir una; el selector de grupo cambia de grupo y conserva la vista; «Grupo completo» conserva la vista; el detalle explica qué es una tanda y el nivel preciso; el resumen dice qué es el Día 2; axe sobre el resumen. | RF-19, RF-21 | — | CU-09, CU-27 |
| Pantallas: progreso, pausa y revisión | 10 | 0 | 0 | `pantallas.test.jsx` (631–745, 678–745 en los bloques de pausa y revisión) | Completados habilitan «Ver resultados» y «Revisar segundo a segundo»; quien no subió no ve «Revisar»; cancelado dice el motivo solo si lo manda el backend; pausa muestra imagen, tiempo y ajusta; cancelar pide confirmación; «El recuadro está bien» vuelve a la cola; ajustar sin mover el recuadro muestra el 422; revisión: en pausa una tecla cambia solo ese segundo y guardar deja la revisión; reiniciar pide confirmación; sin permiso lo explica. | RF-15, RF-16, RF-32 (parcial) | — | CU-07, CU-25, CU-26 |
| Pantallas: grupo y sesión | 4 | 0 | 0 | `pantallas.test.jsx` (612, 620, 954, 960) | Token en localStorage (sobrevive a cerrar pestaña); el menú muestra la versión y cerrar sesión borra los borradores (no comprueba que se borre el token); grupo: «Revisar segundo a segundo» no aparece a quien no subió, y sí a quien subió. | RF-02 (parcial) | — | CU-02 (parcial) |
| Pantallas: perfil | 1 | 0 | 0 | `pantallas.test.jsx` (945–953) | Nombre, apellidos y correo se guardan sin espacios de sobra. | RF-06 (parcial) | — | CU-18 (parcial) |
| Contratos de servicios (guía, backend simulado) | 7 | 0 | 0 | `services/contrato.test.js` | Simula el backend con la forma de la guía (§6): login manda `{correo, contrasena}` y entiende la respuesta; lista y detalle con `/status`; la lista manda los filtros con los valores de la guía y no manda los vacíos; resultados de una tanda con «etiqueta · letra»; revisión completa lo que la guía no trae y pone el token del video; notificaciones con tipo e idExperimento; subir video con un POST multipart con los campos de §6.1. **No llama al backend real.** | RF-01 (simulado), RF-33 (simulado) | — | CU-01 (simulado) |
| Equivalencias guía ↔ pantalla | 3 | 0 | 0 | `lib/contrato.test.js` | Cada tabla de equivalencias va y vuelve sin perder valores; valores de la guía (estados, sesión, rol, conducta); la letra de la tanda es su ordinal (A = 1). | — | — | — |
| Servicios sobre datos de ejemplo (sin experimentos) | 41 (de `servicios.test.js`) | 0 | 0 | `services/servicios.test.js` (108–265, 382–606 menos experimentos) | Notificaciones; recuperar contraseña (solo la existente recibe enlace, el enlace sirve una vez, vencido o inventado no sirve); administradores (no se puede desactivar al único); contraseña temporal (invalida la anterior, 404 y 409 como el backend, correo único); resultados por tanda; cola de análisis; pausa; reportes sin backend; revisión; comparación entre grupos; cancelar subida; envoltorio de errores; administración con el backend real (datos de ejemplo). **Son pruebas de la capa de datos de ejemplo (`mocks`), no del backend.** | RF-03 (mock, enlace vencido), RF-31 (mock, comparación), RF-32 (mock) | RNF-05 (mock, temporal con cambio obligatorio) | CU-03 (mock), CU-13 (mock, alerta de disco), CU-25 (mock) |
| **Total área 7 (sin contar las 7 axe de componentes)** | **151** | **0** | **0** | | | | | |

**Conteos del área 7:** frontend 151 (`accesibilidad` 12 + `pantallas` 80 + `AvisosLegales` 8 + `services/contrato` 7 + `lib/contrato` 3 + `servicios` no-experimentos 41) más 7 axe en componentes. **Backend 0**: no hay pruebas de accesibilidad ni de contratos en Java. Worker 0. Todo corre sin base.

**Pantallas que axe revisa (15 pantallas completas):** inicio de sesión; Experimentos; Nuevo experimento; Experimento; Grupo; Cargar video; Análisis en curso; Pausa; Resultados (grupo 2, tanda A, vista por defecto); Revisión; Mi perfil; Administración; resumen del experimento; aviso de privacidad; términos.

**Pantallas que axe no revisa:** restablecer contraseña; primer acceso; las vistas de resultados «Segundo a segundo», «Minuto a minuto», «Cada 5 segundos» y «Comparación entre grupos» (solo se revisa la vista por defecto); la vista de gráficas dentro del resumen (solo el componente); los diálogos de eliminar; el panel de notificaciones; la pantalla de error.

---

## RF / RNF / CU → prueba

Solo cuento lo que una prueba concreta comprueba. «(parcial)» indica que la prueba comprueba una parte del requisito.

**Área 2**
- RF-08 (solo .mp4 o .mov, con mensaje antes de subir): `pantallas.test.jsx` → «el navegador solo revisa la extensión; el servidor rechaza el video vertical (D-64)» (parcial: UI, sin backend).
- RF-09 (uno o dos videos; Día 1 sin análisis): `pantallas.test.jsx` → «el video de Día 1 se guarda sin encolar análisis y vuelve al experimento (D-71)» (parcial: sin los 300 s).
- RF-10 (video reproducible y horizontal): `services/servicios.test.js` → «el servidor rechaza un video vertical o que no se puede reproducir (D-64)» (solo mock).
- RF-11 (nombre único, datos al crear): `services/servicios.test.js` → «el nombre es único en todo el sistema (EXPERIMENTO.nombre UNIQUE)» (solo mock).
- RF-12 (análisis automático al validar): `services/servicios.test.js` → «subir el primer video crea la tanda, la encola y actualiza la lista» (solo mock).
- RF-23 (lista con estados y filtros): `ListaDeExperimentosTest` → «cada_estado_filtra_los_experimentos_que_tienen_ese_estado» (backend, con base).
- RF-24 (aviso de 7 días con nombre y fecha): `ListaDeExperimentosTest` → «el_resumen_trae_los_avisos_de_vencimiento» (backend, con base).
- RF-27 (eliminar propio o Administrador, con confirmación): `pantallas.test.jsx` → «desde la lista, solo en los experimentos propios (o como Administrador)».
- RF-30 (Administrador gestiona cualquier experimento): `services/servicios.test.js` → «eliminar: solo quien lo creó o un Administrador, sin pedir nombre ni contraseña (D-63, R-08)» (solo mock).
- RNF-04 (solo usuarios autenticados): `ListaDeExperimentosTest` → «un_valor_que_no_es_valido_da_400_en_cada_filtro» (el nombre no lo dice: contiene la comprobación de 401 sin sesión).
- CU-04 (registrar experimento): `services/servicios.test.js` → «crear asigna el id siguiente y empieza sin grupos» (solo mock).
- CU-05 (subir video): `pantallas.test.jsx` → «elegir el grupo con el teclado, confirmar la tanda y encolar el video» (UI); `ArreglosE50Test` → «la_tanda_fuera_de_a_z_se_rechaza_con_422» (backend, parcial).
- CU-19 (encolar análisis): `pantallas.test.jsx` → «elegir el grupo con el teclado, confirmar la tanda y encolar el video» (UI).
- CU-20 (notificar rechazo de video): `pantallas.test.jsx` → «el navegador solo revisa la extensión; el servidor rechaza el video vertical (D-64)» (parcial).
- CU-22 (consultar lista): `ListaDeExperimentosTest` → «cada_estado_filtra_los_experimentos_que_tienen_ese_estado» (backend, con base).
- CU-23 (eliminar experimento): `pantallas.test.jsx` → «desde el detalle pide solo confirmación; con un análisis procesando, no se puede (D-87)».
- CU-24 (gestionar experimentos globales): `services/servicios.test.js` → «eliminar: solo quien lo creó o un Administrador…» (solo mock).

**Área 7**
- RF-01 (inicio de sesión): `pantallas.test.jsx` → «entra con una cuenta de ejemplo y llega a Experimentos»; `services/contrato.test.js` → «login: manda {correo, contrasena} y entiende la respuesta» (simulado).
- RF-02 (cerrar sesión descarta el token): `pantallas.test.jsx` → «el menú de usuario muestra la versión y cerrar sesión borra los borradores de la revisión» (parcial: no comprueba el token).
- RF-03 (recuperación con enlace de 60 minutos): `pantallas.test.jsx` → «recuperar la contraseña con el enlace y entrar con la nueva»; `services/servicios.test.js` → «un enlace vencido o inventado no sirve» (parcial: no comprueba los 60 minutos).
- RF-05 (alta y temporal única): `pantallas.test.jsx` → «crear una cuenta muestra la contraseña temporal una sola vez» (parcial).
- RF-06 (perfil): `pantallas.test.jsx` → «perfil: nombre, apellidos y correo se guardan sin espacios de sobra» (parcial: no comprueba que el identificador no se edite).
- RF-19 (resultados por tanda y grupo): `pantallas.test.jsx` → «ver el grupo completo: todas sus tandas completadas, con los estadísticos recalculados (D-93)»; «cada conducta con su porcentaje, y el desglose por minuto de cualquier espécimen».
- RF-21 (comparación entre grupos): `services/servicios.test.js` → «comparación entre grupos calculada: sale de las tandas: medias, pendientes y grupos que mezclan niveles» (mock).
- RF-28 (lista de cuentas con filtros): `pantallas.test.jsx` → «filtra las cuentas por rol y estado, con cuántos experimentos creó cada una (§9.6)».
- RF-32 (revisión segundo a segundo): `pantallas.test.jsx` → «en pausa, una tecla cambia solo ese segundo; guardar deja la revisión» (parcial, UI).
- RF-33 (notificaciones con enlace): `services/contrato.test.js` → «notificaciones: título y enlace con tipo e idExperimento» (simulado).
- RNF-04 (solo autenticados): `pantallas.test.jsx` → «sin sesión, cualquier ruta lleva al login».
- RNF-05 (temporal con cambio obligatorio): `services/servicios.test.js` → «la cuenta creada entra solo con su temporal y debe cambiarla» (mock; no comprueba el hash).
- CU-01 (iniciar sesión): `pantallas.test.jsx` → «entra con una cuenta de ejemplo y llega a Experimentos».
- CU-02 (cerrar sesión): `pantallas.test.jsx` → «el menú de usuario muestra la versión y cerrar sesión borra los borradores…» (parcial).
- CU-03 (recuperar contraseña): `pantallas.test.jsx` → «recuperar la contraseña con el enlace y entrar con la nueva».
- CU-07 (monitorear progreso): `pantallas.test.jsx` → «los análisis completados habilitan «Ver resultados» y «Revisar segundo a segundo» (§9.4)» (parcial).
- CU-09 (consultar resultados): `pantallas.test.jsx` → «muestra los especímenes de la tanda y la comparación entre grupos».
- CU-11 (gestionar cuenta): `pantallas.test.jsx` → «crear y editar cuenta se hacen en un modal que se cierra con Escape (§9.6)».
- CU-12 (crear cuenta): `pantallas.test.jsx` → «crear una cuenta muestra la contraseña temporal una sola vez».
- CU-13 (monitorear disco): `services/servicios.test.js` → «la alerta de disco solo llega a los Administradores» (mock).
- CU-16 (cambiar contraseña al primer acceso): `pantallas.test.jsx` → «una cuenta nueva pasa por el primer acceso antes de entrar».
- CU-18 (actualizar perfil): `pantallas.test.jsx` → «perfil: nombre, apellidos y correo se guardan sin espacios de sobra» (parcial).
- CU-25 (revisar segundo a segundo): `pantallas.test.jsx` → «en pausa, una tecla cambia solo ese segundo; guardar deja la revisión».
- CU-26 (corregir etiqueta de un segundo): `pantallas.test.jsx` → «en pausa, una tecla cambia solo ese segundo; guardar deja la revisión».
- CU-27 (estadísticas de grupo o tanda): `pantallas.test.jsx` → «ver el grupo completo: todas sus tandas completadas, con los estadísticos recalculados (D-93)».

**Recuento de RF, RNF y CU distintos**
- Área 2: RF-08, RF-09, RF-10, RF-11, RF-12, RF-23, RF-24, RF-27, RF-30 (9 RF); RNF-04 (1 RNF); CU-04, CU-05, CU-19, CU-20, CU-22, CU-23, CU-24 (7 CU).
- Área 7: RF-01, RF-02, RF-03, RF-05, RF-06, RF-19, RF-21, RF-28, RF-32, RF-33 (10 RF); RNF-04, RNF-05 (2 RNF); CU-01, CU-02, CU-03, CU-07, CU-09, CU-11, CU-12, CU-13, CU-16, CU-18, CU-25, CU-26, CU-27 (13 CU).
- **Unión:** 19 RF distintos (RF-01, 02, 03, 05, 06, 08, 09, 10, 11, 12, 19, 21, 23, 24, 27, 28, 30, 32, 33), 2 RNF distintos (RNF-04, RNF-05) y 20 CU distintos.

---

## Observaciones (por qué algunos RF no quedan bien cubiertos)

1. **El backend casi no prueba las áreas 2 y 7.** Crear, eliminar experimento, eliminar tanda, subir (formato, vertical, Día 1, un video por sesión, nombre único, 2 a 4 cilindros) y el detalle no tienen ninguna prueba de backend. Las 14 de la lista y las 3 de subida son todas las pruebas de backend del área, y todas necesitan base.
2. **Las pruebas de frontend prueban la capa de datos de ejemplo**, no el backend: `servicios.test.js` usa `mocks` (VITE_USE_MOCKS=true). `contrato.test.js` simula el backend con la forma de la guía. Por eso el rechazo de un video vertical, el nombre único y el 409 de un segundo video se dan por probados solo en simulación.
3. **Nombre engañoso:** `ListaDeExperimentosTest.un_valor_que_no_es_valido_da_400_en_cada_filtro` también comprueba el 401 sin sesión (línea 192). El nombre no lo dice.
4. **La prueba de cerrar sesión** comprueba que se borran los borradores de la revisión, no que se borre el token (RF-02, CU-02).
5. **La recuperación de contraseña** prueba que un enlace vencido no sirve, pero no los 60 minutos (RF-03).
6. **Axe no prueba RNF-06** (usabilidad sin ayuda técnica): eso requiere usuarios reales.
7. **El archivo base decía 75 pruebas en `pantallas.test.jsx`:** hoy son 80.
8. **Errores de texto:** `RevisionServiceImpl` dice «Solo quien subió el video…» donde el criterio es el creador (ya registrado en el inventario de pantallas).

---

## Lo que no pude confirmar

- Que las pruebas de backend con base pasen: no se corrió la suite (lo hará el orquestador).
- Que `npm test` pase en una máquina limpia.
- Los conteos del archivo base para las áreas que no revisé (1, 3–6, 8).
- Que las 7 axe de componentes de gráficas sean suficientes para la accesibilidad de esas pantallas (solo el componente, sin la página completa).
- El número de pruebas que Maven reporta como *skipped* (el conteo es estático).
