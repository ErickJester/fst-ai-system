# 08 · Pruebas por área: worker y pipeline, notificaciones y correo, base de datos

Base: `inventario/06_pruebas.md` (HEAD ba903f4). Conteos:

- **Worker:** conteo estático de `def test_` en `worker/test_*.py`. **No se ejecutaron**: las pruebas del worker corren dentro de Docker (`docker exec <worker> python -m unittest <archivo>`) y no se usó Docker.
- **Backend:** conteo de `@Test` en los archivos **trackeados en HEAD** (206 en 23 archivos). Las marcadas «FST_TEST_DB_URL» se omiten sin esa variable (`@EnabledIfEnvironmentVariable`). No se corrió Maven.
- **Frontend:** conteo de `it(` en los archivos trackeados (la suite se corrió antes: 331 casos en 27 archivos, todos pasan).

Qué quiere decir «comprueba»: se leyeron las aserciones de cada prueba (no solo el nombre). Donde una prueba solo verifica un texto o un cálculo auxiliar, se dice.

---

## Área 3 · Worker y pipeline

Incluye: etapas y cola, pausa a los tres segundos y su ajuste, cancelación por plazo, reinicio, tiempos, modelo, progreso, diagnóstico en PDF, limpieza de reportes huérfanos y borrado de videos.

