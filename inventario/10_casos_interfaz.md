# 10 · Casos comprobados por la interfaz (frontend)

**Qué cuenta:** las 344 pruebas de la interfaz. Son las 348 de `npm test` menos las 4 de `cerrarSesion.test.jsx`, que ya están tabuladas aparte. Cada prueba cuenta **una sola vez**, en el área donde el caso se comprueba como caso principal. Algunas pruebas tocan también otra área; se anotan en la fila, pero no se suman dos veces.

**Cómo se verificó el total:** corrida de `npx vitest run --reporter=json` el mismo día: 29 archivos, 348 casos, 348 pasan. Sin `cerrarSesion.test.jsx` quedan 28 archivos y 344 casos. Por área: 1 = 39 · 2 = 60 · 3 = 41 · 4 = 135 · 5 = 11 · 6 = 8 · 7 = 33 · transversal = 17. Suma: **344**. Coincide con el reporte.

**Qué usa cada caso:**
- **datos de ejemplo**: la app completa o su capa de datos de ejemplo (`services/mocks`, con `VITE_USE_MOCKS=true`). No hay backend.
- **contrato simulado**: un backend falso que responde con la forma de la guía (§6).
- **componente solo**: un componente o una función aislada, con datos fijos y los servicios sustituidos por `vi.mock`.

---

## Área 1 · Cuentas y sesión

| Caso comprobado | Pruebas | Qué usa |
|---|---|---|
| Entrar con una cuenta de ejemplo y llegar a la lista de experimentos | 1 | datos de ejemplo |
| Marcar un correo que no tiene forma de correo | 1 | datos de ejemplo |
| Sin sesión, cualquier dirección lleva al inicio de sesión | 1 | datos de ejemplo |
| Cuenta nueva con contraseña temporal pasa primero por el primer acceso | 1 | datos de ejemplo |
| El inicio de sesión enlaza el aviso de privacidad y los términos | 1 | datos de ejemplo |
| Recuperar la contraseña con el enlace y entrar con la nueva | 1 | datos de ejemplo |
| El enlace de recuperación: responde igual exista o no la cuenta; sirve una sola vez; uno vencido o inventado no sirve | 3 | datos de ejemplo |
| Primer acceso: validaciones (temporal incorrecta, error del servidor, contraseña de más de 72 bytes, casilla de términos obligatoria, aviso y términos sin sesión o con temporal pendiente) | 7 | datos de ejemplo |
| Crear y editar cuentas en un diálogo que se cierra con Escape; filtrar cuentas por rol y estado | 2 | datos de ejemplo |
| Restablecer la contraseña temporal pide confirmación y se muestra una sola vez; una cuenta desactivada solo ofrece reactivarla | 2 | datos de ejemplo |
| Crear una cuenta: la temporal se muestra una sola vez; identificador repetido marca su campo; el identificador admite máximo 10 caracteres | 3 | datos de ejemplo |
| Perfil: nombre, apellidos y correo se guardan sin espacios de sobra | 1 | datos de ejemplo |
| El token de sesión se guarda de forma que sobrevive a cerrar la pestaña | 1 | datos de ejemplo |
| Cerrar sesión borra los borradores de la revisión y la barra muestra la versión | 1 | datos de ejemplo |
| Entrar con cualquier contraseña no vacía; una cuenta desactivada no entra; la cuenta llega con la forma de la guía | 3 | datos de ejemplo |
| Cuenta creada: entra solo con la temporal y debe cambiarla | 1 | datos de ejemplo |
| Restablecer la temporal invalida la contraseña anterior y deja el cambio pendiente con una contraseña distinta | 2 | datos de ejemplo |
| Restablecer la temporal da los mismos 404 y 409 que el backend; no se pueden dar de alta dos cuentas con el mismo correo | 2 | datos de ejemplo |
| No se puede desactivar ni quitar el rol al único Administrador; con dos, uno sí puede dejar de serlo; editar no toma el correo de otra cuenta | 3 | datos de ejemplo |
| Editar una cuenta no manda el identificador y manda el rol de la guía | 1 | contrato simulado |
| Correo de cualquier dominio se acepta como correo | 1 | componente solo |

