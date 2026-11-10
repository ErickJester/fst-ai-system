# Verificación del documento contra el sistema (HEAD ba903f4, v1.9.2)

Documento: `E:\fst-ai-system-latex\chapters\05_diseno.tex` líneas 2703 a 3029 (diseño de la interfaz web) y `04_analisis.tex` líneas 49 a 333 (RF-01 a RF-33).

Sistema: código en `ba903f4`, leído con `git show ba903f4:ruta` (frontend, backend-spring, worker, pipeline). Base de producción: `15489f4` (v1.0.0).

Resultado: `✔ coincide`, `≠ distinto`, `✘ no existe`, `? no pude confirmar`.

Origen de cada `≠` o `✘`: **posterior** = cambió después de 15489f4; **ya estaba** = también distinto en 15489f4.

Rutas de archivo: `frontend/…`, `backend-spring/src/main/java/com/ipn/mx/fst/…` y `worker/…` y `pipeline/…` son relativas a la raíz del repo.

---

## Bloque A · Pantallas (05_diseno.tex, 2703–3029)

| Línea doc | Lo que afirma el documento | Resultado | Lo que hace el sistema | Origen |
|---|---|---|---|---|
| 2703 | «catorce pantallas» (inicio de sesión, restablecer, primer acceso, perfil, experimentos, nuevo, experimento, grupo, cargar video, progreso, pausa, revisión, resultados, administración) | ✔ | `frontend/src/router.jsx` define 14 rutas: `/login`, `/restablecer`, `/primer-acceso`, `/perfil`, `/experimentos`, `/experimentos/nuevo`, `/experimentos/:clave`, `/experimentos/:clave/grupos/:gid`, `/experimentos/:clave/cargar`, `/experimentos/:clave/resultados`, `/analisis`, `…/analisis/:id/pausa`, `…/analisis/:id/revision`, `/admin` (`router.jsx:54-72`). | — |
| 2711 | Credenciales incorrectas: «Correo o contraseña incorrectos», sin revelar cuál campo falló (RN-01) | ✔ | `backend-spring/…/service/AuthServiceImpl.java:47` (`CREDENCIALES_INCORRECTAS`). | — |
| 2712 | Enlace de recuperación de contraseña (RF-03) | ✔ | `frontend/src/pages/LoginPage.jsx:114` («Olvidé mi contraseña») y `:69` (texto de 60 minutos). | — |
| 2713 | La pantalla comprueba que el correo tenga formato válido antes de enviar | ✔ | `frontend/src/pages/LoginPage.jsx:31` («Escribe un correo electrónico válido.»). | — |
| 2737 | Primer acceso: con contraseña temporal solo puede cambiarla o cerrar sesión (RF-05, RF-07) | ✔ | `router.jsx:34-40` (`SoloTemporal`, `Protegida`); `frontend/src/pages/PrimerAccesoPage.jsx:46-59`. | — |
| 2739 | Perfil: datos y cambio de contraseña; identificador institucional de solo lectura | ✔ | `frontend/src/pages/PerfilPage.jsx:85-86` («Identificador institucional · solo lectura», `readOnly`); `:97` («Cambiar contraseña»). | — |
| 2763–2764 | Aviso cuando un video está a menos de 7 días de borrarse (RF-24) | ✔ | `frontend/src/pages/ExperimentosPage.jsx:130-135` (texto «Los videos de … se borran el …»). | — |
| 2765 | Filtros por estado y búsqueda por nombre o tratamiento | ✔ | `ExperimentosPage.jsx:56` (filtros `estado`, `anio`, `tratamiento`) y `:165` (búsqueda). El documento no menciona el filtro por año. | — |
| 2766 | Eliminar experimento con diálogo que pide confirmación (RN-09) | ✔ | `frontend/src/components/EliminarExperimento.jsx:50-57`. | — |
| 2791 | Solo el creador o un administrador puede eliminar; el diálogo no pide nombre ni contraseña (RF-27, RN-09) | ✔ | `EliminarExperimento.jsx:9` (`puedeEliminar = user.admin \|\| exp.creador_id === user.id`) y `:12` (comentario «sin nombre ni contraseña»). | — |
| 2792 | Detalle del grupo: especímenes de cada tanda con el cilindro que ocuparon | ✔ | `frontend/src/pages/GrupoPage.jsx:103-106` (un cuadro numerado por espécimen, `especimen(i + 1)`). | — |
| 2817 | Formulario en dos pasos: datos generales y carga de video por tanda | ✔ | `frontend/src/pages/NuevoExperimentoPage.jsx:52-100` (paso 1) y `CargarVideoPage.jsx` (paso 2). | — |
| 2817–2820 | Carga de tanda: grupo o tratamiento, tanda sugerida y confirmada, de dos a cuatro cilindros, sesión (Día 1 o Día 2) y archivo | ✔ | `CargarVideoPage.jsx:391` (sesión Día 1 / Día 2); `CargarVideoPage.jsx:371` («Precargada: siguiente letra…. Editable.»); `backend-spring/…/service/VideoServiceImpl.java:238-239` («Una tanda nueva necesita entre 2 y 4 cilindros.»). | — |
| 2821 | Solo acepta `.mp4` o `.mov` en horizontal; el rechazo de formato ocurre en el cliente | ✔ | Cliente: `CargarVideoPage.jsx:242` (`/\.(mp4\|mov)$/i`) y `:417` (`accept`). Horizontal en el servidor: `VideoServiceImpl.java:130-137` (sonda y «El video es vertical»). | — |
| 2823 | Con la carga válida, el análisis inicia automáticamente (RF-12) | ✔ | `backend-spring/…/domain/valores/EstadoAnalisis.java:9` (`EN_COLA`, estado al encolar). | — |
| 2872–2873 | Progreso: tres etapas (preprocesamiento, localización de los cilindros y clasificación) y barra de progreso en porcentaje | ✔ | `frontend/src/lib/fst.js:47-49` (Preprocesamiento, Cilindros, Clasificación); `ProgresoPage.jsx:21` («localización de los N cilindros»); `ProgresoPage.jsx:151` (`role="progressbar"`, `aria-valuenow`). | — |
| 2875 | Errores previstos: el video no se pudo abrir, o no se encontraron los cilindros | ✔ | `worker/worker.py:162` («No se pudo abrir el video…»); `worker/worker.py:213` y `worker/diagnostico.py:84-85` (cilindros esperados frente a encontrados). | — |
| 2875–2876 | Si hay error: descripción según la etapa y botón de descarga del reporte de diagnóstico (RF-16) | ✔ | `ProgresoPage.jsx:271-289` («Reporte de diagnóstico.»). | — |
| 2880 | No hay botón de pausar durante el análisis | ✔ | `ProgresoPage.jsx` no contiene ningún botón «Pausar». | — |
| 2882–2883 | Tres segundos sin ver al espécimen: pausa, notificación, imagen fija y tres botones («Ajustar y continuar», «El recuadro está bien, continuar», «Cancelar análisis») | ✔ | `worker/worker.py:264` (`RACHA_PAUSA_S = 3`), `worker/worker.py:437` (notificación de pausa); `frontend/src/pages/PausaPage.jsx:139-141` (los tres botones). | — |
| 2883 | Si no responde en 72 horas, el análisis se cancela (RN-17) | ✔ | `worker/cola.py:68` (`PAUSE_TIMEOUT_HOURS`), `:77` y `:81` (cancelación y aviso). | — |
| 2883 | En la pantalla de progreso, un análisis en pausa **solo muestra** el enlace «Ajustar o cancelar →» | ≠ | `ProgresoPage.jsx:316` muestra además «Tiempo para responder: …» o «Pasaron las 72 horas: se cancela.», y `:322` el enlace. | ya estaba (ProgresoPage.jsx es idéntico en 15489f4) |
| 2885 | Reinicio desde la revisión, solo quien subió el video o un Administrador; si no, el mensaje «Solo quien subió el video o un Administrador puede reiniciar este análisis.» | ✔ | `backend-spring/…/service/RevisionServiceImpl.java:369-376` (`puedeCorregir`: administrador o creador del experimento) con el mensaje literal en `:376`. El criterio real es el creador (`:469-472`), que en la práctica es quien subió los videos. Mismo mensaje en 15489f4. | — |
| 2885–2887 | Antes de reiniciar avisa que se borrarán el resultado y la revisión, sin deshacer; al confirmar vuelve a «en cola» (RN-04) | ✔ | `frontend/src/pages/RevisionPage.jsx:552` («…no se puede recuperar»), `:278` («volvió a la cola»). | — |
| 2905 | «Cuatro pestañas»: resultados por espécimen, comparación entre grupos, línea de tiempo por minuto y bloques de 5 s | ≠ | `frontend/src/components/resultados/constantes.js` (`TABS`) tiene **cinco** pestañas: «Por espécimen», «Segundo a segundo», «Minuto a minuto», «Cada 5 segundos», «Comparación entre grupos». | posterior (15489f4 tenía cuatro) |
| 2906 | Nombres «línea de tiempo por minuto» y «bloques de 5 s» | ≠ | Las pestañas se llaman ahora «Minuto a minuto» y «Cada 5 segundos» (`constantes.js`, `TABS`). | posterior (15489f4: «Línea de tiempo por minuto» y «Bloques de 5 s») |
| 2906–2911 | Bloques de 5 s con RN-15: gana la conducta con más segundos; empate en el primer lugar, conducta activa; ninguno visto, conducta activa con matiz | ✔ | `backend-spring/…/service/BloquesDeCinco.java:14-16` (empate y ningún segundo visto) y `frontend/src/components/resultados/Bloques.jsx:69` (texto del matiz). | — |
| 2914 | Los fragmentos «no se ve» se marcan en pantalla y en el reporte de resultados | ? | En el reporte sí (`backend-spring/…/service/TablaDeReporte.java:21-22`, columna «Sin ver» y RF-22). En la pantalla no encontré un marcado específico para «no se ve» en `Bloques.jsx`. | — |
| 2917 | Vista por espécimen: tiempo por conducta con columna «Sin ver», y media, desviación estándar y varianza | ✔ | `frontend/src/components/resultados/TablaEspecimenes.jsx:10-11` (`Desviación estándar`, `Varianza`) y `:82` («Sin ver»). | — |
| 2917–2918 | CSV y XLSX incluyen «Sin ver» al final; el PDF también | ✔ | `backend-spring/…/service/TablaDeReporte.java:21-22`, `ExcelDelExperimento.java:46-47`; PDF: `backend-spring/…/repository/SegundoRepository.java:14` (los sin conducta van como «Sin ver» en el PDF). Nota: el texto de la UI que describe el CSV (`constantes.js`, `REPORTES.csv`) no nombra la columna «Sin ver». | — |
| 2919 | El panel lateral compara entre grupos la conducta elegida, por omisión la inmovilidad, con las cuatro conductas | ✔ | `frontend/src/pages/ResultadosPage.jsx` (`useState('inmovilidad')`) y `components/resultados/QueSePaso.jsx:6-9` (cuatro conductas en el selector). | — |
| 2919–2920 | Usa los videos del Día 2; los especímenes pendientes no entran al promedio (RN-12) | ✔ | `QueSePaso.jsx:54` («…promedios… del Día 2…») y textos de pendientes en `ComparacionGrupos.jsx`. | — |
| 2920–2921 | Un grupo que mezcla niveles preciso y agrupado se muestra por tanda y avisa | ✔ | `components/resultados/ComparacionGrupos.jsx:37` («…no se pueden promediar juntas…») y `QueSePaso.jsx` (texto de tandas con distinto nivel). | — |
| 2921 | Botones de descarga PDF, CSV y XLSX | ✔ | `components/resultados/BarraReportes.jsx` (botones CSV, XLSX y PDF). | — |
| 2959 | La revisión se abre solo si el análisis está completado, desde progreso o resultados (RF-32, RN-14) | ✔ | `ProgresoPage.jsx:364` (enlace solo con análisis completado y permiso) y `BarraReportes.jsx:18` («Revisar segundo a segundo»). | — |
| 2959 | El video se muestra solo al creador del experimento o a un administrador | ✔ | `RevisionServiceImpl.java:197-198` (`puedeCorregir` antes de dar el video). El texto del error dice «quien subió el video», pero el criterio es el creador. | — |
| 2960 | Tubo del espécimen destacado | ? | `RevisionPage.jsx:85` (estado `tubo`) existe; no verifiqué el resaltado visualmente. | — |
| 2960–2963 | Línea de tiempo por espécimen: vista ampliada de 40 s y de los 300 s completos | ✔ | `RevisionPage.jsx:480` («Lupa · 40 s») y `:485` (línea de tiempo de los especímenes). | — |
| 2963 | Cada segundo trae la conducta del sistema; si no coincide, se pulsa la correcta | ✔ | `RevisionPage.jsx:439` («Cada segundo ya trae la conducta que puso el sistema…»). | — |
| 2964 | Con el video corriendo, el cambio va desde 0.4 s antes hasta el siguiente cambio de color; en pausa, solo el segundo actual | ✔ | `RevisionPage.jsx:520` («…desde 0.4 s antes…; en pausa, solo el segundo actual»). | — |
| 2965 | Se revisa un espécimen a la vez | ✔ | `RevisionPage.jsx:442` (`aria-label="Especímenes"`, un espécimen seleccionado). | — |
| 2965–2967 | El avance se guarda como borrador hasta que el usuario elige «Guardar» | ≠ | El botón se llama «Guardar correcciones» (`RevisionPage.jsx:420` y `:431`). El borrador sí existe («Borrador sin guardar», `:431`). | ya estaba (15489f4 también dice «Guardar correcciones») |
| 2968 | Los totales por minuto se calculan desde las etiquetas y reflejan las correcciones | ✔ | `RevisionPage.jsx:427` («Los totales y el desglose por minuto se calculan desde los segundos guardados…»). | — |
| 2969–2971 | Panel lateral: conducta del segundo, si es del sistema o corrección, avance del espécimen, segundos antes y después, nivel al guardar (RN-13) | ✔ | `RevisionPage.jsx:520-545` (`Antes`/`Ahora`, «Nivel al guardar»). | — |
| 2971 | Se llega con el botón «Revisar segundo a segundo» de resultados | ✔ | `BarraReportes.jsx:18`. | — |
| 2986 | Vista exclusiva para `ADMINISTRADOR` | ✔ | `router.jsx:31` (`if (admin && !user.admin)`) y `backend-spring/…/config/SeguridadConfig.java:115` (`/admin/**` → `ROLE_ADMINISTRADOR`). | — |
| 2987–2988 | Lista de cuentas con correo, rol y estado; permite desactivarlas sin borrarlas | ✔ | `frontend/src/pages/AdminPage.jsx:164-178` (columnas Persona, Correo, Rol, Estado, Experimentos, Acción); `:197` («Desactivar»); `AdminService.java:22` («Nunca borra.»). | — |
| 2988–2989 | Cola de trabajos y almacenamiento; aviso al 80 % de disco y borrado de los videos más antiguos al 90 % | ✔ | `AdminPage.jsx:16` (`AVISO_DISCO_PCT = 80`); `worker/tareas_programadas.py:199` (avisa al pasar 80 % y borra al pasar 90 %); `worker/comun.py:28` (`DISK_CLEANUP_PERCENT = 90`). | — |
| 2990–2991 | Para crear una cuenta captura identificador institucional, nombre, apellidos y correo (RF-05) | ✔ | `AdminPage.jsx:218-221` (cuatro campos) y `backend-spring/…/dto/CrearCuentaPeticion.java` (`idInstitucional`, `nombre`, `apellidos`, `correo`). | — |
| 2992 | La contraseña temporal se envía por correo en texto plano, después de confirmar; se muestra una sola vez; si el envío falla, la interfaz no avisa | ✔ | Texto plano: `backend-spring/…/utilerias/service/impl/EmailServiceImpl.java:39` (`setText`). Una sola vez: `AdminPage.jsx:404` («no se volverá a mostrar»). Si falla el correo, responde 200 con la temporal: `AdminController.java:66`. | — |
| 2993 | Al editar: nombre, apellidos y correo; el rol es acción aparte; se puede desactivar, reactivar y generar contraseña temporal; identificador de solo lectura | ✔ | `backend-spring/…/dto/ModificarCuentaPeticion.java:9` (el identificador debe ser el de la URL) y `:14-33` (el DTO también acepta `rol` y `activo`; la UI lo separa en acciones). Acciones en `AdminPage.jsx:195-245`. | — |
| 2993–2994 | Impide dejar sin Administrador activo y no deja que un Administrador se desactive o quite su rol | ✔ | `backend-spring/…/service/ReglaAdministradores.java:20` («Debe existir al menos un Administrador activo.»); `AdminController.java:71` (409 «es la propia cuenta»); `AdminServiceImpl.java:181`. | — |