| Área | worker | backend | frontend | archivos | qué comprueban | RF | RNF | CU |
|---|---|---|---|---|---|---|---|---|
| 3.1 Pausa (worker) | 19 | — | — | `worker/test_pausa.py` | Racha de 3 s sin animal decide la pausa; gana la racha más temprana y, en empate, el cilindro de número menor; solo mira los últimos 300 s; el recuadro sale del bloque del instante (no de la mediana); el ajuste se acumula en `correcciones.json` y borra `pausa.json`; «continuar» acumula y borra; sin ajuste no cambia nada; la corrección empieza en k−1; la foto se lee sin enderezar; un tramo ya aceptado no vuelve a pausar; otro cilindro no se afecta; escritura atómica. | — | — | — |
| 3.2 Progreso (worker) | 10 | — | 1 (`servicios.test.js`, «sin backend queda fija») | `worker/test_vigilancia.py`; `frontend/src/services/servicios.test.js` | El avance nunca llega a 100 ni baja; sigue avanzando después de lo esperado; cilindros suma pasos sin pasar de 100; clasificación «prediciendo» por tiempo; último paso y segundos de ffmpeg; el fotograma de referencia se descarta; ritmo leído y guardado. | RF-15 (parcial: solo el porcentaje) | — | CU-07 |
| 3.3 Diagnóstico PDF (worker) | 6 | — | 3 (`diagnostico.test.jsx`: reintentos, PDF ya no existe, «Volver a intentar») | `worker/test_diagnostico.py`; `frontend/src/diagnostico.test.jsx` | El PDF trae páginas e imagen de control; la imagen va debajo del texto y en una segunda página si no cabe; sin control no hay imagen; los rangos de tubos salen del registro; la interfaz reintenta como mucho 15 veces y descarga el PDF. | RF-16 (parcial: el PDF, no la descripción por etapa) | — | CU-08 |
| 3.4 Tiempos (worker y frontend) | 7 | — | 4 (`services/tiempos.test.js`) + 2 (`modo.test.jsx`: cronómetro con y sin modo) + 1 (`lib/fst.test.js`) | `worker/test_tiempos.py`; `frontend/src/services/tiempos.test.js`; `modo.test.jsx`; `lib/fst.test.js` | `tiempos.json` no se escribe sin modo desarrollador; al empezar hay inicio y equipo y nada cerrado; cada etapa cierra la anterior; al cerrar hay fin y total; error o cancelado cierran hasta donde llegó; reiniciar empieza de cero. | — | — | — |
| 3.5 Modelo (worker) | 22 | 5 (`ModeloActualDeDesarrolloTest`, libre) + 5 (`ModeloDelAnalisisTest`, libre) | — | `worker/test_modelo.py`; `backend-spring/.../service/ModeloActualDeDesarrolloTest.java`; `ModeloDelAnalisisTest.java` | Identidad del modelo (archivo, hash, tamaño, paquete); se calcula una vez y otra si cambia; `tiempos.json` trae el modelo; sin modo desarrollador no se calcula ni escribe; `modelo-actual.json` se escribe de forma atómica; el backend responde 404 sin modo y dice si el modelo no existía; el texto del modelo omite lo que no se sabe. | — | — | — |
| 3.6 Pausa (backend) | — | 4 (`PausaServiceUmbralTest`, libre) + 5 (`EstadoConPausaTest`, FST_TEST_DB_URL) | 6 (`pantallas.test.jsx`: imagen, cancelar, continuar, 422, tecla en pausa; el motivo de cancelación) + 6 (`servicios.test.js`: permisos, ajustar a la cola, 422 con 4 px, continuar, cancelar) | `PausaServiceUmbralTest.java`; `EstadoConPausaTest.java`; `pantallas.test.jsx`; `servicios.test.js` | Un recuadro que se mueve tres píxeles o menos se rechaza y cuatro ya es ajuste; un borde movido más de tres píxeles basta; sin el cilindro no hay recuadro con qué comparar; `pausa.json` da cilindro, segundo y plazo restante (70 h tras 2 h en pausa); con `pausa.json` dañado o sin hora la pausa es null y el estado sigue; fuera de pausa la pausa es null; solo el creador o un administrador ve y actúa sobre la pausa. | RN-15/RN-17 (no es RF) | — | — |
| 3.7 Reinicio (backend) | — | 8 (`ReinicioDeAnalisisTest`, FST_TEST_DB_URL) | 1 (`pantallas.test.jsx`, «reiniciar pide confirmación») | `ReinicioDeAnalisisTest.java`; `pantallas.test.jsx` | Solo se reinicia un análisis completado (409 en otros estados); con video borrado, 409; otra cuenta, 403; análisis inexistente o de otro experimento, 404 antes que 403; el creador reinicia y el análisis queda en cola sin resultados, reportes ni archivos viejos; un reinicio repetido, 409; dos reinicios a la vez: solo pasa uno (contra PostgreSQL). | RF-32 (parcial: el reinicio) | — | — |
| 3.8 Limpieza de reportes y bloqueo (worker) | 8 | — | — | `worker/test_reportes.py` | Borra la carpeta de reportes del experimento; sin carpeta no falla; si el borrado falla, solo avisa en el log; borra solo las carpetas de experimentos que ya no existen; `marcar_error` bloquea el experimento antes de tocar el análisis (**aserción sobre el texto del SQL**, `FOR UPDATE`; no se ejecuta contra la base). | — | — | — |
| 3.9 Aviso de vencimiento de video (backend) | — | 1 (`ListaDeExperimentosTest`, «el resumen trae los avisos de vencimiento», FST_TEST_DB_URL) | 1 (`pantallas.test.jsx`, «avisa cuando el video de una tanda ya se borró») | `ListaDeExperimentosTest.java`; `pantallas.test.jsx` | El resumen de la lista trae los avisos de videos que vencen (a 7 días). La pantalla avisa si el video ya se borró. | RF-24 | RNF-08 (parcial) | CU-14 (no cubierto: ver abajo) |
| 3.10 Disco (frontend) | — | — | 1 (`servicios.test.js`, «la alerta de disco solo llega a los Administradores») | `frontend/src/services/servicios.test.js` | Solo los administradores reciben la alerta de disco. No comprueba el umbral (80 %) ni la limpieza (90 %). | RF-29 (parcial) | RNF-09 (parcial) | CU-13 (parcial) |

**Totales del área 3:** worker **72** (`test_pausa` 19, `test_vigilancia` 10, `test_tiempos` 7, `test_modelo` 22, `test_diagnostico` 6, `test_reportes` 8; estático, sin ejecutar); backend **28** (14 libres: `PausaServiceUmbralTest` 4 y `ModeloActualDeDesarrolloTest` 5 + `ModeloDelAnalisisTest` 5; 14 con `FST_TEST_DB_URL`: `ReinicioDeAnalisisTest` 8, `EstadoConPausaTest` 5, `ListaDeExperimentosTest` 1); frontend **≈27** (`servicios` 9, `pantallas` 8, `modo` 2, `tiempos` 4, `fst` 1, `diagnostico` 3). Las 2 pruebas de `test_reportes` que se marcan «bloqueo» en el área 8 también cuentan aquí (no se suman dos veces en el total del worker).

**Sin pruebas en el área 3 (lo que no está cubierto):**