**Suma del área: 39**

---

## Área 2 · Experimentos y carga de video

| Caso comprobado | Pruebas | Qué usa |
|---|---|---|
| Solo quien creó el experimento ve el formulario de subida; un Administrador no | 1 | datos de ejemplo |
| Elegir el grupo con el teclado, confirmar la tanda y encolar el video | 1 | datos de ejemplo |
| Un video de Día 1 se guarda sin encolar análisis y vuelve al experimento | 1 | datos de ejemplo |
| Avisa si la tanda ya tiene el video de esa sesión y no deja guardar | 1 | datos de ejemplo |
| Rechaza antes de subir un archivo más grande que 2048 MB | 1 | datos de ejemplo |
| «Terminar» sin subir nada vuelve al experimento | 1 | datos de ejemplo |
| «Agregar otra tanda» deja el primer formulario como estaba y el nuevo trae el grupo y la tanda que sigue, ya confirmada | 2 | datos de ejemplo |
| Formularios nuevos: si el anterior no tiene grupo, el nuevo nace vacío; en un grupo nuevo, la otra tanda hereda el nombre y la tanda B sin pedir otra vez el tratamiento; si se cambia el grupo, deja de heredar la tanda | 3 | datos de ejemplo |
| «Quitar este formulario» deja solo el otro, y con uno solo ya no se ofrece | 1 | datos de ejemplo |
| Dos formularios del mismo grupo no proponen la misma tanda | 1 | datos de ejemplo |
| Dos tandas de un grupo nuevo se suben con un solo botón, en orden | 2 | datos de ejemplo |
| La misma tanda y sesión en dos formularios se avisa y el botón no se habilita | 1 | datos de ejemplo |
| Si la primera tanda falla, la segunda no se sube; al corregirla, el mismo botón termina | 1 | datos de ejemplo |
| Un solo formulario: al guardar sale a Análisis en curso y el siguiente formulario empieza vacío | 1 | datos de ejemplo |
| El navegador solo revisa la extensión; el servidor rechaza un video vertical y se muestra su motivo | 1 | datos de ejemplo |
| Eliminar un experimento desde su detalle: pide solo confirmación; no se puede con un análisis procesando | 1 | datos de ejemplo |
| Eliminar desde la lista: solo los experimentos propios (o como Administrador) | 1 | datos de ejemplo |
| Quién puede eliminar una tanda; una tanda sin identificador no se ofrece | 2 | componente solo |
| Eliminar una tanda pide confirmación y no borra hasta confirmar; el foco empieza en «Cancelar» | 2 | componente solo |
| Confirmar una tanda la elimina; un 409 o un 403 del servidor se muestran y no cierran el diálogo | 3 | componente solo |
| Si es la última tanda del grupo, avisa que el grupo también se elimina; al eliminar, navega o recarga según el caso | 3 | componente solo |
| En el detalle del grupo: avisa cuando el video de una tanda ya se borró | 1 | datos de ejemplo |
| En el detalle del grupo: lleva a los resultados del grupo completo o de cada tanda | 1 | datos de ejemplo |
| Lista vacía invita a crear el primero; paginación de 20 en 20 con el total del servidor | 2 | datos de ejemplo |
| Filtrar por estado, fecha y tratamiento y avisar de los videos por borrarse; buscar sin acentos esperando a terminar de escribir | 2 | datos de ejemplo |
| Con filtros que no dejan nada, lo dice y el resumen queda intacto | 1 | datos de ejemplo |
| La lista (datos de ejemplo) tiene la forma de las pantallas; paginación y resumen | 2 | datos de ejemplo |
| Filtrar por estado, año y tratamiento; buscar sin acentos; el resumen trae años, tratamientos y avisos; respuesta sin resumen | 3 | datos de ejemplo |
| El ejemplo del detalle de la guía se pasa a la forma de las pantallas | 1 | datos de ejemplo |
| Crear un experimento asigna el número siguiente y empieza sin grupos; el nombre es único en todo el sistema | 2 | datos de ejemplo |
| Subir el primer video crea la tanda, la encola y actualiza la lista | 1 | datos de ejemplo |
| El video de Día 1 se guarda pero no se analiza; una tanda lleva un solo video por sesión | 2 | datos de ejemplo |
| El servidor rechaza un video vertical o que no se puede reproducir | 1 | datos de ejemplo |
| Solo quien creó el experimento sube videos; un Administrador no | 1 | datos de ejemplo |
| Un grupo nuevo necesita tipo y tratamiento; uno existente sigue con su siguiente tanda | 1 | datos de ejemplo |
| El estado del experimento sigue la precedencia (con error manda sobre las demás tandas) | 1 | datos de ejemplo |
| Eliminar: solo quien lo creó o un Administrador, sin pedir nombre ni contraseña | 1 | datos de ejemplo |
| No se elimina con un análisis procesando; los que están en cola salen de la cola | 1 | datos de ejemplo |
| Avisa con la fecha exacta los videos que se borran en 7 días o menos | 1 | datos de ejemplo |
| Cancelar la subida no registra la tanda ni la encola | 1 | datos de ejemplo |
| El nombre del video es el que subió el usuario o «nombre no registrado» | 1 | componente solo |
| La búsqueda de la lista no distingue acentos ni mayúsculas | 1 | componente solo |
| Cada experimento con resultados tiene «Ver resultados» y PDF; no hay CSV ni XLSX sueltos en la lista | 1 | datos de ejemplo |