**Bloque A · conteo:** ✔ 44 · ≠ 4 · ✘ 0 · ? 2 (50 filas).

Las cuatro diferencias: dos son **posteriores** (pestañas: número y nombres) y dos **ya estaban** (texto de progreso en pausa y botón «Guardar correcciones»).

---

## Bloque B1 · Autenticación y gestión de usuarios (04_analisis.tex, RF-01 a RF-07)

| Línea doc | Lo que afirma el documento | Resultado | Lo que hace el sistema | Origen |
|---|---|---|---|---|
| 49 (RF-01) | Inicio de sesión con correo y contraseña; credenciales incorrectas muestran error sin revelar el campo | ✔ | `backend-spring/…/service/AuthServiceImpl.java:47` (`CREDENCIALES_INCORRECTAS`). | — |
| 55 (RF-02) | Cierre de sesión descarta el JWT en el navegador; el servidor no guarda lista de tokens cancelados; vigencia larga y configurable; acceso desde el menú de usuario | ✔ | Cierre solo en el frontend: `frontend/src/contexts/AuthContext.jsx:70-71` («el servidor no guarda sesiones»). Sin lista de revocación en `backend-spring`. Vigencia: `backend-spring/src/main/resources/application.properties:63` (`JWT_EXPIRES_HOURS`, 168 h). Menú: `frontend/src/components/Topbar.jsx:162` («Cerrar sesión»). | — |
| 62 (RF-03) | Recuperación por enlace al correo registrado; el enlace dura 60 minutos | ✔ | `application.properties:69` (`RESET_LINK_MINUTES`, 60) y `AuthServiceImpl.java:206` (correo de restablecer). | — |
| 67 (RF-04) | Dos roles; el Administrador tiene los permisos del Investigador más la gestión de cuentas; no es un tipo de usuario aparte | ✔ | El rol sale de la tabla de administradores: `backend-spring/…/config/ConvertidorJwt.java:42`; `SeguridadConfig.java:115`. Todos los investigadores ven todos los experimentos: `backend-spring/…/repository/ExperimentoRepository.java:19`. | — |
| 80 (RF-05) | Alta: identificador de 1 a 10 dígitos sin espacios a los lados; nombre, apellidos, correo; se crea como Investigador; contraseña temporal por correo; identificador no modificable; no se desactiva la última cuenta de Administrador ni se le quita el rol; nadie se desactiva a sí mismo | ✔ | Identificador: `CrearCuentaPeticion.java:17-18` (`@Size(max = 10)`, `@Pattern("[0-9]*")`) y `:35` (`trim()`: los espacios se recortan, no se rechazan). Última cuenta: `ReglaAdministradores.java:20`. Propia cuenta: `AdminController.java:71` y `AdminServiceImpl.java:181`. Correo temporal: `AdminServiceImpl.java:318`. Identificador fijo: `ModificarCuentaPeticion.java:9`. | — |
| 93 (RF-06) | El investigador actualiza nombre, apellidos y correo; la contraseña va en otro formulario; el identificador no es editable | ✔ | `frontend/src/pages/PerfilPage.jsx:85-86` (identificador `readOnly`) y `:97` (formulario «Cambiar contraseña»). | — |
| 99 (RF-07) | Solo el Administrador crea cuentas; no hay autorregistro público | ✔ | Única alta: `AdminController.java` (`POST /admin/users`). No hay endpoint público de alta en `AuthController.java`. | — |