- `worker/cola.py`: tomar el siguiente análisis con `FOR UPDATE SKIP LOCKED` y reencolar huérfanos: **no hay prueba**.
- `worker/tareas_programadas.py`: borrado de videos a los 30 días, limpieza de disco al 90 %, respaldo diario: **no hay prueba** de esas funciones (el respaldo sí, en `test_respaldo.py`, área 8).
- Cancelación automática de una pausa a las 72 h (`revisar_pausas`): **no hay prueba**; `EstadoConPausaTest` solo comprueba el plazo que se muestra.
- El pipeline completo (etapas 1 a 3 con video real): **no hay prueba automatizada**; la prueba de carga de la base no lo ejecuta.

---

## Área 6 · Notificaciones y correo

Incluye: el correo que manda el worker, el correo de cuenta nueva y de recuperación, el contador de notificaciones y el candado con el que el worker crea avisos.

| Área | worker | backend | frontend | archivos | qué comprueban | RF | RNF | CU |
|---|---|---|---|---|---|---|---|---|
| 6.1 Correo del worker | 16 | — | — | `worker/test_correos.py` | Un mensaje SMTP por pendiente con asunto, destinatario y texto; se saltan los pendientes sin correo; con `SMTP_HOST` vacío no se intenta nada ni se registra error; remitente por omisión; un correo rechazado no corta los demás; sin servidor no lanza; STARTTLS y usuario; los seis tipos tienen asunto con texto; `notificar` deja el correo pendiente con el asunto de su tipo; `probar_correo` dice si salió, el motivo del rechazo y el código de salida (0, 1, 2). | RF-33 | — | — |
| 6.2 Notificaciones y contador (backend) | — | 2 (`ArreglosE50Test`: contador de no leídas y tope de la lista, FST_TEST_DB_URL) | 5 (`campanita.test.jsx`: punto y número, marcar como leída, pestaña oculta, sondeo cada 30 s) + 1 (`services/contrato.test.js`, título y enlace de la notificación) | `ArreglosE50Test.java`; `frontend/src/campanita.test.jsx`; `services/contrato.test.js` | El contador cuenta todas las no leídas de la cuenta, aunque la lista traiga solo algunas; no pasa del tope de la lista; la campanita muestra el número del servidor y sondea según la pestaña. | RF-33 | — | — |
| 6.3 Cuenta nueva y recuperación (backend) | — | 9 de correo + 3 de enlace (`AdministracionYRecuperacionTest`, FST_TEST_DB_URL) | 6 (`pantallas.test.jsx`: primer acceso ×4, recuperar, crear cuenta repetida) + 2 (`services/servicios.test.js`: correo repetido, enlace de un solo uso) | `AdministracionYRecuperacionTest.java`; `pantallas.test.jsx`; `services/servicios.test.js` | Crear cuenta da una temporal de 12 caracteres, la manda por correo una sola vez y obliga a cambiarla; un correo que falla no deshace la cuenta; con el correo caído la temporal se responde y sirve; identificador o correo repetidos, 409 y ningún correo; una cuenta desactivada, 409 y sin correo; el administrador no se da temporal a sí mismo; una temporal nueva deja sin efecto la anterior y los enlaces; `recover` responde igual exista o no el correo y solo manda si existe; el enlace de recuperación sirve una sola vez, también con dos peticiones a la vez; una cuenta desactivada después de pedir el enlace ya no lo usa. | RF-03, RF-05 | RNF-05 | CU-03, CU-12, CU-16 |
| 6.4 Candado de la cuenta y avisos del worker (backend) | — | 2 (`AdministracionYRecuperacionTest`: candado de cuenta y edición mientras el worker crea un aviso, FST_TEST_DB_URL) | — | `AdministracionYRecuperacionTest.java` | El worker puede insertar notificaciones mientras el administrador bloquea la cuenta (`FOR NO KEY UPDATE`) sin que se traben ni se pierda nada. | RF-33 | — | — |

**Totales del área 6:** worker **16** (estático, sin ejecutar); backend **16** (todas con `FST_TEST_DB_URL`; ninguna corre sin base); frontend **≈14** (`campanita` 5, `contrato` 1, `pantallas` 6, `servicios` 2).

**Sin pruebas en el área 6:**

- Que el correo de un análisis terminado o en pausa salga por SMTP de punta a punta (el worker lo prueba por partes, con un servidor simulado).
- Que el correo de la alerta de disco o del video borrado salga (la creación del aviso está en `test_correos`, el disparo no).
- Que el aviso de 7 días se mande por correo (RF-24 lo muestra en la lista; no hay correo de ese aviso).

---

## Área 8 · Base de datos (PostgreSQL real)