**Suma del área: 60**

---

## Área 3 · Progreso del análisis, pausa, cola y diagnóstico

| Caso comprobado | Pruebas | Qué usa |
|---|---|---|
| Un análisis completado habilita «Ver resultados» y «Revisar segundo a segundo»; quien no subió el video no ve esto último | 2 | datos de ejemplo |
| Un análisis cancelado dice el motivo solo si el servidor lo manda | 1 | datos de ejemplo |
| Pantalla de pausa: muestra la imagen con el recuadro, el tiempo que queda y deja ajustar | 1 | datos de ejemplo |
| Cancelar una pausa pide confirmación; después la pausa ya no está | 1 | datos de ejemplo |
| «El recuadro está bien, continuar» vuelve el análisis a la cola sin cambiar el recuadro | 1 | datos de ejemplo |
| Ajustar sin mover el recuadro muestra el error en la misma pantalla con los tres botones | 1 | datos de ejemplo |
| Sin servidor, la cola queda fija con cuatro estados (procesando, en cola, en pausa y con error) | 1 | datos de ejemplo |
| Los análisis completados recientemente salen del más nuevo al más viejo | 1 | datos de ejemplo |
| Sin servidor, el PDF de diagnóstico no existe (lo genera el worker) | 1 | datos de ejemplo |
| Un análisis en pausa sale de la cola y avisa; solo quien subió el video o un Administrador ve la pausa | 2 | datos de ejemplo |
| Ajustar el recuadro devuelve el análisis a la cola por delante de los que llegaron después; cancelar lo termina y una segunda vez da error | 2 | datos de ejemplo |
| Un recuadro casi igual al automático da error con mensaje; con 4 px ya cuenta como ajuste | 1 | datos de ejemplo |
| Continuar sin cambiar el recuadro vuelve a la cola; otra vez da error | 1 | datos de ejemplo |
| Tiempo del análisis: formato m:ss y h:mm:ss; segundos desde un reloj que va atrasado nunca es negativo; el equipo es su nombre o su CPU; tiempo de cada etapa | 4 | componente solo |
| Plazo de la pausa: en horas y minutos, o «menos de 1 min» | 2 | componente solo |
| Porcentaje de cada etapa en Progreso: hecha 100, en curso el del servidor, pendiente 0; la barra general suma 100 | 3 | componente solo |
| Datos de tiempos: etapas con las claves del frontend; total y equipo; análisis viejo sin registro da null | 4 | componente solo |
| La imagen de control del análisis con error solo se pide si el servidor la tiene; se pide una vez y se muestra con su pie | 2 | componente solo |
| La imagen de control se libera al salir y, si no se puede pedir, no muestra nada | 2 | componente solo |
| Diagnóstico: reintenta como mucho 15 veces y luego muestra un texto claro y «Volver a intentar» | 1 | componente solo |
| Diagnóstico: si el PDF ya no existe, no reintenta y lo dice | 1 | componente solo |
| Diagnóstico: «Volver a intentar» pide el PDF y lo descarga si ya está | 1 | componente solo |
| Modo desarrollador: CSV de una tanda y ZIP del grupo completo; sin modo no hay botón; el clic avisa y deja el botón ocupado | 3 | componente solo |
| Modo desarrollador: cronómetro y tiempo de cada etapa; sin modo, solo la barra de avance | 2 | componente solo |