**B1 · conteo:** ✔ 7 · ≠ 0 · ✘ 0 · ? 0.

---

## Bloque B2 · Carga de video (RF-08 a RF-12)

| Línea doc | Lo que afirma el documento | Resultado | Lo que hace el sistema | Origen |
|---|---|---|---|---|
| 106 (RF-08) | Solo `.mp4` o `.mov`; otro formato se rechaza con mensaje específico antes de subir | ✔ | Cliente: `frontend/src/pages/CargarVideoPage.jsx:242` («El archivo no es .mp4 ni .mov»). Servidor: `VideoServiceImpl.java:116` (422, «Solo se aceptan videos .mp4 o .mov.»). | — |
| 112 (RF-09) | Uno o dos videos por tanda: Día 1 de 20 min y Día 2 de 5 min; el Día 1 es opcional; el análisis toma como máximo los últimos 300 s del Día 2; primer video de tanda nueva pide grupo y tratamiento; si el grupo existe, sugiere la siguiente tanda y pide confirmación | ✔ | Sesiones: `CargarVideoPage.jsx:391` («Día 1 (20 min)», «Día 2 (5 min)»). Últimos 300 s: `worker/worker.py:44` (`MAX_SEGUNDOS = 300`) y `:489` (video completo si dura menos). Sugerencia y confirmación: `CargarVideoPage.jsx:371` y `:380`. Estado «En espera del Día 2»: `frontend/src/lib/fst.js:84`. | — |
| 112 (RF-09, segunda parte) | Cada tanda debe tener al menos un video | ? | El estado «Sin video» existe (`fst.js`, `ESTADO_TANDA`), pero no encontré una validación que impida crear una tanda sin video. | — |
| 126 (RF-10) | Validar que el archivo sea un video reproducible y horizontal antes de encolar; si falla, rechazo con código de error y mensaje | ✔ | `VideoServiceImpl.java:130-137` (sonda con `ffprobe`; vertical → «El video es vertical: se necesita un video horizontal…»), 422 como código de error. | — |
| 133 (RF-11) | Al crear el experimento: nombre único, fecha y notas opcionales; grupo, tratamiento y número de especímenes (2 a 4) se capturan al subir el primer video de cada grupo | ✔ | `frontend/src/pages/NuevoExperimentoPage.jsx:67-68` («Único en todo el sistema»); `VideoServiceImpl.java:238-239` (2 a 4); nombre repetido → 409, `ExperimentoController.java:148`. | — |
| 141 (RF-12) | El análisis del Día 2 se inicia automáticamente al completarse la carga y validación; el Día 1 no se analiza | ✔ | Estado de encolado `EstadoAnalisis.java:9` (`EN_COLA`) al validar; «Solo el Día 2 se analiza» en `CargarVideoPage.jsx:393`. | — |