Pruebas que corren contra PostgreSQL: solo las **122 de backend con `FST_TEST_DB_URL`** en 11 archivos (las de `AdministracionYRecuperacionTest` 30, `ArreglosE50Test` 14, `ReinicioDeAnalisisTest` 8, `ListaDeExperimentosTest` 14, `ReportesCsvXlsxTest` 18, `RevisionYVideoTest` 18, `BloquesDeResultadosTest` 5, `EstadoConPausaTest` 5, `SinVerYNivelTest` 5, `SegundosYExcelTest` 3, `GraficasEndpointTest` 2). Corren sobre una base vacía con el esquema `backend-spring/db/init/001_esquema.sql`. Las migraciones (`migraciones.sql`) no tienen prueba.

Worker: `test_respaldo.py` (5) usa un `pg_dump` simulado; `test_reportes.py` (2 de 8) solo comprueba el texto del SQL. **Ninguna prueba del worker corre contra la base real.** Frontend: 0 (no toca la base).

| Área | worker | backend | frontend | archivos | qué comprueban | RF | RNF | CU |
|---|---|---|---|---|---|---|---|---|
| 8.1 Respaldo (worker) | 5 | — | — | `worker/test_respaldo.py` | Crea `fst-AAAAMMDD.dump` sin temporales; conserva los 7 más nuevos; reemplaza el de hoy; si `pg_dump` falla no queda nada válido y el log no trae la clave; sin `pg_dump` solo registra. **Simulado**, no contra la base. | — | — | — |
| 8.2 Restricciones y candados (backend) | — | 8 concretas (en `AdministracionYRecuperacionTest` y `ReinicioDeAnalisisTest`) | — | ver lista de abajo | Unicidad de identificador y correo; candados `FOR UPDATE` y `FOR NO KEY UPDATE`; `lock_timeout` (55P03); último administrador activo bajo concurrencia; reinicio concurrente. | — | — | — |
| 8.3 Resto de consultas (backend) | — | 114 más (gated) | — | 9 archivos gated | Consultas, filtros, resultados y reportes contra la base real; no comprueban restricciones de forma directa. | — | — | — |

**Restricciones que quedan comprobadas contra la base real, y por qué prueba:**

1. **Unicidad de `usuario.idinstitucional` (PK) y de `usuario.correo` (UNIQUE):** dos altas simultáneas del mismo identificador dejan una sola fila (`la_misma_cuenta_pedida_dos_veces_a_la_vez_se_crea_una_sola_vez`; `identificador_o_correo_repetidos_responden_409`, que comprueba que no se cambia nada y no sale correo). La prueba no distingue si el 409 viene de la validación previa o de la base; la concurrencia sí muestra que es la base quien impide la segunda fila.
2. **Candados:** `FOR NO KEY UPDATE` de la cuenta y `FOR UPDATE` del experimento no se traban entre el worker y la edición (`el_candado_de_la_cuenta_deja_pasar_las_notificaciones_del_worker_y_for_update_no`; `editar_el_nombre_mientras_el_worker_crea_una_notificacion_no_se_traba_ni_pierde_nada`). Un `lock_timeout` de 500 ms devuelve `55P03` (en el test del candado, líneas 552-572).
3. **Último administrador activo:** no se puede desactivar ni quitar el rol al último, ni con dos peticiones simultáneas (`r16_no_se_desactiva_ni_se_le_quita_el_rol_al_ultimo_administrador`; `r16_dos_administradores_que_se_desactivan_a_la_vez_no_dejan_el_sistema_sin_ninguno`; `r16_quitarse_el_rol_el_uno_al_otro_a_la_vez_tampoco_deja_el_sistema_sin_ninguno`).
4. **Reinicio concurrente:** dos reinicios a la vez solo pasa uno (`dos_reinicios_a_la_vez_solo_pasa_uno`, `ReinicioDeAnalisisTest`).
5. **Contador de no leídas:** cuenta las filas de `notificacion` con `leido = false` de la cuenta (`el_contador_de_no_leidas_cuenta_todas_las_de_la_cuenta`).
6. **Dominios:** las pruebas insertan solo valores válidos (`'Dia 2'`, `::estado_analisis`, `'control'`). **No hay una prueba que intente un valor fuera del dominio.**

**Restricciones que NO quedan comprobadas contra la base real (aclaración):**

