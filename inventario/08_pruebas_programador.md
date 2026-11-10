# 08 · Pruebas del programador por área

**Áreas:** (1) Cuentas y sesión · (4) Resultados y reportes · (5) Revisión segundo a segundo.

**Fuente:** el árbol de trabajo tal como está hoy (HEAD `ba903f4` más cambios sin commit). Se parte de `06_pruebas.md`, que es correcto a HEAD, y se corrige lo que cambió en el árbol de trabajo. Conteos: `@Test` en Java, `it(`/`test(` en JS (las pruebas de `accesibilidad.test.jsx` que salen de un bucle se cuentan por corrida), `def test_` en Python. Los cuerpos de las pruebas de backend citadas como evidencia se leyeron. En frontend se leyeron las de acceso, primer acceso, sesión, desactivación, revisión y el token del video; las demás citadas (recuperar contraseña, porcentaje por conducta, botones de reporte y perfil) se tomaron de su nombre. Las pruebas no citadas se clasificaron por nombre.

**No se corrió ninguna suite en este encargo.** Los números de ejecución vienen de la última corrida conocida: frontend 344/344, backend sin base pasando.

## Correcciones a `06_pruebas.md`

- **Backend a HEAD:** 206 pruebas, 122 con `FST_TEST_DB_URL`. Correcto.
- **Backend en el árbol de trabajo:** `AdministracionYRecuperacionTest.java` tiene 31 pruebas, no 30 (se agregó `con_contrasena_temporal_el_cambio_exige_aceptar_los_terminos_y_no_toca_nada_sin_ello`). Con base son 123, y las no commiteadas suman 13.
- **Frontend:** 344 casos en 28 archivos, no 331 en 27. Faltan dos cosas en 06: `pages/AvisosLegales.test.jsx` (8 casos, sin commit, pruebas de las páginas de aviso y términos de RN-18) y las 5 pruebas nuevas de `pantallas.test.jsx` (que pasa de 75 a 80).
- **`GraficasPdfTest.java`** no está condicionada entera: solo una de sus 9 pruebas depende de `FST_PDF_EJEMPLO`. Las otras 8 corren sin base.

## Resumen por área

| Área | backend | frontend | worker | archivos | qué comprueban | RF | RNF | CU |
|---|---|---|---|---|---|---|---|---|
| **1 Cuentas y sesión** | 43 (38 con BD, 5 sin BD) | 46 | 0 | `AdministracionYRecuperacionTest` (31), `ArreglosE50Test` (7 de 14), `ReglaAdministradoresTest` (5); `pantallas.test.jsx` (23), `services/servicios.test.js` (15), `pages/AvisosLegales.test.jsx` (8, sin commit) | Inicio de sesión, temporal y primer acceso con aceptación de términos, recuperar contraseña y su enlace, alta y edición de cuentas, desactivación, regla del último Administrador, permisos por rol, panel de monitoreo | ✔ 03, 04, 05, 06, 28 · ◐ 01, 02, 07, 29 | ✔ 04, 05 | ✔ 03, 11, 12, 15, 16, 17, 18 · ◐ 01, 02, 13 |
| **4 Resultados y reportes** | 109 (33 con BD, 76 sin BD) | 127 | 0 | `BloquesDeCincoTest` (12), `BloquesDeResultadosTest` (5, BD), `EstadisticosTest` (6), `GraficasDeResultadosTest` (10), `GraficasEndpointTest` (2, BD), `GraficasPdfTest` (9, sin commit), `ReporteAlmacenTest` (5), `ReporteAlmacenVersionTest` (3, sin commit), `ReporteCsvTest` (17), `ReporteXlsxTest` (7), `ReportesCsvXlsxTest` (18, BD), `ExcelDelExperimentoTest` (4), `SegundosDeResultadosTest` (4), `SegundosYExcelTest` (3, BD), 2 PDF de `ArreglosE50Test` (BD), 2 de totales de `SinVerYNivelTest` (BD); en frontend, `components/resultados/graficas/*` (72), `SegundoASegundo.test.jsx` (10), `lib/svgAPng.test.js` (8), `services/graficas.test.js` (7), `services/segundos.test.js` (3), `pantallas.test.jsx` (18), `services/servicios.test.js` (4), `lib/porcentajes.test.js` (4), `borradores.test.jsx` (1) | Resultados por espécimen, por minuto, bloques de 5 s, grupos y tandas, estadísticos muestrales, comparación entre grupos, CSV, XLSX, PDF de resultados, Excel del experimento, segundo a segundo en vista y gráficas | ✔ 19, 20, 21, 22, 31 · ◐ 18 | ✔ 04 (solo 401 sin sesión) | ✔ 09, 10, 27 |
| **5 Revisión segundo a segundo** | 29 (29 con BD, 0 sin BD) | 13 | 0 | `RevisionYVideoTest` (18, BD), `SinVerYNivelTest` (3 de nivel, BD), `ReinicioDeAnalisisTest` (8, BD); `pantallas.test.jsx` (7), `services/servicios.test.js` (5), `borradores.test.jsx` (1) | Corrección por segundo, origen y nivel, reinicio, permisos de creador y Administrador, video con token temporal | ✔ 32 | ✔ 04 (401 del video y JWT en la URL) | ✔ 25, 26 |