**B2 · conteo:** ✔ 5 · ≠ 0 · ✘ 0 · ? 1.

---

## Bloque B3 · Análisis conductual (RF-13 a RF-18)

| Línea doc | Lo que afirma el documento | Resultado | Lo que hace el sistema | Origen |
|---|---|---|---|---|
| 149 (RF-13) | Tres etapas secuenciales: preprocesamiento, cilindros y clasificación; cada etapa con indicador de estado en la pantalla de progreso | ✔ | `backend-spring/…/domain/valores/EtapaAnalisis.java:10` (`CILINDROS`) y `Analisis.java:57` (`preprocesamiento`, `cilindros`, `clasificacion`); `frontend/src/lib/contrato.js:27` y `fst.js:47-49`; indicador en `ProgresoPage.jsx:150-151`. | — |
| 159 (RF-14) | Detecta de dos a cuatro cilindros; si no los encuentra, se detiene y genera reporte de diagnóstico | ✔ | `worker/worker.py:186-190` (etapa cilindros), `:213` (`cilindros_esperados`, `cilindros_encontrados`), `worker/diagnostico.py:84-85` (reporte con cilindros esperados frente a encontrados). | — |
| 165 (RF-15) | Progreso en tiempo real con barra de porcentaje y etapa activa; continúa aunque se cierre la pestaña; pausa hasta respuesta o 72 horas | ✔ | `ProgresoPage.jsx:151` (`aria-valuenow`); `worker/cola.py:68` (72 horas); la ejecución es del worker, no de la pestaña. | — |
| 173 (RF-16) | Si falla: descripción según la etapa, botón de reporte de diagnóstico en PDF; el investigador no puede reiniciar desde esta pantalla (RN-04) | ✔ | `ProgresoPage.jsx:271-289` (diagnóstico) y `:283` («El investigador no puede reiniciar el análisis.»). | — |
| 180 (RF-17) | Clasifica cada segundo en nado, inmovilidad o escalamiento; si ninguna llega a 0.80 pero nado + escalamiento sí, conducta activa; excluyentes; nivel agrupado o preciso | ✔ | `pipeline/segundos.py:54-66` (`regla`: argmax ≥ umbral; si no, nado + escalamiento ≥ umbral → activa), `:189` (`--umbral` por omisión 0.80). Nivel: `RevisionPage.jsx:545`. | — |
| 180 (RF-17, buceo) | En el etiquetado del entrenamiento el buceo cuenta como nado | ? | La regla del buceo aparece en la UI (`components/resultados/constantes.js`, «el buceo cuenta aquí»), pero no encontré el etiquetado de entrenamiento en el repo. | — |
| 190 (RF-18) | Asigna una conducta a cada segundo sin descartar por duración; bloques de 5 s con RN-15; «sin ver» como quinta clave; las cinco suman 100 % y 60 s por minuto | ✔ | `backend-spring/…/service/BloquesDeCinco.java:14-16`; «Sin ver» en `components/resultados/constantes.js` (`SIN_VER`) y `TablaDeReporte.java:21-22`. | — |
| 190 (RF-18, columnas) | La pantalla de bloques tiene tantas columnas como bloques tiene el espécimen más largo; los más cortos dejan celdas vacías | ? | No leí la disposición de columnas en `components/resultados/Bloques.jsx`. | — |