**Suma del área: 41**

---

## Área 4 · Resultados, gráficas y reportes

| Caso comprobado | Pruebas | Qué usa |
|---|---|---|
| Pantalla de la tanda: muestra los especímenes y la comparación entre grupos | 1 | datos de ejemplo |
| Grupo completo: todas sus tandas completadas, con estadísticos recalculados | 1 | datos de ejemplo |
| En el grupo completo, las tandas sin análisis completado se señalan como faltantes | 1 | datos de ejemplo |
| Cada conducta con su porcentaje y desglose por minuto de cualquier espécimen | 1 | datos de ejemplo |
| Los bloques de 5 s los calcula el servidor; sin él, la sección lo explica | 1 | datos de ejemplo |
| Los botones de reporte no generan archivos en el modo de ejemplo; explican lo que hará el servidor | 1 | datos de ejemplo |
| El resumen del experimento dice qué se logró y qué falta | 1 | datos de ejemplo |
| «¿Qué pasó?» compara con el control y dice en palabras comunes los casos de nivel mixto y sin datos | 1 | datos de ejemplo |
| Las dos descargas del resumen: en el modo de ejemplo no hay archivo y lo dice | 1 | datos de ejemplo |
| «Ver el detalle» abre la primera tanda lista en la vista elegida | 1 | datos de ejemplo |
| Segundo a segundo con grupo completo pide elegir una tanda (dos casos) | 2 | datos de ejemplo |
| El selector de grupo cambia de grupo y conserva la vista; «Grupo completo» también | 2 | datos de ejemplo |
| El detalle explica qué es una tanda y qué significa el nivel preciso; el resumen dice qué es el Día 2 | 2 | datos de ejemplo |
| Datos de resultados: cada tanda terminada tiene su grupo y sus especímenes; una en cola o con error no tiene resultados | 2 | datos de ejemplo |
| Sin servidor, los reportes no tienen archivo | 1 | datos de ejemplo |
| Comparación entre grupos: medias, pendientes y grupos que mezclan niveles; cada espécimen trae las cuatro conductas y el nivel agrupado o preciso | 2 | datos de ejemplo |
| Sin servidor no hay bloques de 5 s; el desglose por minuto suma como mucho 60 s por minuto | 2 | datos de ejemplo |
| Descarga con el nombre que manda el servidor; media y varianza muestrales (n − 1); segundos en min:seg | 3 | componente solo |
| Primera tanda con el Día 2 analizado, o nada si no hay; nombres de conductas como en pantalla; bloques de 5 s: final y columnas | 5 | componente solo |
| Porcentajes de cada fila: el análisis guarda 300 s; un video de 360 s con 300 guardados suma 100; con división no exacta suma 100; una fila sin segundos da ceros | 4 | componente solo |
| Galería de gráficas: detalles cerrados y avisos visibles; barra de cada grupo con su media ± DE; etiquetas en orden; grupos sin datos y mixtos no se promedian; sin DE, «no aplica»; conducta activa fuera de las barras; notas y tabla con los mismos números | 7 | componente solo |
| Galería: se pueden elegir las seis gráficas; «Ver varias» hasta tres sin repetir ids; las flechas no cambian la gráfica dentro de un campo; «Ver varias» pide marcar y muestra el máximo | 4 | componente solo |
| Galería: la elección se recuerda; sin almacenamiento funciona igual; el rayado va dentro de su svg; dos gráficas no repiten ids | 4 | componente solo |
| Galería: etiquetas largas caben en su columna, el nombre completo queda en el título; el nombre se parte en renglones; la leyenda muestra el patrón; las flechas dan la vuelta; la escala es de 300 s y solo crece si hace falta | 6 | componente solo |
| Galería: sin fallos de accesibilidad con tres grupos, uno sin datos y uno mixto | 1 | componente solo |
| G2 curva por minuto: una línea por grupo; un minuto sin especímenes corta la línea; sin DE no hay banda; minuto incompleto marcado; grupos sin datos y mixtos no se dibujan; selector de conducta; sin fallos de accesibilidad | 7 | componente solo |
| G3 barras por minuto: una barra por grupo y minuto; sin DE no hay barra de error; todas las DE nulas; minuto incompleto con rayado; sin datos y mixto; patrones con prefijo; selector de conducta; sin fallos de accesibilidad | 8 | componente solo |
| G4 barra apilada: una columna por grupo; cinco categorías incluida «Sin ver»; escala según la duración real; segmentos con número; null es «sin datos», nunca cero; sin datos y mixto; pendientes con su n; notas y descarga; sin fallos de accesibilidad | 9 | componente solo |
| G5 variabilidad: CV en la barra y varianza en la tabla; grupos sin datos y mixtos marcados; seis grupos sin etiquetas (el dato queda en el título); CV null como «no aplica»; todos null sin eje; título y nota de variación entre especímenes; mixto con una fila por tanda; pendientes con su n; notas y descarga; sin fallos de accesibilidad | 12 | componente solo |
| G6 mapa por minuto: celda con número y rango de 10 s; sin datos rayado sin número; mixto sin celdas; incompleto con borde discontinuo y cursiva; límites 0, 10 y 60; número en blanco o negro según el rango; leyenda de seis rangos; selector de conducta; sin fallos de accesibilidad | 10 | componente solo |
| Segundo a segundo: cambio de espécimen; conducta vacía se dice «Sin ver» con su borde; origen traducido; origen desconocido o vacío dice «Sin dato»; la leyenda de «Sin ver» lleva borde; un segundo que no viene queda vacío; tiempo m:ss; sin especímenes lo dice; la tabla tiene caja con desplazamiento y se puede enfocar; sin fallos de accesibilidad | 10 | componente solo |
| Botón «Descargar imagen»: estado normal; mientras prepara queda deshabilitado; con éxito baja un PNG con el nombre; con error muestra el aviso y vuelve a quedar habilitado | 4 | componente solo |
| Exportar un gráfico a PNG: rechaza lo que no es un svg; resuelve un PNG; no toca el original; tamaño según el viewBox y la escala; dibuja el fondo; copia los estilos calculados; rechaza sin contexto de canvas | 8 | componente solo |
| Datos de las gráficas (de ejemplo): grupos en orden; mixto sin estadísticos; sin especímenes va con n = 0; faltantes con su estado; cinco categorías suman la duración; minutos con incompleto; n − 1 con dos especímenes | 7 | datos de ejemplo |
| Segundo a segundo (de ejemplo): trae duración, grupo, letra y segundos; «sin ver» es nulo y las conductas van con el nombre de la base; tanda sin análisis da 404 | 3 | datos de ejemplo |