**Totales de mis áreas:** backend 181 (100 con BD, 81 sin BD); frontend 186 (todas corren con jsdom, ninguna depende de BD); worker 0.

**Distintos cubiertos:** RF ✔ 11 (03, 04, 05, 06, 19, 20, 21, 22, 28, 31, 32) y ◐ 5 (01, 02, 07, 18, 29), 16 en total. RNF ✔ 2 (04, 05). CU ✔ 12 (03, 09, 10, 11, 12, 15, 16, 17, 18, 25, 26, 27) y ◐ 3 (01, 02, 13).

## Detalle por área

### 1 · Cuentas y sesión

| Archivo | Casos | Con BD | Qué comprueba (de cuerpos leídos o de nombres confirmados) |
|---|---|---|---|
| `backend-spring/.../service/AdministracionYRecuperacionTest.java` | 31 | sí (todas) | Permisos del Administrador (403 al investigador, 401 sin sesión, rol leído de la base en cada petición). Alta con temporal única, enviada por correo, con cambio obligatorio y hash bcrypt. Temporal con aceptación de términos (400 sin aceptación, 200 con ella). Restablecer temporal (409 sobre cuenta desactivada, sin tocar hash ni correo). Duplicados (409). Lista filtrada por rol y estado. Edición de datos, rol y desactivación; rechazo de correo de otra cuenta e identificador distinto (409 y 400). Último Administrador no se desactiva ni pierde el rol. Recuperar contraseña: misma respuesta exista o no la cuenta; el correo dice «60 minutos»; enlace de un solo uso; enlace vencido o de otro uso rechazado. Monitoreo: disco, conductas y cola en orden. |
| `backend-spring/.../service/ArreglosE50Test.java` | 7 de 14 | sí | Alta ignora el rol y nace como Investigador; identificador recortado, con letras o de más de 10 dígitos; el Administrador no se desactiva ni pierde el rol a sí mismo; sí puede cambiar su nombre y desactivar a otro. (Las otras 7 pruebas del archivo son de experimentos y de notificaciones; ver «No asignadas».) |
| `backend-spring/.../service/ReglaAdministradoresTest.java` | 5 | no | Quitar al único Administrador activo deja sin administrador (rechazado); con otro activo sí se puede; una cuenta que no era administradora activa no cuenta; la temporal tiene 12 caracteres sin los que se confunden y no se repite. |
| `frontend/src/pantallas.test.jsx` | 23 | — | Acceso (entrar, correo inválido, sin sesión lleva al login); primer acceso (temporal que no coincide, error del servidor, 72 bytes, casilla sin marcar, enlaces de la casilla, aviso y términos sin sesión y con temporal pendiente); login con sus enlaces; recuperar contraseña con enlace; modal de cuenta cerrable con Escape; filtro por rol y estado con cuántos experimentos creó cada una; restablecer temporal con confirmación; cuenta desactivada sin botón de temporal; crear cuenta muestra la temporal una vez; identificador repetido y máximo de 10 caracteres; perfil guarda sin espacios; sesión persiste en localStorage; cerrar sesión borra borradores. |
| `frontend/src/services/servicios.test.js` | 15 | — | Forma de cuenta; mock de login (cuenta desactivada no entra; cuenta nueva entra solo con su temporal y debe cambiarla); restablecer temporal invalida la anterior y deja el cambio pendiente, con 404 y 409 como el backend; un correo por cuenta; recuperar responde igual exista o no; enlace de un solo uso; enlace vencido o inventado no sirve; último Administrador; con dos, uno sí puede dejar de serlo; editar no toma el correo de otra cuenta; editar no manda el identificador. |
| `frontend/src/pages/AvisosLegales.test.jsx` | 8 | — | Páginas de aviso de privacidad y de términos (RN-18): título, secciones, enlace «Volver», sin sesión, axe sin fallos, nota de redacción, contenido sobre datos, uso y contraseña, y la frase de que no emite diagnóstico y de que los videos se borran a los 30 días. **Sin commit.** |