**B3 · conteo:** ✔ 6 · ≠ 0 · ✘ 0 · ? 2.

---

## Bloque B4 · Resultados (RF-19 a RF-22)

| Línea doc | Lo que afirma el documento | Resultado | Lo que hace el sistema | Origen |
|---|---|---|---|---|
| 207 (RF-19) | Tiempo total en segundos y porcentaje de cada conducta, y de «sin ver», por espécimen, en tabla descargable; elegir grupo completo o una tanda | ✔ | `components/resultados/TablaEspecimenes.jsx:29-46` (tiempo y porcentaje); «VER» tanda o grupo completo en `ResultadosPage.jsx`; descarga CSV/XLSX en `backend-spring/…/controller/ExperimentoController.java:105-115`. | — |
| 214 (RF-20) | Desglose de conductas por minuto por espécimen, con «sin ver» como quinta columna | ✔ | `components/resultados/DesglosePorMinuto.jsx:32` («Minuto») y columna «Sin ver» en la misma tabla. | — |
| 219 (RF-21) | Comparación entre grupos con el tiempo promedio por conducta usando los videos del Día 2; grupo mixto por tanda, sin promedio | ✔ | `components/resultados/QueSePaso.jsx:54` y `ComparacionGrupos.jsx:37`. El cálculo de promedios está en `backend-spring/…/service/ResultadosServiceImpl.java:690-700`. | — |
| 225 (RF-22) | CSV y XLSX con columnas Grupo, Tanda, Espécimen, Tiempo (min:seg), Nado, Escalamiento, Inmovilidad, Conducta activa, Sin ver; fila por espécimen y minuto; al final media, DE y varianza; PDF del reporte y reporte de diagnóstico aparte | ✔ | Encabezado: `backend-spring/…/service/TablaDeReporte.java:21-22` (mismo orden que el documento). XLSX: `ExcelDelExperimento.java:46-47`. Media, DE y varianza: `ResultadosServiceImpl.java:690-700`. Reportes: `ExperimentoController.java:82-115`. | — |