**Suma del área: 135**

---

## Área 5 · Revisión segundo a segundo

| Caso comprobado | Pruebas | Qué usa |
|---|---|---|
| En pausa, una tecla cambia solo ese segundo; guardar deja la revisión | 1 | datos de ejemplo |
| Reiniciar la revisión pide confirmación y lleva a Análisis en curso | 1 | datos de ejemplo |
| Sin permiso, la revisión lo explica | 1 | datos de ejemplo |
| En el detalle del grupo, «Revisar segundo a segundo» no aparece a quien no subió el video ni es Administrador; sí aparece a quien lo subió | 2 | datos de ejemplo |
| El video de la revisión se pide con la sesión y se pone en la dirección con un permiso temporal, no con la sesión | 1 | datos de ejemplo |
| Sin video, la revisión se consulta pero no se corrige ni se reinicia | 1 | datos de ejemplo |
| La revisión solo se abre para un análisis completado y para quien subió el video o un Administrador | 1 | datos de ejemplo |
| Guardar cambia los segundos, recalcula los totales y marca la revisión | 1 | datos de ejemplo |
| Reiniciar borra la revisión y vuelve a encolar el análisis | 1 | datos de ejemplo |
| Los cambios sin guardar se escriben en el navegador al ocultar la pestaña o salir | 1 | datos de ejemplo |