**Lo que no cubre esta área:** RF-01 y RF-02 solo tienen pruebas parciales (ver «Marcas parciales»); no hay prueba de login con contraseña incorrecta en el backend; no hay prueba de que no exista formulario de registro público (RF-07).

### 4 · Resultados y reportes

| Archivo | Casos | Con BD | Qué comprueba |
|---|---|---|---|
| `BloquesDeCincoTest.java` | 12 | no | Bloques de 5 s que empiezan en 1, 6 y 11; el último bloque incompleto trae solo los segundos que existen; la conducta del bloque es la más frecuente con empate (activa gana); «sin ver» es conducta activa con matiz; una conducta fuera del dominio es error de programa; un espécimen sin segundos no tiene bloques. |
| `BloquesDeResultadosTest.java` | 5 | sí | Bloques con la forma de la guía; solo entran tandas con análisis completado; usa los segundos ya corregidos; sin sesión 401, experimento inexistente 404 y cualquier cuenta con sesión puede verlos (esto último sostiene RNF-04); experimento sin completados responde lista vacía. |
| `EstadisticosTest.java` | 6 | no | Sin especímenes los tres valores son null; con uno, la varianza es cero; con dos, se divide entre n−1 (10 y 20 dan varianza 50); valores conocidos; valores iguales sin dispersión; redondeo a un decimal. |
| `GraficasDeResultadosTest.java` | 10 | no | Cinco categorías y cinco minutos siempre; grupos en orden control, referencia, experimentales; varianza y desviación muestrales; coeficiente de variación; grupo sin datos con n=0; conducta activa y sin ver con sus valores; duración media; minuto incompleto marcado; **niveles mezclados: el grupo no se promedia y se da por tanda**. |
| `GraficasEndpointTest.java` | 2 | sí | Grupos en orden con faltantes y estadísticos; sin sesión 401 y experimento inexistente 404. |
| `GraficasPdfTest.java` (sin commit) | 9 | 1 con `FST_PDF_EJEMPLO` | Sección de gráficas con marcadores; cada marcador salta a una página válida; sin grupos con datos no hay sección; nivel mixto no se dibuja y lo dice; el texto del PDF trae títulos, notas y nombres de grupo; etiqueta larga no se sale de su columna. |
| `ReporteAlmacenTest.java` | 5 | no | El PDF es del experimento; el CSV y el XLSX son del grupo o de la tanda; un alcance con otro texto se rechaza y no puede salir de la carpeta. |
| `ReporteAlmacenVersionTest.java` (sin commit) | 3 | no | Sin marca de versión borra los PDF viejos y deja CSV; con la marca actual no toca nada; si la base falla al invalidar, el arranque se detiene. |
| `ReporteCsvTest.java` | 17 | no | BOM UTF-8 y fin de línea CRLF; **encabezado exacto (RF-22)**; sin ver es la última columna; una fila por espécimen y minuto; estadísticos al final con un decimal; revisión y niveles mixtos en su aviso; escape RFC 4180; protección contra fórmulas en celdas de texto; modelos solo en modo desarrollador. |
| `ReporteXlsxTest.java` | 7 | no | Hoja Resultados con encabezado en negrita; segundos como números; estadísticos en gris y varianza incluida; revisión y aviso al final; texto que parece fórmula se guarda como texto; modelos en modo desarrollador. |
| `ReportesCsvXlsxTest.java` | 18 | sí | CSV de grupo con columnas, filas y estadísticos; CSV de tanda con su letra; aviso con niveles distintos; apostrofo en grupos que empiezan con igual; XLSX con números reales; 400 sin grupo y con tanda inválida; 404 para recursos de otro experimento; 409 sin análisis completados (no queda ni fila ni archivo); sin sesión 401; segunda descarga sale del archivo guardado; tras corregir segundos se regenera y marca revisión; modelo en CSV y XLSX solo en modo desarrollador; el PDF trae la tabla de modelos en modo desarrollador. |
| `ExcelDelExperimentoTest.java` | 4 | no | Libro con las hojas Resultados y Segundo a segundo; la hoja de segundos tiene una columna por conducta con 1 o 0 y el origen; la hoja de resultados trae la tabla del grupo con estadísticos; sin segundos la hoja solo trae el encabezado. |
| `SegundosDeResultadosTest.java` | 4 | no | La respuesta trae duración, grupo, letra y segundos; tanda sin análisis completado responde 404; sin letra responde 400; si la tanda o el grupo no existen, el error de alcance sube tal cual. |
| `SegundosYExcelTest.java` | 3 | sí | Segundos de una tanda con conducta, origen y sin ver null; tanda sin completar 404 y sin tanda 400; el Excel del experimento tiene sus dos hojas. |
| `SinVerYNivelTest.java` (solo 2 de 5) | 2 | sí | Totales y minutos traen las cinco claves y suman el 100 %; con resultados sin minutos también trae sin ver. (Las otras 3 son de nivel y están en el área 5.) |
| `ArreglosE50Test.java` (solo 2 de 14) | 2 | sí | El PDF sin completados se entrega pero no se guarda en caché; el PDF con resultados se guarda y trae la columna sin ver. |
| `frontend/src/components/resultados/graficas/*.test.jsx` | 72 | — | GaleriaGraficas (22), G2 curva (7), G3 barras (8), G4 apilada (9), G5 variabilidad (12), G6 mapa (10), BotonDescargarImagen (4): cada gráfica dibuja sus grupos, marca nivel mixto y grupos sin datos con su nota, muestra n y las dos notas obligatorias, la tabla tiene los mismos números que el dibujo, y axe no marca fallos. |
| `frontend/src/components/resultados/SegundoASegundo.test.jsx` | 10 | — | Selector de espécimen; «Sin ver» con su borde; origen con su texto (máquina, confirmada, corregida, dudosa; desconocido se dice «Sin dato»); tiempo m:ss; sin especímenes muestra aviso; axe. |
| `frontend/src/lib/svgAPng.test.js` | 8 | — | Conversión del SVG a PNG: rechaza entrada nula o que no es SVG, tamaño del viewBox por escala, fondo, copia de estilos sin tocar el original, rechazo sin canvas. |
| `frontend/src/services/graficas.test.js` | 7 | — | Todos los grupos del Día 2 en orden; grupo con nivel mixto sin estadísticos y con números por tanda; n=0 sin números; tandas pendientes en faltantes; cinco categorías que suman; minuto incompleto; **desviación y varianza muestrales con n−1**. |
| `frontend/src/services/segundos.test.js` | 3 | — | Forma de los segundos por tanda; «sin ver» es conducta null; tanda sin completar 404. |
| `frontend/src/pantallas.test.jsx` (18 de 80) | 18 | — | Resumen y detalle: especímenes y comparación entre grupos; los botones de reporte explican lo que hará el backend; grupo completo con estadísticos recalculados y tandas faltantes señaladas; **cada conducta con su porcentaje y el desglose por minuto**; bloques de 5 s los calcula el backend; resumen sin tanda; «¿Qué pasó?» en palabras comunes; las dos descargas en demo no tienen archivo; «Ver el detalle» abre la tanda; segundo a segundo por tanda (y con grupo completo pide elegir una); axe del resumen; selector de grupo y vista conservada; glosas de tanda, nivel y Día 2; lleva a resultados del grupo y de la tanda. |
| `frontend/src/services/servicios.test.js` (4 de 60) | 4 | — | Tandas con medias, pendientes y grupos que mezclan niveles; cada espécimen trae las cuatro conductas (con conducta activa el nivel es agrupado, R-12); bloques de 5 s del backend (sin él no hay bloques); desglose por minuto suma a lo más 60 s. |
| `frontend/src/lib/porcentajes.test.js` | 4 | — | Análisis guarda 300 s aunque el video dure más; el porcentaje de una fila suma 100 % (sin dividir entre la duración del video); división no exacta sigue sumando 100; fila sin segundos da ceros. |
| `frontend/src/borradores.test.jsx` (1 de 2) | 1 | — | «Cada experimento con resultados tiene «Ver resultados →» y un PDF; sin CSV ni XLSX sueltos». |