**B4 · conteo:** ✔ 4 · ≠ 0 · ✘ 0 · ? 0.

---

## Bloque B5 · Lista y gestión de experimentos (RF-23 a RF-27)

| Línea doc | Lo que afirma el documento | Resultado | Lo que hace el sistema | Origen |
|---|---|---|---|---|
| 244 (RF-23) | La lista muestra nombre, **fecha**, número de grupos, **sesión**, estado (con «sin videos» y «en espera del Día 2») y accesos a resultados y descarga; filtros por estado, año y tratamiento | ≠ | Estados: `frontend/src/lib/fst.js:83-90` y `backend-spring/…/service/ExperimentoServiceImpl.java:88` («sin videos», «en espera del Día 2»); filtros `ExperimentosPage.jsx:56`. **Pero** las columnas son: Experimento, Grupos, Especímenes, Día 2 analizados, Estado, Responsable, Retención, Acciones (`ExperimentosPage.jsx:171-172`): no hay columna de fecha ni de sesión. | ya estaba (ExperimentosPage.jsx es idéntico en 15489f4) |
| 254 (RF-24) | Aviso visible cuando un video está a menos de 7 días de borrarse, con nombre del experimento y fecha exacta | ✔ | `frontend/src/pages/ExperimentosPage.jsx:130-135` («Los videos de … se borran el …»). Cálculo: `worker/tareas_programadas.py:143` (`now() + interval '7 days'`). | — |
| 265 (RF-25) | Borrado automático 30 días después de un análisis exitoso, con aviso en la interfaz antes; si el disco pasa de 90 %, se adelanta a los videos más antiguos (RNF-09) | ✔ | `worker/tareas_programadas.py:78` (`interval '30 days'`), `:143` (aviso de 7 días), `:199` (80 % avisa, 90 % borra los más antiguos); `worker/comun.py:28`. | — |
| 272 (RF-26) | Resultados y reportes se conservan indefinidamente, aunque el video se haya borrado | ✔ | El borrado afecta solo al video (`worker/tareas_programadas.py`, consultas sobre `VIDEO`); el texto «Resultados y reportes se conservan siempre» está en `ExperimentosPage.jsx:136`. | — |
| 277 (RF-27) | Eliminar un experimento propio desde la lista o el detalle; el Administrador cualquiera; acción permanente y con aviso de confirmación | ✔ | `frontend/src/components/EliminarExperimento.jsx:9` (`puedeEliminar`), `ExperimentoPage.jsx:126-128` (acción en el detalle), `ExperimentosPage.jsx:136` («El borrado es irreversible»). | — |