**Suma del área: 11**

---

## Área 6 · Notificaciones y correo

| Caso comprobado | Pruebas | Qué usa |
|---|---|---|
| El punto y el número de la campana son los de no leídas, aunque la lista traiga solo leídas | 1 | componente solo |
| Sin no leídas no hay punto ni número | 1 | componente solo |
| Al marcar una como leída, el número baja en seguida y se vuelve a pedir al servidor | 1 | componente solo |
| Con la pestaña oculta no se consulta; al volver a verla se consulta en seguida | 1 | componente solo |
| Con la pestaña visible, se consulta cada 30 segundos | 1 | componente solo |
| El título y el enlace de cada notificación salen de su tipo y de su experimento | 1 | datos de ejemplo |
| Marcar todas como leídas hace una petición por cada una | 1 | datos de ejemplo |
| La alerta de disco solo llega a los Administradores | 1 | datos de ejemplo |

**Suma del área: 8**

---

## Área 7 · Accesibilidad, pantallas legales y contratos

| Caso comprobado | Pruebas | Qué usa |
|---|---|---|
| Sin fallos de accesibilidad (axe) en las 12 pantallas de la lista de accesibilidad (inicio de sesión, lista de experimentos, nuevo, experimento, grupo, cargar video, análisis, pausa, resultados, revisión, perfil y administración) | 12 | datos de ejemplo |
| Sin fallos de accesibilidad en el resumen del experimento | 1 | datos de ejemplo |
| Aviso de privacidad: título y secciones sin sesión; datos, usos y protección; enlace «Volver»; sin fallos de accesibilidad | 4 | componente solo |
| Términos de uso: título y secciones sin sesión; no emite diagnóstico y los videos se borran a los 30 días; enlace «Volver»; sin fallos de accesibilidad | 4 | componente solo |
| Forma de las respuestas y peticiones de la guía: login, lista y detalle, resultados de una tanda, revisión y notificaciones | 6 | contrato simulado |
| Subir un video manda una sola petición multipart con los campos de la guía | 1 | contrato simulado |
| Equivalencias guía ↔ pantalla: cada tabla va y vuelve sin perder valores; valores de la guía; la letra de la tanda es su orden | 3 | contrato simulado |
| Un error se lee del campo de error del envoltorio; las respuestas JSON se desenvuelven y los archivos no | 2 | componente solo |

**Suma del área: 33**

> El caso «axe en 12 pantallas» es la prueba de accesibilidad de la lista; las 15 pantallas revisadas en total, y las que no, están en la parte 2.

---

## Transversal (no es un área de 09)

Estos casos no encajan en las áreas de 09. Los pongo aparte para que la suma dé 344.