**Lo que no cubre esta área:** no hay prueba de RF-18 sobre conductas de unos 2 s (eso es el clasificador, no esta área); no hay prueba de la copia H.264 de RF-32 (no es de estas pruebas); RF-26 (los resultados se conservan indefinidamente) no tiene prueba concreta: `ReporteAlmacenVersionTest` borra los PDF viejos por versión, y eso no es retención.

### 5 · Revisión segundo a segundo

| Archivo | Casos | Con BD | Qué comprueba |
|---|---|---|---|
| `backend-spring/.../service/RevisionYVideoTest.java` | 18 | sí | **Video con token temporal:** lo piden quien subió el video y el Administrador, otra cuenta 403; sin token o con uno inventado 401; token de otro análisis rechazado; el JWT de sesión no sirve en la URL ni en la cabecera; el video responde 200 y con Range 206; si el video ya se borró, 404. **Corrección:** el origen solo puede ser confirmada, corregida o dudosa; solo se guardan los segundos que vienen; un cuerpo con error no guarda ni los buenos; el Administrador corrige y otra cuenta recibe 403 sin cambiar nada; solo se corrige un análisis completado; sin video, 409; quien subió puede corregir (y sin video, no). |
| `backend-spring/.../service/SinVerYNivelTest.java` (3 de 5) | 3 | sí | Una corrección a conducta activa sube el nivel a agrupado y quitarla lo baja a preciso (**RF-32, CU-26**); un nivel agrupado del worker baja si la revisión quita la última conducta activa; una corrección rechazada no cambia el nivel. |
| `backend-spring/.../service/ReinicioDeAnalisisTest.java` | 8 | sí | Solo se reinicia un completado (409 en otro estado); con video borrado 409 sin tocar nada; otra cuenta 403 y sin sesión 401; experimento o análisis inexistente 404 antes que 403; **el creador reinicia, el análisis queda en cola y se borran resultados, reportes y archivos viejos**; un Administrador también puede; el reinicio no toca otros análisis; un reinicio repetido 409; dos reinicios a la vez pasan solo uno. |
| `frontend/src/pantallas.test.jsx` (7 de 80) | 7 | — | «Ver resultados» y «Revisar» habilitados en análisis completados; quien no subió no ve «Revisar»; grupo: «Revisar» no aparece a quien no subió ni es Administrador; quien subió sí lo ve; en la revisión, una tecla cambia solo ese segundo y guardar deja la revisión; reiniciar pide confirmación y lleva a En curso; sin permiso, la pantalla lo explica. |
| `frontend/src/services/servicios.test.js` (5 de 60) | 5 | — | El video se pide con el token de video y la URL no lleva el JWT; sin video (ya se borró) la revisión se consulta pero no se corrige ni se reinicia; la revisión solo es de un análisis completado y de quien subió o el Administrador; guardar cambia los segundos, recalcula los totales y marca la revisión; reiniciar borra la revisión y vuelve a encolar. |
| `frontend/src/borradores.test.jsx` (1 de 2) | 1 | — | Al ocultarse la página (pagehide, visibilitychange) el borrador de la revisión se escribe en ese momento. |