**B5 · conteo:** ✔ 4 · ≠ 1 · ✘ 0 · ? 0.

---

## Bloque B6 · Administración (RF-28 a RF-30)

| Línea doc | Lo que afirma el documento | Resultado | Lo que hace el sistema | Origen |
|---|---|---|---|---|
| 285 (RF-28) | Lista de usuarios con correo, si es Administrador o Investigador, estado y número de experimentos; filtros por rol y estado | ✔ | `frontend/src/pages/AdminPage.jsx:164-178` (filtros Rol y Estado; columnas Correo, Rol, Estado, Experimentos). | — |
| 292 (RF-29) | Monitoreo: porcentaje de disco, GB libres, tamaño de la cola, número de análisis activos y tiempo estimado para vaciar la cola con un promedio fijo por video; alerta por encima del 80 % | ✔ | `AdminPage.jsx:16` (`AVISO_DISCO_PCT = 80`), `:18` (`MIN_POR_VIDEO = 10`), `:335` (resaltado y aviso al pasar 80 %), `:342` (liberable en 7 días). El conteo de «análisis activos» no lo localicé aparte; la cola sí. | — |
| 299 (RF-30) | El Administrador ve y gestiona los experimentos de cualquier investigador | ✔ | `AdminPage.jsx:280-281` («Como Administrador puedes eliminar cualquier experimento…») y `ExperimentoRepository.java:19` (lista de todos los experimentos). | — |

**B6 · conteo:** ✔ 3 · ≠ 0 · ✘ 0 · ? 0.

---

## Bloque B7 · Estadísticas y revisión (RF-31 y RF-32)

| Línea doc | Lo que afirma el documento | Resultado | Lo que hace el sistema | Origen |
|---|---|---|---|---|
| 304 (RF-31) | Media, desviación estándar y varianza por conducta entre especímenes del grupo, agregando tandas; por tanda o por grupo; en CSV/XLSX; DE muestral (n−1); comparación con cuatro conductas y sin promediar grupos mixtos | ✔ | `backend-spring/…/service/ResultadosServiceImpl.java:690-700` (n − 1, «MUESTRALES»); `components/resultados/TablaEspecimenes.jsx:10-11` (nota «muestral, n − 1»); comparación con cuatro conductas en `ComparacionGrupos.jsx`; grupo mixto en `:37`. | — |
| 315 (RF-32) | Pantalla de revisión cuando el análisis está completado; video con línea de tiempo por espécimen; corrección por el creador o el administrador; copia del video en H.264 recortada a los segundos analizados; origen de cada conducta (máquina, confirmada, corregida, dudosa); totales por minuto desde las etiquetas; nivel recalculado al guardar; reiniciar borra la revisión y avisa | ✔ | Permiso: `RevisionServiceImpl.java:469-472` (`puedeCorregir`: administrador o creador). H.264 recortado: `worker/worker.py:498` (`libx264`, `-t MAX_SEGUNDOS`). Origen: `backend-spring/…/domain/valores/OrigenSegundo.java:9-12`. Totales: `RevisionPage.jsx:427`. Reinicio con aviso: `RevisionPage.jsx:552`. Nivel al guardar: panel `RevisionPage.jsx:545` (el recálculo en el backend no lo leí a fondo). | — |

**B7 · conteo:** ✔ 2 · ≠ 0 · ✘ 0 · ? 0.

---

## Bloque B8 · Notificaciones (RF-33)