| Caso comprobado | Pruebas | Qué usa |
|---|---|---|
| Fechas: la hora del servidor se lee en la hora local; fecha corta sin depender de la zona horaria; rango de fechas del mismo mes, de meses distintos o del mismo día | 3 | componente solo |
| Consultas en segundo plano: más seguidas con análisis activos y más espaciadas sin ellos; si una consulta falla, se conservan los datos anteriores | 2 | componente solo |
| Consultas que se detienen con la pestaña oculta y se retoman al volver | 2 | componente solo |
| Versión en la esquina: se ve siempre, con la del backend; una sola vez; si no hay versión del backend, la del paquete | 3 | componente solo |
| Franja de modo desarrollador: dice solo «Modo desarrollador», sin repetir la versión; no sale sin modo; sin proveedor tampoco | 4 | componente solo |
| El modo desarrollador lo lee el backend una sola vez; si el backend no contesta, el modo queda apagado; una respuesta sin el campo es modo apagado | 3 | componente solo |

**Suma transversal: 17**

---

## Total

| Área | Suma |
|---|---|
| 1 · Cuentas y sesión | 39 |
| 2 · Experimentos y carga de video | 60 |
| 3 · Progreso, pausa, cola y diagnóstico | 41 |
| 4 · Resultados, gráficas y reportes | 135 |
| 5 · Revisión segundo a segundo | 11 |
| 6 · Notificaciones y correo | 8 |
| 7 · Accesibilidad, pantallas legales y contratos | 33 |
| Transversal | 17 |
| **Total** | **344** |

**Cuadra con el reporte: sí, exactamente 344.** Por archivo, con el número de casos de cada uno:

| Archivo | Casos | Área |
|---|---|---|
| pantallas.test.jsx | 80 | 1 (23), 2 (28), 3 (7), 4 (16), 5 (5), 7 (1) |
| services/servicios.test.js | 60 | 1 (15), 2 (19), 3 (9), 4 (7), 5 (5), 6 (3), 7 (2) |
| components/resultados/graficas/GaleriaGraficas.test.jsx | 22 | 4 |
| components/resultados/graficas/G5Variabilidad.test.jsx | 12 | 4 |
| accesibilidad.test.jsx | 12 | 7 |
| components/resultados/graficas/G6MapaPorMinuto.test.jsx | 10 | 4 |
| components/resultados/SegundoASegundo.test.jsx | 10 | 4 |
| eliminarTanda.test.jsx | 10 | 2 |
| lib/fst.test.js | 23 | 1 (1), 2 (2), 3 (9), 4 (8), transversal (3) |
| components/resultados/graficas/G4ApiladaPorGrupo.test.jsx | 9 | 4 |
| modo.test.jsx | 15 | 3 (5), transversal (10) |
| components/resultados/graficas/G3BarrasPorMinuto.test.jsx | 8 | 4 |
| lib/svgAPng.test.js | 8 | 4 |
| pages/AvisosLegales.test.jsx | 8 | 7 |
| campanita.test.jsx | 5 | 6 |
| components/resultados/graficas/G2CurvaPorMinuto.test.jsx | 7 | 4 |
| services/contrato.test.js | 7 | 7 |
| services/graficas.test.js | 7 | 4 |
| lib/porcentajes.test.js | 4 | 4 |
| services/tiempos.test.js | 4 | 3 |
| control.test.jsx | 4 | 3 |
| components/resultados/graficas/BotonDescargarImagen.test.jsx | 4 | 4 |
| borradores.test.jsx | 2 | 5 (1), 2 (1) |
| hooks/useAsync.test.jsx | 2 | transversal |
| hooks/usePolling.test.jsx | 2 | transversal |
| diagnostico.test.jsx | 3 | 3 |
| services/segundos.test.js | 3 | 4 |
| lib/contrato.test.js | 3 | 7 |
| **Total** | **344** | |

(Los dos archivos de pantallas y servicios están repartidos entre áreas, por eso su suma aparece en la tabla de arriba. Un archivo con números entre paréntesis indica cuántos casos van a cada área.)