**Lo que no cubre esta área:** no hay prueba del ajuste de la pausa ni del recuadro (es de la pausa, no de la revisión); no hay prueba de que la corrección recalcule el nivel a través de la pantalla completa más allá del endpoint.

## RF / RNF / CU → prueba (una prueba de evidencia por requisito)

Marcas: ✔ comprobado · ◐ parcial (la prueba toca el requisito pero no verifica todo lo que dice).

**RF**
- ✔ RF-03 → `recover_responde_lo_mismo_exista_o_no_el_correo_y_solo_manda_correo_si_existe` (`AdministracionYRecuperacionTest`; el correo trae «60 minutos»)
- ✔ RF-04 → `un_investigador_recibe_403_en_todo_admin` (`AdministracionYRecuperacionTest`)
- ✔ RF-05 → `patch_cambia_datos_y_rol_desactiva_y_no_hay_delete` (`AdministracionYRecuperacionTest`); también `r16_no_se_desactiva_ni_se_le_quita_el_rol_al_ultimo_administrador` y `el_alta_ignora_el_rol_y_nace_como_investigador` (`ArreglosE50Test`)
- ✔ RF-06 → `patch_con_un_correo_de_otra_cuenta_responde_409_y_con_otro_identificador_responde_400` (`AdministracionYRecuperacionTest`); también `perfil: nombre, apellidos y correo se guardan sin espacios de sobra` (`pantallas.test.jsx`)
- ✔ RF-19 → `totales_y_minutos_traen_las_cinco_claves_y_suman_el_cien_por_ciento` (`SinVerYNivelTest`); también `cada conducta con su porcentaje, y el desglose por minuto de cualquier espécimen` (`pantallas.test.jsx`)
- ✔ RF-20 → `totales_y_minutos_traen_las_cinco_claves_y_suman_el_cien_por_ciento` (`SinVerYNivelTest`)
- ✔ RF-21 → `con_niveles_mezclados_el_grupo_no_se_promedia_y_se_da_por_tanda` (`GraficasDeResultadosTest`)
- ✔ RF-22 → `el_encabezado_es_exactamente_el_de_D_99` (`ReporteCsvTest`); también `las_filas_de_estadisticos_son_numeros_y_estan_en_gris_la_de_Varianza_incluida` (`ReporteXlsxTest`)
- ✔ RF-28 → `la_lista_filtra_por_rol_y_por_estado` (`AdministracionYRecuperacionTest`)
- ✔ RF-31 → `con_dos_especimenes_se_divide_entre_n_menos_1` (`EstadisticosTest`); también `varianza_y_desviacion_muestrales_con_dos_especimenes_y_coeficiente_de_variacion` (`GraficasDeResultadosTest`)
- ✔ RF-32 → `el_administrador_puede_corregir_y_otra_cuenta_recibe_403_sin_cambiar_nada` (`RevisionYVideoTest`); también `una_correccion_a_conducta_activa_sube_el_nivel_a_agrupado_y_quitarla_lo_baja_a_preciso` (`SinVerYNivelTest`) y `el_creador_reinicia_y_el_analisis_queda_en_cola_sin_resultados_ni_reportes_ni_archivos_viejos` (`ReinicioDeAnalisisTest`)
- ◐ RF-01 → `una cuenta desactivada no entra` (`servicios.test.js`: login da 401). Falta: el mensaje sin revelar el campo; no hay prueba de login con contraseña incorrecta en el backend.
- ◐ RF-02 → `el menú de usuario muestra la versión y cerrar sesión borra los borradores de la revisión` (`pantallas.test.jsx`). Falta: que el token se descarte.
- ◐ RF-07 → `un_investigador_recibe_403_en_todo_admin` (crear cuentas solo para el Administrador). Falta: que no exista registro público.
- ◐ RF-18 → `armar_parte_la_lista_de_segundos_en_bloques_que_empiezan_en_1_6_11` (`BloquesDeCincoTest`) y `totales_y_minutos…` (sin ver como quinta clave). Falta: conductas de unos 2 s.
- ◐ RF-29 → `system_trae_disco_conductas_y_la_cola_en_orden_de_llegada` (`AdministracionYRecuperacionTest`). Falta: la alerta al 80 % y el tiempo estimado.