| Línea doc | Lo que afirma el documento | Resultado | Lo que hace el sistema | Origen |
|---|---|---|---|---|
| 333 (RF-33) | Avisos en el panel de la barra superior y por correo cuando el análisis termina, queda en pausa, se detiene por error; al administrador, cuando el disco pasa 80 %; al investigador, cuando un video se borra antes de 30 días; correos desde una cuenta configurable; «Cancelado» solo por plazo; consulta al entrar y cada 30 s; el borrado a los 30 días no notifica | ✔ | Panel: `frontend/src/components/Topbar.jsx:116` (campana «Notificaciones»); cada 30 s: `Topbar.jsx:13` (`CADA_MS = 30000`). Correos del worker: `worker/cola.py:84`, `:144` (`enviar_correos`); cancelado por plazo: `cola.py:79`; error: `cola.py:138`; pausa: `worker.py:437`; completado: `worker.py:564`. Admin al 80 %: `worker/tareas_programadas.py:199`. Borrado antes de 30 días: `tareas_programadas.py:267`. Cuenta configurable: `application.properties:88-89`. Borrado a los 30 días sin aviso: `tareas_programadas.py:67-82` (no hay notificación). | — |
| 333 (RF-33, envío fallido) | Si el envío falla, el aviso permanece en el panel | ? | No leí el manejo de error de `enviar_correos` ni de la tabla de notificaciones para confirmar que el aviso queda en el panel. | — |

**B8 · conteo:** ✔ 1 · ≠ 0 · ✘ 0 · ? 1.

---

## Resumen

| Bloque | ✔ | ≠ | ✘ | ? | Filas |
|---|---|---|---|---|---|
| A · Pantallas (05_diseno.tex 2703–3029) | 44 | 4 | 0 | 2 | 50 |
| B1 · Autenticación y usuarios (RF-01 a RF-07) | 7 | 0 | 0 | 0 | 7 |
| B2 · Carga de video (RF-08 a RF-12) | 5 | 0 | 0 | 1 | 6 |
| B3 · Análisis conductual (RF-13 a RF-18) | 6 | 0 | 0 | 2 | 8 |
| B4 · Resultados (RF-19 a RF-22) | 4 | 0 | 0 | 0 | 4 |
| B5 · Lista y gestión (RF-23 a RF-27) | 4 | 1 | 0 | 0 | 5 |
| B6 · Administración (RF-28 a RF-30) | 3 | 0 | 0 | 0 | 3 |
| B7 · Estadísticas y revisión (RF-31 y RF-32) | 2 | 0 | 0 | 0 | 2 |
| B8 · Notificaciones (RF-33) | 1 | 0 | 0 | 1 | 2 |
| **Total** | **76** | **5** | **0** | **6** | **87** |

Diferencias (`≠`), con origen:

| Línea doc | Diferencia | Origen |
|---|---|---|
| 05_diseno.tex:2883 | Pausa en progreso: el documento dice que solo muestra el enlace; el sistema también muestra el plazo | ya estaba |
| 05_diseno.tex:2905 | «Cuatro pestañas»; el sistema tiene cinco | posterior |
| 05_diseno.tex:2906 | Nombres «Línea de tiempo por minuto» y «Bloques de 5 s»; ahora «Minuto a minuto» y «Cada 5 segundos» | posterior |
| 05_diseno.tex:2965–2967 | El documento dice «Guardar»; el botón dice «Guardar correcciones» | ya estaba |
| 04_analisis.tex:244 (RF-23) | La lista no tiene columnas de fecha ni de sesión | ya estaba |

## Observaciones que no son diferencias con el documento

- `components/resultados/QueSePaso.jsx:54` y el resumen del experimento usan la palabra «animales» («promedios de los animales»). La pantalla debe decir «especímenes», como el resto de la interfaz.
- `RevisionServiceImpl.java:198`, `:267` y `:376` dicen «Solo quien subió el video…», pero el criterio que se aplica es el creador del experimento (`:469-472`). El texto y el criterio no coinciden, aunque en la práctica sean la misma persona.
- RF-05 dice «sin espacios a los lados»: el sistema recorta los espacios en silencio (`CrearCuentaPeticion.java:35`), no los rechaza.
- `components/resultados/constantes.js` (`REPORTES.csv`) describe el CSV sin mencionar la columna «Sin ver», aunque el reporte sí la incluye.
- RF-22 pide «una fila por espécimen y por minuto»: el encabezado lo confirma, pero no abrí un archivo CSV generado para revisar las filas ni la suma de 60 s.

## Lo que no pude confirmar

- Que cada tanda tenga al menos un video (RF-09): no encontré la validación.
- La disposición de columnas de la pantalla de bloques de 5 s (RF-18).
- El etiquetado de entrenamiento con buceo como nado (RF-17): está fuera del repo.
- Que el aviso de notificación permanezca en el panel si el correo falla (RF-33).
- El resaltado visual del tubo en la revisión (05_diseno.tex:2960).
- El recálculo del nivel de clasificación en el backend al guardar (RF-32): solo vi el panel.
- Los valores numéricos de un CSV o XLSX generado (suma de 60 s por minuto y 100 %).
- Los números de línea son de `ba903f4`; si cambia el código, cambian.