- **CHECK:** la base lo hace cumplir (está en el DDL) pero no hay prueba que lo verifique: ninguna prueba intenta violarlo. Categoría: *la base lo hace cumplir, sin prueba que lo verifique*.
- **Llaves foráneas con NO ACTION:** la base lo hace cumplir; ninguna prueba intenta violarlas (borrar un padre con hijos). Categoría: *la base lo hace cumplir, sin prueba que lo verifique*.
- **UNIQUE compuestas:** la base lo hace cumplir; ninguna prueba intenta violarlas. Categoría: *la base lo hace cumplir, sin prueba que lo verifique*.
- **Índices `ix_*`:** no son una restricción de integridad; ninguna prueba verifica que existan ni que se usen. No aplica la categoría de violación.

Ninguna de estas restricciones deja de estar aplicada por la base: todas están declaradas en `001_esquema.sql`. Por eso no hay ninguna en la categoría «ninguna prueba intenta violarlo y la base no lo hace cumplir».

**Estado de la suite con base:** según el reporte del orquestador (9-nov, contra PostgreSQL real), 219 pruebas, 0 fallos, 1 omitida (la que genera un PDF de ejemplo). Esa corrida no está en este archivo; las 122 pruebas gated de este inventario son el conteo estático de HEAD.

**Lista original (referencia):**

- **CHECK:** ninguna prueba inserta valores inválidos a propósito. Quedan sin comprobar: `video.duracion > 0`, `segundo` entre 1 y 300, `ncilindros` entre 2 y 4, `ordinal >= 1`, `numerocilindro` entre 1 y 4, y `ck_analisis_diagnostico`.
- **Llaves foráneas con `ON DELETE NO ACTION`:** no hay prueba que intente borrar un padre y espere el error de la base. Las pruebas solo insertan hijos con padres existentes.
- **UNIQUE compuestas** (`uq_grupo_etiqueta`, `uq_tanda_ordinal`, `uq_especimen_cilindro`, `uq_video_sesion`, `uq_analisis_video`, `uq_observacion`, `uq_reporte_ruta`, `uq_experimento_nombre`): sin prueba negativa.
- **Índices** `ix_*`: sin prueba.
- **Migraciones** (`migraciones.sql`: `nombreoriginal`, quitar `uq_reporte_formato`): sin prueba.

---

## RF / RNF / CU → prueba (solo cuando una prueba concreta lo comprueba)

Leyenda: **completo** = una prueba verifica el requisito tal como está escrito; **parcial** = verifica una parte.

**RF (`chapters/04_analisis.tex:49-333`)**

- **RF-03** (recuperación de contraseña, enlace de 60 min) · completo · `AdministracionYRecuperacionTest.recover_responde_lo_mismo_exista_o_no_el_correo_y_solo_manda_correo_si_existe` (backend, FST_TEST_DB_URL); frontend `pantallas.test.jsx`, «recuperar la contraseña con el enlace y entrar con la nueva».
- **RF-05** (el administrador crea cuentas y las desactiva) · completo · `AdministracionYRecuperacionTest.crear_una_cuenta_da_una_temporal_una_sola_vez_la_manda_por_correo_y_obliga_a_cambiarla` (backend, FST_TEST_DB_URL).
- **RF-15** (progreso en tiempo real) · parcial · `worker/test_vigilancia.py`, `test_nunca_llega_a_uno_y_nunca_baja`; frontend `servicios.test.js`, «sin backend queda fija…». La etapa activa no se verifica.
- **RF-16** (descripción del problema y PDF de diagnóstico) · parcial · `worker/test_diagnostico.py`, `test_el_pdf_trae_las_paginas_y_la_imagen`; frontend `diagnostico.test.jsx`, «reintenta como mucho 15 veces…». La descripción por etapa no se prueba.
- **RF-24** (aviso a 7 días del borrado) · completo · `ListaDeExperimentosTest.el_resumen_trae_los_avisos_de_vencimiento` (backend, FST_TEST_DB_URL).
- **RF-29** (monitoreo del disco) · parcial · frontend `servicios.test.js`, «la alerta de disco solo llega a los Administradores». No se prueba el porcentaje ni la cola.
- **RF-32** (revisión y reinicio) · parcial (solo reinicio) · `ReinicioDeAnalisisTest.el_creador_reinicia_y_el_analisis_queda_en_cola_sin_resultados_ni_reportes_ni_archivos_viejos` (backend, FST_TEST_DB_URL).
- **RF-33** (notificaciones en la barra y por correo) · completo para los dos canales · `worker/test_correos.py`, `test_notificar_deja_el_correo_pendiente_con_el_asunto_del_tipo`; backend `ArreglosE50Test.el_contador_de_no_leidas_cuenta_todas_las_de_la_cuenta`; frontend `campanita.test.jsx`, «el punto y el número son los de unreadCount…».
- **RF-25** (borrado automático de videos a los 30 días): **sin prueba**.
- **RF-13 y RF-14** (pipeline de tres etapas y detección de cilindros): **sin prueba** de esas etapas.