**RNF**
- ✔ RNF-04 → `sin_sesion_admin_responde_401_y_el_rol_se_lee_de_la_base_en_cada_peticion` (`AdministracionYRecuperacionTest`); también `el_jwt_de_sesion_en_la_url_se_rechaza` (`RevisionYVideoTest`)
- ✔ RNF-05 → `crear_una_cuenta_da_una_temporal_una_sola_vez_la_manda_por_correo_y_obliga_a_cambiarla` (`AdministracionYRecuperacionTest`; comprueba con el encoder del sistema que el hash guardado corresponde a la temporal, no el formato bcrypt en sí)
- RNF-13 (4 usuarios simultáneos): **no hay prueba.** Las pruebas de concurrencia que hay (`dos_reinicios_a_la_vez_solo_pasa_uno`, `la_misma_cuenta_pedida_dos_veces_a_la_vez...`) prueban integridad, no los 4 usuarios ni el tiempo de respuesta.

**CU** (campo «Referencias» de `05_diseno.tex`)
- ◐ CU-01 (Iniciar sesión; RF-01, RF-04) → `entra con una cuenta de ejemplo y llega a Experimentos` (`pantallas.test.jsx`). Falta el flujo de error con mensaje.
- ◐ CU-02 (Cerrar sesión; RF-02) → `el menú de usuario muestra la versión y cerrar sesión borra los borradores de la revisión`. Falta el descarte del token.
- ✔ CU-03 (Recuperar contraseña; RF-03) → `recover_responde_lo_mismo_exista_o_no_el_correo_y_solo_manda_correo_si_existe` (`AdministracionYRecuperacionTest`); también `recuperar la contraseña con el enlace y entrar con la nueva` (`pantallas.test.jsx`)
- ✔ CU-09 (Consultar resultados; RF-19, RF-20, RF-21, RF-31) → `devuelve_los_bloques_de_cada_especimen_con_la_forma_de_la_guia` (`BloquesDeResultadosTest`)
- ✔ CU-10 (Descargar reporte; RF-22, RF-31) → `el_encabezado_es_exactamente_el_de_D_99` (`ReporteCsvTest`); también `los botones de reporte no generan archivos: explican lo que hará el backend` (`pantallas.test.jsx`)
- ✔ CU-11 (Gestionar cuenta; RF-05, RF-06, RF-07) → `patch_cambia_datos_y_rol_desactiva_y_no_hay_delete` (`AdministracionYRecuperacionTest`)
- ✔ CU-12 (Crear cuenta; RF-05, RF-07) → `identificador_o_correo_repetidos_responden_409` (`AdministracionYRecuperacionTest`)
- ◐ CU-13 (Monitorear estado; RF-29) → `system_trae_disco_conductas_y_la_cola_en_orden_de_llegada`. Falta la alerta y el tiempo estimado.
- ✔ CU-15 (Verificar Administrador; RF-04) → `un_investigador_recibe_403_en_todo_admin` (`AdministracionYRecuperacionTest`)
- ✔ CU-16 (Cambiar contraseña al primer acceso; RF-05) → `con_contrasena_temporal_el_cambio_exige_aceptar_los_terminos_y_no_toca_nada_sin_ello` (`AdministracionYRecuperacionTest`); también `primer acceso: sin marcar la casilla no se envía y el campo lo dice` (`pantallas.test.jsx`)
- ✔ CU-17 (Desactivar cuenta; RF-05) → `patch_cambia_datos_y_rol_desactiva_y_no_hay_delete` (`AdministracionYRecuperacionTest`, desactiva con 200); la regla del último Administrador, en `r16_no_se_desactiva_ni_se_le_quita_el_rol_al_ultimo_administrador`
- ✔ CU-18 (Actualizar perfil; RF-06) → `patch_con_nombre_apellidos_o_correo_vacios_o_solo_espacios_responde_400` (`AdministracionYRecuperacionTest`)
- ✔ CU-25 (Revisar segundo a segundo; RF-32) → `puede_corregir_es_verdadero_para_quien_subio_el_video_y_el_administrador_y_falso_para_otra_cuenta` (`RevisionYVideoTest`)
- ✔ CU-26 (Corregir etiqueta; RF-32) → `solo_se_guardan_los_segundos_que_vienen_y_el_resto_queda_como_estaba` (`RevisionYVideoTest`)
- ✔ CU-27 (Consultar estadísticas; RF-31, RF-21) → `con_dos_especimenes_se_divide_entre_n_menos_1` (`EstadisticosTest`)