**Lo que no cuadró al principio:** el área 4 de `09_resumen_cap7.md` daba 127 de interfaz, y el área 1 daba 46. Mis cifras son otras porque 09 suma pruebas que cuentan en dos áreas (por ejemplo, las de `pantallas.test.jsx` están en 1, 2, 3 y 5 a la vez), y aquí cada caso cuenta una sola vez. Esto es lo que hay que mirar si el capítulo 7 compara con 09.

---

# Parte 2 · Accesibilidad (axe-core)

## Pantallas que revisa axe

| # | Pantalla | Archivo | Pruebas |
|---|---|---|---|
| 1 | Inicio de sesión | accesibilidad.test.jsx | 1 |
| 2 | Experimentos | accesibilidad.test.jsx | 1 |
| 3 | Nuevo experimento | accesibilidad.test.jsx | 1 |
| 4 | Experimento | accesibilidad.test.jsx | 1 |
| 5 | Grupo | accesibilidad.test.jsx | 1 |
| 6 | Cargar video | accesibilidad.test.jsx | 1 |
| 7 | Análisis en curso | accesibilidad.test.jsx | 1 |
| 8 | Pausa | accesibilidad.test.jsx | 1 |
| 9 | Resultados (tanda, vista por defecto) | accesibilidad.test.jsx | 1 |
| 10 | Revisión segundo a segundo | accesibilidad.test.jsx | 1 |
| 11 | Mi perfil | accesibilidad.test.jsx | 1 |
| 12 | Administración | accesibilidad.test.jsx | 1 |
| 13 | Resumen del experimento | pantallas.test.jsx | 1 |
| 14 | Aviso de privacidad | pages/AvisosLegales.test.jsx | 1 |
| 15 | Términos y condiciones | pages/AvisosLegales.test.jsx | 1 |

**Total: 15 pantallas, 15 pruebas.** (Las 12 del archivo `accesibilidad.test.jsx` son las pantallas 1 a 12; la lista se recorre con un bucle, así que cada una es una prueba.)

Además, hay 7 pruebas de axe sobre componentes de gráficas y de la segunda vista (no son pantallas): `GaleriaGraficas`, `G2`, `G3`, `G4`, `G5`, `G6` y `SegundoASegundo`, una cada una. Están contadas dentro del área 4.

## Lo que axe NO revisa

- Primer acceso y restablecer contraseña.
- Las vistas de resultados: segundo a segundo, minuto a minuto, cada 5 segundos y comparación entre grupos (solo se revisa la vista por defecto, en la pantalla 9).
- La vista de gráficas dentro del resumen: solo el componente de la galería, no la página completa.
- Los diálogos: eliminar experimento, eliminar tanda, crear y editar cuenta.
- El panel de notificaciones.
- La pantalla de error.

## Reglas que se desactivan, y por qué

- **Contraste de color (`color-contrast`): desactivado en las 22 pruebas axe del frontend** (15 de pantallas: `accesibilidad.test.jsx`, `pantallas.test.jsx` y `AvisosLegales.test.jsx`; y 7 de componentes: `GaleriaGraficas`, G2 a G6 y `SegundoASegundo`). La razón escrita está solo en `accesibilidad.test.jsx`, líneas 3-5: «El contraste de color no se revisa aquí: jsdom no calcula estilos». jsdom no aplica las hojas de estilo ni el diseño, así que axe no puede calcular el color de fondo ni el texto, y daría resultados falsos.
- Ninguna otra regla se desactiva.

## ¿Existe una revisión de contraste?

**No, en ningún lado.** No hay una prueba de contraste en otro archivo, ni un script, ni una herramienta en el repositorio, ni una lista de comprobación manual que lo mencione (`frontend/CHECKLIST.md` dice «axe-core sobre las 12 pantallas», sin contraste). El contraste de los tokens de color en `app.css` no está comprobado por ninguna prueba. Si el capítulo 7 necesita decirlo, la frase honesta es que el contraste no está verificado.