Requisitos de RF que cubren mis áreas: **8** (`RF-03`, `RF-05`, `RF-24`, `RF-33` completos; `RF-15`, `RF-16`, `RF-29`, `RF-32` parciales).

**RNF (`chapters/04_analisis.tex:373-479`)**

- **RNF-05** (contraseñas con sal; temporal con cambio obligatorio) · completo · `AdministracionYRecuperacionTest.crear_una_cuenta_da_una_temporal_una_sola_vez_la_manda_por_correo_y_obliga_a_cambiarla` (verifica el hash y el cambio obligatorio; FST_TEST_DB_URL).
- **RNF-08** (borrado a 30 días y aviso a 7 días) · parcial · `ListaDeExperimentosTest.el_resumen_trae_los_avisos_de_vencimiento` (solo el aviso). El borrado a 30 días no tiene prueba.
- **RNF-09** (alerta al 80 % y limpieza al 90 %) · parcial · frontend `servicios.test.js`, «la alerta de disco solo llega a los Administradores» (solo quién la recibe).

Requisitos RNF que cubren mis áreas: **3** (`RNF-05` completo; `RNF-08` y `RNF-09` parciales).

**CU (`chapters/05_diseno.tex`, campo «Referencias»)**

- **CU-03** (recuperar contraseña) · completo · backend `AdministracionYRecuperacionTest.recover_responde_lo_mismo…`; frontend `pantallas.test.jsx`, «recuperar la contraseña…».
- **CU-07** (monitorear progreso) · completo · `worker/test_vigilancia.py`, `test_nunca_llega_a_uno_y_nunca_baja`; frontend `servicios.test.js`, «sin backend queda fija…».
- **CU-08** (descargar reporte de diagnóstico) · completo · `worker/test_diagnostico.py`, `test_el_pdf_trae_las_paginas_y_la_imagen`; frontend `diagnostico.test.jsx`, ««Volver a intentar» pide el PDF a mano…».
- **CU-12** (crear cuenta de usuario) · completo · backend `AdministracionYRecuperacionTest.identificador_o_correo_repetidos_responden_409`; frontend `pantallas.test.jsx`, «crear cuenta: un identificador repetido marca el campo del identificador…».
- **CU-13** (monitorear estado del sistema) · parcial · frontend `servicios.test.js`, «la alerta de disco solo llega a los Administradores».
- **CU-16** (cambiar contraseña al primer acceso) · completo · backend `AdministracionYRecuperacionTest.con_contrasena_temporal_el_cambio_exige_aceptar_los_terminos_y_no_toca_nada_sin_ello` (FST_TEST_DB_URL); frontend `pantallas.test.jsx`, «una cuenta nueva pasa por el primer acceso antes de entrar».
- **CU-14** (borrar video automáticamente): **sin prueba** del borrado (el aviso de la pantalla sí tiene prueba, `pantallas.test.jsx`, «avisa cuando el video de una tanda ya se borró»; no es el borrado).

Casos de uso que cubren mis áreas: **6** (`CU-03`, `CU-07`, `CU-08`, `CU-12`, `CU-16` completos; `CU-13` parcial). `CU-14` sin cobertura.

---

## Resumen

- **RF distintos cubiertos por mis áreas:** 8 (4 completos, 4 parciales).
- **RNF distintos cubiertos:** 3 (1 completo, 2 parciales).
- **CU distintos cubiertos:** 6 (5 completos, 1 parcial). CU-14 sin cobertura.
- **Conteos:** worker 72 (área 3) + 16 (área 6) + 5 (área 8) (estáticos; `test_reportes` se cuenta una vez en el área 3); backend 28 + 16 + 122 (área 8 incluye todas las gated, algunas se solapan con las áreas 3 y 6).

**No pude confirmar:**

- Que las pruebas del worker pasen: son conteo estático, y corren dentro de Docker.
- Que el 409 de identificador repetido venga de la base y no de la validación previa (la prueba no lo separa).
- Qué restricciones de la base se comprueban en las pruebas de integración que no leí una por una (`BloquesDeResultadosTest`, `RevisionYVideoTest`, `ReportesCsvXlsxTest`, `SegundosYExcelTest`, `SinVerYNivelTest`, `GraficasEndpointTest`): se asume que solo verifican comportamiento.