**Fuera de RF/RNF/CU:** RN-18 (aviso de privacidad y términos) tiene las pruebas de `AvisosLegales.test.jsx` y `pantallas.test.jsx` (aviso y casilla). Es una regla de negocio, no un RF, y no hay una marca de RN en este inventario.

## Pruebas de estas áreas que no son mías (no asignadas aquí)

- Backend: `ListaDeExperimentosTest` (14, BD), `EstadoConPausaTest` (5, BD), `PausaServiceUmbralTest` (4), `ModeloDelAnalisisTest` (5), `ModeloActualDeDesarrolloTest` (5), 7 de `ArreglosE50Test` (experimentos y notificaciones).
- Frontend: `eliminarTanda.test.jsx` (10), `campanita.test.jsx` (5), `modo.test.jsx` (15), `control.test.jsx` (4), `diagnostico.test.jsx` (3), `lib/fst.test.js` (23), `services/contrato.test.js` (7), `lib/contrato.test.js` (3), `services/tiempos.test.js` (4), `hooks/*.test.jsx` (4), `accesibilidad.test.jsx` (12), y 32 de `pantallas.test.jsx` y 36 de `servicios.test.js` (experimentos, carga de video, progreso y pausa).
- Worker (93, todas sin asignar): `test_correos.py` (16), `test_diagnostico.py` (6), `test_modelo.py` (22), `test_pausa.py` (19), `test_reportes.py` (8, limpieza de archivos al borrar un experimento), `test_respaldo.py` (5), `test_tiempos.py` (7), `test_vigilancia.py` (10).

## Lo que no pude confirmar

- Que las 181 pruebas de backend de mis áreas pasen: no corrí Maven en este encargo. Las 81 sin BD se corrieron en una sesión anterior y pasaron; las 100 con BD no se han corrido. Entre las 100 está la prueba nueva de `AdministracionYRecuperacionTest` (sin commit).
- Que las 186 pruebas de frontend de mis áreas pasen en esta versión: la última corrida completa dio 344/344, pero no la corrí en este encargo.
- Que la prueba `recover…` verifique que el enlace caduque a los 60 minutos y no solo que el correo diga «60 minutos» (confirmé lo segundo).
- Si el mensaje «Not implemented: navigation to another Document» que apareció en una corrida anterior de `npm test` viene de alguna prueba de mis áreas: no lo revisé.
