# Qué falta en el documento (y qué falta en el sistema)

Lo lleva orquestador_web. Solo registro y comparación: **no se ha editado LaTeX ni el sistema.** El otro lado de esta tabla lo lleva orquestador_latex en `E:\fst-ai-system-latex\SINCRONIA_CON_SISTEMA.md`.

- **Sistema comparado:** HEAD `ba903f4` = v1.9.2 (8-nov-2026). Producción (Hostinger) = v1.0.0 = `15489f4` (21-oct-2026). Entre las dos hay 14 commits y 91 archivos.
- **Documento comparado:** el que refleja el sistema cercano a v1.0.0 (cap. 6 cerrado el 1-nov, cap. 5 tocado hasta el 2-nov), según orquestador_latex.
- **Fuentes (todas en `D:\proyectos\fst\inventario\`):** `01` a `07` (inventario del sistema) y `verificacion_programador.md`, `verificacion_pantallas_rf.md`, `verificacion_bd_desarrollo.md` (el documento contra el sistema, ítem por ítem).
- **Estado de la columna «Posición»:** la propuso orquestador_latex (capítulo, sección, línea) y yo la confirmé el 9-nov. Las posiciones y su confirmación final están en la sección 6; las columnas `pendiente` de las tablas 1A, 1B y 2 se leen con esa sección.

## 0. Lectura rápida

1. **La base de datos, el worker, el pipeline, el docker-compose, el nginx y la configuración no cambiaron desde producción.** Los archivos SQL son idénticos a `15489f4`; el único cambio de configuración es la versión de `package.json`. Los cambios posteriores son de interfaz, endpoints, reportes y modo desarrollador.
2. **Lo nuevo desde producción** que el documento no tiene: 4 endpoints, la pantalla de resumen de resultados, las seis gráficas, la vista segundo a segundo, el Excel del experimento, la carga de varias tandas y el modo desarrollador ampliado.
3. **Lo que el documento nunca tuvo bien** (ya estaba así en producción): una regla que el sistema no cumple (RN-18), tres que dice distinto (RN-03, RN-17, rol de subir video), «almacenamiento cifrado», y en el apéndice D las omisiones de valores por defecto, un CHECK, los valores por defecto y los cinco índices.

## 1. Por agregar al documento: el sistema lo tiene y el documento no

### 1A. Nuevo desde producción (posterior a `15489f4`)

| ID | Qué es | Dónde está en el sistema | Versión | Posición en el documento | Confirmación |
|---|---|---|---|---|---|
| N-01 | Endpoint `GET /experiments/{id}/results/charts`: datos de las gráficas por grupo, conducta y minuto | `ResultadosController.java`, `GraficasDeResultados.java` | 1.7.0 | pendiente (tabla de endpoints, `05_diseno.tex`, cerca de la línea 732) | pendiente |
| N-02 | Endpoint `GET /experiments/{id}/results/seconds?grupo=&tanda=`: la conducta de cada segundo con su origen | `SegundosController.java`, `SegundosDeResultados.java` | 1.8.0 | pendiente (misma tabla) | pendiente |
| N-03 | Endpoint `GET /experiments/{id}/report/excel`: Excel del experimento con hojas «Resultados» y «Segundo a segundo» | `ExcelExperimentoController.java`, `ExcelDelExperimento.java` | 1.8.0 | pendiente (misma tabla, junto a pdf, csv y xlsx) | pendiente |
| N-04 | Endpoint `GET /dev/modelo-actual` (solo modo desarrollador; 404 sin él) | `DevModeloController.java`, `ModeloActualDeDesarrollo.java` | 1.9.0 | pendiente (tabla de endpoints y cap. 6, modo desarrollador) | pendiente |
| N-05 | Pantalla de resumen de resultados del experimento: «Listos X de Y grupos», «¿Qué pasó? (comparado con el control)», descargas, «Ver el detalle» | `ExperimentoResultados.jsx`, `QueSePaso.jsx` | 1.8.0 | pendiente (pantallas, `05_diseno.tex`; y el CU de resultados) | pendiente |
| N-06 | Galería de seis gráficas (barras por grupo, curva por minuto, barras por minuto, reparto de la duración, variabilidad entre especímenes, mapa por minuto), con tabla de números y «Descargar imagen» | `components/resultados/graficas/` | 1.7.0 | pendiente (pantallas y reportes; decisión del usuario sobre «gráficas de barras», sección 3) | pendiente |
| N-07 | Vista «Segundo a segundo»: franja por espécimen y tabla con tiempo, conducta y origen | `SegundoASegundo.jsx` | 1.8.0 | pendiente (pantallas; y cap. 4, RF de resultados) | pendiente |
| N-08 | Hoja «Segundo a segundo» del Excel: una fila por espécimen y segundo, con tiempo y una columna 1 o 0 por conducta (lo que pidió el Dr. Sandino en la entrevista) | `ExcelDelExperimento.java` | 1.8.0 | pendiente (reportes; RF de descarga de datos) | pendiente |
| N-09 | Detalle por tanda con cinco vistas y selector de grupo y tanda: Por espécimen, Segundo a segundo, Minuto a minuto, Cada 5 segundos, Comparación entre grupos | `constantes.js` (`TABS`), `ResultadosPage.jsx` | 1.7.0 y 1.8.0 | pendiente (pantallas, `05_diseno.tex:2905-2906`) | pendiente |
| N-10 | Cargar video: «Agregar otra tanda» suma formularios, hereda grupo y tanda, y un solo botón sube todo en orden | `CargarVideoPage.jsx` | 1.1.0 a 1.4.0 | pendiente (CU-05 y pantalla de carga) | pendiente |
| N-11 | Cargar video: bajo el botón de guardar se dice lo primero que falta | `CargarVideoPage.jsx` | 1.9.1 | pendiente (pantalla de carga; detalle menor) | pendiente |
| N-12 | La versión del sistema se ve siempre abajo a la derecha | `VersionEsquina.jsx` | 1.2.0 | pendiente (pantallas, elementos globales) | pendiente |
| N-13 | Modo desarrollador: franja roja «Modo desarrollador», el modelo (.pt) en PDF, CSV y XLSX, y el modelo que se usará en la carga de video | varios | 1.2.1, 1.5.0, 1.9.0 | pendiente (cap. 6, configuración y modo desarrollador) | pendiente |
| N-14 | Revisión segundo a segundo con el tema claro (el oscuro se retiró) | `RevisionPage.jsx`, `app.css` | 1.6.0 | pendiente (solo si el documento menciona el tema oscuro: no lo menciona) | pendiente |
| N-15 | Cifras de pruebas actuales: 206 de backend (122 con base), 331 de interfaz, 93 de worker | `06_pruebas.md` | 1.5.0 a 1.9.2 | pendiente (`06_desarrollo.tex:414-416`, hoy 162, 202 y 71) | pendiente |

### 1B. Ya estaba en producción y el documento nunca lo tuvo

| ID | Qué es | Dónde está en el sistema | Posición en el documento | Confirmación |
|---|---|---|---|---|
| B-01 | Endpoint `GET /health` (público; estado, modo desarrollador y versión) | `HealthController.java` | pendiente (tabla de endpoints) | pendiente |
| B-02 | Endpoint `GET /experiments/{id}/dev/salida-modelo` (solo modo desarrollador) | `ResultadosController.java:75` | pendiente (tabla de endpoints y modo desarrollador) | pendiente |
| B-03 | CHECK `video.duracion > 0` | `001_esquema.sql:110` | pendiente (`apendice_D.tex:164`) | pendiente |
| B-04 | Once valores por defecto: `usuario.activo` true, `usuario.cambiorequerido` false, `analisis.estado` «en cola», `analisis.etapa` «preprocesamiento», `analisis.nivelclasif` «preciso», `analisis.fechaanalisis` now(), `video.fechacarga` now(), `segundo.origen` «maquina», `reporte.fechageneracion` now(), `notificacion.leido` false, `notificacion.fechacreacion` now() | `001_esquema.sql` | pendiente (apéndice D, por columna) | pendiente |
| B-05 | Cinco índices: `ix_experimento_usuario`, `ix_analisis_estado`, `ix_observacion_video`, `ix_notificacion_usuario`, `ix_notificacion_experim` (el nombre real termina en «experim», no en «experimento») | `001_esquema.sql:182-186` | pendiente (apéndice D) | pendiente |
| B-06 | Dos migraciones: `video.nombreoriginal` (20-oct) y se quita `uq_reporte_formato` (25-oct) | `migraciones.sql` | pendiente (apéndice D o cap. 6) | pendiente |

## 2. Por corregir o retirar: el documento dice algo que el sistema no hace igual

«Desde» indica si la diferencia ya existía en producción o apareció después.

| ID | Qué dice el documento | Qué hace el sistema | Documento | Desde | Posición / decisión |
|---|---|---|---|---|---|
| C-01 | La tabla de endpoints dice que subir video lo hace el rol «Investigador» | Solo el creador del experimento sube videos; otro investigador recibe 403 | `05_diseno.tex:704` | producción | pendiente |
| C-02 | RN-03 da las duraciones de 20 min (Día 1) y 5 min (Día 2) | El código no las valida; solo aparecen como texto de la interfaz | `04_analisis.tex:593` | producción | decisión del usuario: aclarar en el documento, o validar en el sistema |
| C-03 | RN-17 dice que cancelar un análisis no crea notificación | La cancelación automática a las 72 h sí crea la notificación «analisis cancelado» | `04_analisis.tex:727` | producción | decisión del usuario: distinguir la cancelación automática de la del usuario |
| C-04 | RN-18: aviso de privacidad y términos, aceptados en el primer ingreso | **Resuelto en el sistema (9-nov):** se construyó. Rutas públicas `/aviso-privacidad` y `/terminos`, enlaces en el login, casilla obligatoria en el primer acceso y el servidor exige `aceptaTerminos` al cambiar una contraseña temporal. La aceptación no se guarda: sin columna nueva. Falta commit y versión (queda en el árbol de trabajo) | `04_analisis.tex:742` | producción | decisión del usuario tomada: opción «construir en el sistema»; el documento debe agregar la pantalla, el caso de uso y el campo `aceptaTerminos` |
| C-05 | «Almacenamiento cifrado» | Solo la contraseña va con hash (bcrypt); lo demás no está cifrado | `04_analisis.tex:1255` | producción | decisión del usuario: corregir el texto |
| C-06 | Cuatro pestañas: por espécimen, comparación entre grupos, línea de tiempo por minuto, bloques de 5 s | Cinco pestañas con otros nombres: «Por espécimen», «Segundo a segundo», «Minuto a minuto», «Cada 5 segundos», «Comparación entre grupos» | `05_diseno.tex:2905-2906` | posterior (1.7.0 y 1.8.0) | pendiente |
| C-07 | El avance de la revisión se guarda con «Guardar» | El botón dice «Guardar correcciones» | `05_diseno.tex:2965-2967` | producción | pendiente |
| C-08 | RF-23: la lista muestra nombre, fecha, número de grupos, sesión, estado… | Las columnas son: Experimento, Grupos, Especímenes, Día 2 analizados, Estado, Responsable, Retención | `04_analisis.tex:244` | producción | pendiente |
| C-09 | En progreso, un análisis en pausa solo muestra el enlace «Ajustar o cancelar →» | Muestra además el tiempo que queda para responder | `05_diseno.tex:2883` | producción | pendiente |
| C-10 | Pruebas: 162 de backend, 202 de interfaz, 71 de worker | 206, 331 y 93 | `06_desarrollo.tex:414-416` | posterior | pendiente |
| C-11 | El simulador `worker_fake` sirve para probar sin el pipeline real | No existe en el repositorio (solo se menciona en un checklist) | `06_desarrollo.tex:418` | producción | pendiente |
| C-12 | Los umbrales de disco (80 % y 90 %) vienen del `.env` | No están en `.env.example`: son variables con valor por omisión en `worker/comun.py` | `06_desarrollo.tex:85-87` | producción | pendiente |
| C-13 | GRUPO: el tratamiento no puede estar vacío | `NOT NULL`, pero sin CHECK: una cadena vacía se acepta | `apendice_D.tex:121-129` | producción | pendiente |
| C-14 | «Las duraciones y los segundos usan numeric» | `duracion` es `numeric(8,2)`, pero `segundo` es `smallint` | `apendice_D.tex:199` | producción | pendiente |
| C-15 | Se menciona «gráficas de barras» (ya retirado del texto) | El sistema ahora tiene seis gráficas | (no aparece hoy) | posterior | ver N-06; decisión del usuario |

## 3. Decisiones que son del usuario

1. **C-04 (RN-18):** decidido: se construye en el sistema, sin guardar la aceptación.
2. **C-02 (RN-03) y C-03 (RN-17):** ¿se ajusta el documento a lo que hace el sistema, o el sistema a lo que dice el documento?
3. **C-05:** cómo se redacta «almacenamiento cifrado».
4. **Mensajes de error:** varios dicen «quien subió el video» y el criterio del código es el creador del experimento (el documento también dice creador). ¿Se unifica el texto?
5. **N-06:** cómo se presentan las gráficas en el documento; recordar que son un extra y que Sandino no las pidió.
6. **Apéndice D (B-03 a B-06):** lo agrega el documento solo si el usuario lo pide.

## 4. Sin poder confirmar

- RN-06, RN-09 (cola), RN-11 (videos sanos), RN-17 (reinicio desde el principio): requieren probar con datos reales.
- RF-09 «cada tanda con al menos un video»: no se halló una validación que lo impida.
- RF-17 (el buceo cuenta como nado en el etiquetado de entrenamiento): el etiquetado de entrenamiento no está en este repositorio.
- RF-18 (columnas de la pantalla de bloques), RF-33 (aviso si falla el correo) y «tubo resaltado» en la revisión: faltó leer el código o verlo en pantalla.
- Pantalla: el marcado de fragmentos «no se ve» en la pantalla de resultados (en el reporte sí está).
- RNF-02 (F1 de al menos 85 %): sin medición todavía; la validación en Colab está pendiente.
- RNF-12 (PEP 8): no hay linter de Python configurado.
- Valores de un CSV generado de verdad: no se generó ninguno en esta revisión.

## 5. Lo que falta en el sistema para igualar al documento

Nada pendiente de construir: RN-18 (aviso de privacidad y aceptación) se construyó el 9-nov sin cambiar la base de datos. Queda **sin commit**. Las demás diferencias del apartado 2 se resuelven cambiando el documento, salvo que el usuario decida lo contrario (C-02, C-03).

## 6. Posiciones propuestas por orquestador_latex y confirmación de orquestador_web (9-nov-2026)

Las líneas son las del árbol de trabajo del 9-nov. Referencias: tabla de endpoints `05_diseno.tex:681-778`; resultados `05_diseno.tex:2903`; revisión `:2956`; apéndice D `back/apendice_D.tex`. **Nadie ha editado nada.**

| ID | Posición propuesta | Confirmación |
|---|---|---|
| N-01, N-02 | Tabla de endpoints, filas nuevas tras `05_diseno.tex:734` (`/results/blocks`) | Confirmada |
| N-03 | Misma tabla, fila nueva tras `:742` (`/report/xlsx`) | Confirmada |
| N-04, B-02 | Grupo nuevo «Modo desarrollador» tras `:778`, con aviso de que responden 404 sin el modo; N-04 también en `06_desarrollo.tex:92-124` | Confirmada |
| B-01 | Grupo nuevo «Sistema» tras `:778`, antes del grupo «Modo desarrollador» (no antes de `/auth/login`) | Confirmada, con ese lugar |
| N-05 | `05_diseno.tex:2903`, párrafo antes de «Cuatro pestañas» (`:2905`); ampliar CU-09 (`:1787`) y RF-19 a RF-22; sin CU nuevo | Confirmada; ampliar RF-19, sin RF nuevo |
| N-06 | Solo ubicar: `05_diseno.tex:2903-2930`, RF-22 / CU-09 y `06_desarrollo.tex:328` | Confirmada; la decide el usuario |
| N-07 | `05_diseno.tex:2905-2906` (junto con C-06), `06_desarrollo.tex:328` y un RF nuevo tras RF-22 (`04_analisis.tex:225`) | Confirmada, con **RF nuevo** (es lo central para el Dr. Sandino) |
| N-08 | RF-22 (`04_analisis.tex:225`), CU-10 (`05_diseno.tex:1846`), `:2923` y `06_desarrollo.tex:354` | Confirmada; ampliar RF-22 y CU-10 |
| N-09 | Es C-06 | Confirmada |
| N-10 | `05_diseno.tex:2815`, CU-05 (`:1480`) y RF-08 a RF-10 | Confirmada; ampliar CU-05 |
| N-11 | `05_diseno.tex:2815-2870`, una frase | Confirmada |
| N-12 | `05_diseno.tex:2700-2707` y `06_desarrollo.tex:125` | Confirmada |
| N-13 | `06_desarrollo.tex:85-90`, `:92`, `:125` y `:354` | Confirmada |
| N-14 | No aplica (el documento no habla del tema oscuro) | Confirmada |
| N-15 | Es C-10 | Confirmada |
| B-03 | `back/apendice_D.tex:164` | Confirmada |
| B-04 | **Una tabla corta aparte**, después de los índices; no se agrega una columna a la tabla de columnas | Confirmada con esa opción (evita reajustar una tabla de 5 columnas que ya ocupa una hoja) |
| B-05 | Sección y tabla nuevas tras `apendice_D.tex:235` (nombre real `ix_notificacion_experim`) | Confirmada; juntar índices y valores por defecto en una sola sección |
| B-06 | Nota en la introducción de `apendice_D.tex:8-27`; el documento no afirma `uq_reporte_formato`, así que solo se dice que `nombreOriginal` llegó por migración | Confirmada |
| C-01 | `05_diseno.tex:704`; revisar CU-05 (`:1480`) | Confirmada; la celda debe decir «Creador» |
| C-02 a C-05 | Solo ubicadas (`04_analisis.tex:593`, `:727`, `:742`, `:1255`) | Confirmadas como ubicación; decisión del usuario |
| C-06 | `05_diseno.tex:2905-2906`, `06_desarrollo.tex:328`, RF-18 y RF-20 | Confirmada |
| C-07 | `05_diseno.tex:2966-2967`, CU-26 (`:2011`) y secuencia `:2492` | Confirmada |
| C-08 | `04_analisis.tex:244`, `05_diseno.tex:2760` y su maqueta | Confirmada |
| C-09 | `05_diseno.tex:2883` y `:2870` | Confirmada |
| C-10, C-11 | `06_desarrollo.tex:414-416` y `:418` | Confirmadas |
| C-12 | `06_desarrollo.tex:85-87` y `:152-184` | Confirmada |
| C-13 | `back/apendice_D.tex:129` | Confirmada |
| C-14 | `back/apendice_D.tex:24-26` | Confirmada; comprobar que sea la misma frase que `:199` |
| C-15 | No aplica (ya no aparece en ningún `.tex`) | Confirmada |

Observación de orquestador_latex: la regla RN-18 (`04_analisis.tex:742`) es un cambio sin commit de otra sesión y trae un «\» suelto al final de la fila; no se toca hasta que el usuario decida.

## 7. Siguiente paso

El usuario decide dos cosas: (1) cuáles de estos cambios se hacen en el documento y cuáles en el sistema (sección 3), y (2) si se aplican. Después orquestador_latex los ejecuta y yo reviso que cada línea coincida con lo registrado aquí.
