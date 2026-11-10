# 10 · Casos comprobados del backend (219 pruebas anteriores)

Este documento cubre las **219 pruebas de backend** que ya existían. Quedan fuera las 49 de `CrearEliminarSubirTest` (16), `PlazoDeRecuperacionTest` (4) y `RestriccionesDeLaBaseTest` (29), que ya están en sus tablas.

**Cómo se contó:** `grep -c "@Test"` por archivo en `backend-spring/src/test/java` da **268**; menos 16 + 4 + 29 = 49 → **219**. Cada prueba aparece en **una sola fila**, en su área principal, para que la suma por área y el total coincidan.

**Dónde corre:** «sin base» = corre con `./mvnw -o test`. «con PostgreSQL» = necesita `FST_TEST_DB_URL`. Una prueba marcada «sin base» que usa un doble (correo, base de datos o servicio simulados) lo dice en la fila. Ninguna prueba de estas usa el reloj simulado; los enlaces vencidos se firman con una fecha pasada (se anota en su fila).

Totales: **95 sin base**, **123 con PostgreSQL** y **1** que solo corre con `FST_PDF_EJEMPLO`. 95 + 123 + 1 = 219.

---

## Área 1 · Cuentas y sesión

| Caso comprobado | Pruebas | Dónde corre |
|---|---|---|
| La regla del último Administrador: quitar al único Administrador activo lo deja sin administrador; con otro activo sí se puede; una cuenta que sigue siendo administradora activa no cuenta; una que no lo era no cuenta | 4 | sin base |
| La temporal tiene 12 caracteres, sin los que se confunden, y no se repite | 1 | sin base |
| Un investigador recibe 403 en todo `/admin` (listar, sistema, crear, desactivar y temporal) | 1 | con PostgreSQL |
| Sin sesión, `/admin` responde 401; si se le quita el rol a un Administrador, su siguiente petición responde 403 (el rol se lee de la base en cada petición) | 1 | con PostgreSQL |
| Crear una cuenta da una temporal de 12 caracteres, la deja con cambio obligatorio, la manda por correo (**correo simulado**) y no aparece en la lista | 1 | con PostgreSQL |
| Con temporal, cambiar la contraseña sin aceptar el aviso y los términos da 400 y no cambia nada; con la aceptación sí cambia (**sin aceptación, 400; aceptando, 200**) | 1 | con PostgreSQL |
| Identificador o correo repetidos dan 409 con su mensaje y no crean la cuenta | 1 | con PostgreSQL |
| Una temporal nueva reemplaza a la anterior y deja sin efecto los enlaces de recuperación pendientes | 1 | con PostgreSQL |
| Dos temporales seguidas: solo sirve la última | 1 | con PostgreSQL |
| Restablecer la temporal de una cuenta desactivada da 409 y no cambia la contraseña ni manda correo (**correo simulado**) | 1 | con PostgreSQL |
| El Administrador no puede darse una temporal a sí mismo (409 con mensaje); una cuenta inexistente da 404 | 1 | con PostgreSQL |
| Con una temporal, el token que la cuenta tenía queda bloqueado hasta que cambia la contraseña | 1 | con PostgreSQL |
| Con temporal, solo sirven el estado público de salud y el cierre de sesión; la lista de experimentos y las notificaciones dan 401 | 1 | con PostgreSQL |
| La lista de cuentas se filtra por rol y por estado; un rol inválido da 400 | 1 | con PostgreSQL |
| Editar una cuenta cambia nombre, correo, rol y desactivación; no hay borrado de cuentas | 1 | con PostgreSQL |
| Editar con el correo de otra cuenta da 409; con otro identificador da 400; con el mismo identificador de la URL sí guarda | 1 | con PostgreSQL |
| Nombre, apellidos o correo vacíos o solo espacios dan 400 y no cambian nada | 1 | con PostgreSQL |
| Último Administrador activo: no se puede desactivar ni quitar el rol (409); con otro activo sí | 1 | con PostgreSQL |
| Cambiar nombre o apellidos borra los reportes guardados; cambiar solo el correo o el rol no | 1 | con PostgreSQL |
| El panel de monitoreo trae disco, conductas y la cola en orden de llegada | 1 | con PostgreSQL |
| Recuperar contraseña responde igual exista o no el correo; solo manda correo a una cuenta activa que existe; el correo dice «60 minutos» (**correo simulado**) | 1 | con PostgreSQL |
| El enlace de recuperación cambia la contraseña una sola vez; después sirve la nueva y no la anterior | 1 | con PostgreSQL |
| El enlace deja de servir si la contraseña cambia por otro camino, si venció, si es un token de sesión o basura, y no abre la API (**el enlace vencido se firma con una fecha pasada, no con el reloj**) | 1 | con PostgreSQL |
| Un enlace con contraseña que no cumple las reglas da 422 y no gasta el enlace | 1 | con PostgreSQL |
| Una cuenta desactivada después de pedir el enlace ya no puede usarlo | 1 | con PostgreSQL |
| El alta ignora el rol que venga y la cuenta nace como Investigador | 1 | con PostgreSQL |
| Identificador de 1 a 10 dígitos: se recortan los espacios y se guarda limpio; con letras da 400 con «solo puede llevar dígitos»; con más de 10 dígitos da 400 | 3 | con PostgreSQL |
| Administrador: no se desactiva ni se quita su rol a sí mismo (409, «usa Mi perfil»); sí cambia su nombre y su correo; sí desactiva a otro | 3 | con PostgreSQL |

**Suma del área: 35** (5 sin base, 30 con PostgreSQL).

---

## Área 2 · Experimentos y carga de video

| Caso comprobado | Pruebas | Dónde corre |
|---|---|---|
| Lista de experimentos sin filtros: 20 por página, total correcto, página vacía fuera de rango | 1 | con PostgreSQL |
| Filtro por estado (completado, en cola, error, en espera del Día 2, sin videos) | 1 | con PostgreSQL |
| Filtro por fecha, por año o por día | 1 | con PostgreSQL |
| Filtro por tratamiento sin distinguir mayúsculas ni acentos, con el tratamiento completo | 1 | con PostgreSQL |
| Los filtros combinados se cumplen todos a la vez | 1 | con PostgreSQL |
| Con filtros, el total son los que pasan y las páginas cortan de 20 en 20 | 1 | con PostgreSQL |
| Con filtros, cada experimento trae los mismos campos que sin filtro | 1 | con PostgreSQL |
| Un valor no válido en cualquier filtro da 400; sin sesión da 401 | 1 | con PostgreSQL |
| Búsqueda por nombre sin distinguir mayúsculas y con un pedazo del nombre; los espacios de sobra se recortan | 1 | con PostgreSQL |
| Búsqueda por tratamiento sin acentos y por un pedazo del nombre del tratamiento | 1 | con PostgreSQL |
| La búsqueda se combina con los demás filtros y con las páginas | 1 | con PostgreSQL |
| El resumen cuenta todos los experimentos, con o sin filtros, por estado, años y tratamientos | 1 | con PostgreSQL |
| El resumen trae los avisos de vencimiento a 7 días, con la fecha de borrado | 1 | con PostgreSQL |
| Sin experimentos, el resumen viene vacío | 1 | con PostgreSQL |
| Al subir un video, una tanda fuera de A a Z da 422 con su mensaje | 1 | con PostgreSQL |
| Al subir un video, una etiqueta de más de 100 caracteres da 422 | 1 | con PostgreSQL |
| Al subir un video, una etiqueta de 100 caracteres pasa la revisión de largo | 1 | con PostgreSQL |

**Suma del área: 17** (todas con PostgreSQL).

---

## Área 3 · Worker y pipeline (lo que se comprueba desde el backend)

| Caso comprobado | Pruebas | Dónde corre |
|---|---|---|
| Con pausa, el estado trae el cilindro, el segundo desde el que se pausó y el plazo que queda (menos de 60 s) | 1 | con PostgreSQL |
| Sin archivo de pausa, la pausa va en null y el estado responde 200 | 1 | con PostgreSQL |
| Archivo de pausa dañado o sin hora: pausa en null y no se cae el estado | 2 | con PostgreSQL |
| Un análisis que no está en pausa trae pausa en null, aunque el archivo exista | 1 | con PostgreSQL |
| Un movimiento de 3 px o menos cuenta como casi igual y se rechaza; 4 px ya es ajuste | 2 | sin base |
| Basta que un solo borde del recuadro se mueva más de 3 px para aceptar el ajuste | 1 | sin base |
| Sin el cilindro no hay recuadro con qué comparar | 1 | sin base |
| Modelo en modo desarrollador: sin ese modo responde 404 | 1 | sin base |
| Si el worker no informó o el archivo no se puede leer, responde archivo en null con el motivo | 2 | sin base |
| Con el modelo informado devuelve archivo, huella, fecha y texto (**archivo de prueba**) | 1 | sin base |
| Si el modelo no existía, responde archivo en null, la ruta esperada y sin texto | 1 | sin base |
| El texto del modelo dice archivo, red, huella, tamaño y fecha | 1 | sin base |
| La huella son los primeros 16 caracteres y la fecha solo el día | 1 | sin base |
| Un análisis anterior al registro del modelo dice «sin registro» | 1 | sin base |
| Lo que no se sabe se omite; sin datos dice «modelo desconocido» | 1 | sin base |
| Una huella o una fecha cortas se muestran completas | 1 | sin base |

**Suma del área: 19** (5 con PostgreSQL, 14 sin base).

---

## Área 4 · Resultados y reportes

| Caso comprobado | Pruebas | Dónde corre |
|---|---|---|
| Un bloque de 5 s toma la conducta con más segundos | 1 | sin base |
| En empate entre nado o escalamiento y otra conducta, el bloque queda en conducta activa | 2 | sin base |
| En empate de inmovilidad con una conducta activa, el bloque queda en conducta activa | 1 | sin base |
| La conducta activa del modelo no se suma ni a nado ni a escalamiento | 1 | sin base |
| Cinco segundos sin ver dan conducta activa con matiz; con algún sin ver el bloque lleva matiz; sin sin ver no hay matiz aunque haya empate | 3 | sin base |
| Una conducta fuera de las cinco es un error de programa | 1 | sin base |
| Los segundos se parten en bloques que empiezan en 1, 6 y 11; el último incompleto trae solo los que existen; un espécimen sin segundos no tiene bloques | 3 | sin base |
| Sin especímenes, media, varianza y desviación son null | 1 | sin base |
| Con un espécimen, la media es su valor y la varianza y la desviación son cero | 1 | sin base |
| Con dos especímenes, la varianza se divide entre n − 1 (muestral) | 1 | sin base |
| Valores conocidos dan la media, la varianza y la desviación esperadas | 1 | sin base |
| Valores iguales no tienen dispersión | 1 | sin base |
| Redondeo a un decimal; los null se respetan | 1 | sin base |
| Las cinco categorías y los cinco minutos siempre aparecen; los grupos salen en orden: control, referencia y experimentales | 2 | sin base |
| Varianza y desviación muestrales con dos especímenes, y coeficiente de variación | 1 | sin base |
| Con un solo espécimen, la media existe y la dispersión no aplica (null) | 1 | sin base |
| Con media cero el coeficiente de variación no existe, pero la desviación sí | 1 | sin base |
| Grupo sin datos: n = 0 y todo en null | 1 | sin base |
| Conducta activa y sin ver con sus valores por espécimen | 1 | sin base |
| La duración media es la suma real de las cinco categorías | 1 | sin base |
| Un minuto incompleto se marca; solo cuentan los especímenes con segundos en ese minuto | 1 | sin base |
| Niveles mezclados: el grupo no se promedia y se da por tanda | 1 | sin base |
| La sección de gráficas del PDF sale con sus marcadores, y cada marcador lleva a una página válida | 2 | sin base |
| Sin grupos con datos no hay sección ni marcador en el PDF | 1 | sin base |
| Un grupo con datos basta para las dos primeras gráficas del PDF | 1 | sin base |
| Nivel mixto: la gráfica no se dibuja y su nota lo dice | 1 | sin base |
| El texto del PDF trae los títulos, las notas y los nombres de grupo | 1 | sin base |
| Una etiqueta larga se parte o se recorta sin salirse de su columna | 1 | sin base |
| El PDF con gráficas es legible y tiene al menos una página | 1 | sin base |
| Escribe el PDF de ejemplo en la ruta indicada | 1 | se omite; corre con la variable `FST_PDF_EJEMPLO` |
| El PDF es del experimento y no puede ser de un grupo ni de una tanda | 2 | sin base |
| El CSV y el XLSX son del grupo o de la tanda, nunca del experimento | 2 | sin base |
| Un alcance con otro texto (ruta, barra o vacío) se rechaza | 1 | sin base |
| Sin marca de versión, borra los PDF viejos, deja los CSV y escribe la marca actual (**base simulada**) | 1 | sin base |
| Con la marca de la versión actual no toca nada (**base simulada**) | 1 | sin base |
| Si la base falla al invalidar, el arranque se detiene (**base simulada**) | 1 | sin base |
| El archivo empieza con la marca BOM de UTF-8 y sus líneas terminan en CRLF | 1 | sin base |
| El encabezado es exactamente el de la guía | 1 | sin base |
| Sin ver es la última columna, después de conducta activa; cada fila es un espécimen por minuto con segundos enteros | 2 | sin base |
| Después de las filas van media, desviación y varianza con un decimal; la revisión humana dice Sí o No; el aviso de niveles mixtos aparece cuando hace falta | 4 | sin base |
| Escapes según RFC 4180: comas, comillas y saltos de línea | 1 | sin base |
| Un texto que empieza con =, +, −, @, tabulador o retorno lleva apóstrofo antes (las comillas van por fuera) | 2 | sin base |
| Un texto normal, o con = en medio, no cambia | 1 | sin base |
| Los números nunca llevan apóstrofo; ninguna celda de texto empieza como fórmula | 2 | sin base |
| Sin modelos no hay filas de modelo; en modo desarrollador va una fila por modelo después de la revisión, y el aviso antes que los modelos | 3 | sin base |
| La hoja Resultados tiene el encabezado en negrita | 1 | sin base |
| En el XLSX, el texto va como texto y los segundos como números | 1 | sin base |
| En el XLSX, los estadísticos son números en gris, la varianza va incluida y «Sin ver» es una columna | 1 | sin base |
| En el XLSX, la revisión y el aviso van al final, como en el CSV | 1 | sin base |
| Un texto que parece fórmula se guarda como texto en el XLSX | 1 | sin base |
| Sin modelos, la hoja termina en la revisión; en modo desarrollador, los modelos van después | 2 | sin base |
| El libro de Excel del experimento tiene dos hojas: Resultados y Segundo a segundo | 1 | sin base |
| La hoja de segundos tiene una columna por conducta con 1 o 0, y el origen | 1 | sin base |
| La hoja de resultados trae la tabla del grupo con sus estadísticos | 1 | sin base |
| Sin segundos, la hoja de segundos solo trae el encabezado | 1 | sin base |
| Una tanda trae duración, grupo, letra y segundos de cada espécimen (**resultados simulados**) | 1 | sin base |
| Una tanda sin análisis completado responde 404 (**resultados simulados**) | 1 | sin base |
| Sin letra de tanda responde 400 (**resultados simulados**) | 1 | sin base |
| Si la tanda o el grupo no existen, el error de alcance sube tal cual (**resultados simulados**) | 1 | sin base |
| Bloques de cada espécimen con la forma de la guía: conducta, matiz y segundos | 1 | con PostgreSQL |
| Solo entran las tandas con análisis completado | 1 | con PostgreSQL |
| Los bloques usan los segundos ya corregidos | 1 | con PostgreSQL |
| Sin sesión 401; experimento inexistente 404; cualquier cuenta con sesión puede ver los bloques | 1 | con PostgreSQL |
| Un experimento sin análisis completados responde lista vacía | 1 | con PostgreSQL |
| Grupos en orden, con faltantes, n, media, desviación y varianza por conducta, y minutos incompletos | 1 | con PostgreSQL |
| Sin sesión 401; experimento inexistente 404 | 1 | con PostgreSQL |
| El CSV del grupo trae columnas, filas, estadísticos y cabeceras de descarga | 1 | con PostgreSQL |
| El CSV de una tanda trae solo esa tanda y su nombre lleva su letra | 1 | con PostgreSQL |
| Tandas con niveles distintos agregan el aviso en el CSV | 1 | con PostgreSQL |
| Un grupo cuyo nombre empieza con = sale con apóstrofo en el CSV y como texto en el XLSX | 1 | con PostgreSQL |
| El XLSX trae números de verdad y las mismas cabeceras; el archivo se guarda en la carpeta de reportes | 1 | con PostgreSQL |
| Sin grupo, o con una tanda que no es una letra: 400 | 1 | con PostgreSQL |
| Un grupo de otro experimento, una tanda que no existe o un experimento que no existe: 404 | 1 | con PostgreSQL |
| Sin análisis completados: 409, y no queda ni fila ni archivo | 1 | con PostgreSQL |
| Sin sesión 401; cualquier cuenta con sesión puede descargar el CSV y el XLSX | 1 | con PostgreSQL |
| La segunda descarga sale del archivo guardado | 1 | con PostgreSQL |
| Tras corregir segundos, el reporte se regenera y marca la revisión; corregir invalida los tres reportes | 1 | con PostgreSQL |
| Un segundo marcado como dudoso, sin conducta, cuenta como revisión | 1 | con PostgreSQL |
| Resultados del grupo: la varianza va junto a la media y la desviación | 1 | con PostgreSQL |
| En modo desarrollador, el CSV dice con qué modelo se hizo cada análisis; el de una tanda trae solo el de esa tanda; el XLSX también | 3 | con PostgreSQL |
| Sin modo desarrollador, los reportes no dicen el modelo aunque el archivo exista | 1 | con PostgreSQL |
| El PDF del experimento trae la tabla de modelos en modo desarrollador | 1 | con PostgreSQL |
| El Excel del experimento tiene sus dos hojas; la hoja de segundos tiene las filas esperadas | 1 | con PostgreSQL |
| Segundos de una tanda con conducta, origen y sin ver nulo | 1 | con PostgreSQL |
| Una tanda sin análisis completado responde 404; sin tanda responde 400 | 1 | con PostgreSQL |
| El PDF sin análisis completados se entrega, pero no se guarda en caché | 1 | con PostgreSQL |
| El PDF con resultados se guarda y trae la columna «Sin ver» | 1 | con PostgreSQL |
| Totales y minutos traen las cinco claves y suman 100 % | 1 | con PostgreSQL |
| Los resultados sin minutos también traen «Sin ver» en los totales | 1 | con PostgreSQL |

**Suma del área: 109** (77 sin base o que se omiten sin base de datos, 32 con PostgreSQL).

---

## Área 5 · Revisión segundo a segundo

| Caso comprobado | Pruebas | Dónde corre |
|---|---|---|
| Token de video: lo piden quien subió el video y el Administrador; otra cuenta da 403; sin sesión da 401 | 1 | con PostgreSQL |
| El token de video se rechaza si el análisis no está completado (409) | 1 | con PostgreSQL |
| El token de video se rechaza si el video ya no está (409 con mensaje) | 1 | con PostgreSQL |
| El token de video de un análisis inexistente o de otro experimento da 404 | 1 | con PostgreSQL |
| Con su token, el video responde 200 y con un rango de bytes responde 206 | 1 | con PostgreSQL |
| El token de un análisis no sirve para el video de otro | 1 | con PostgreSQL |
| La sesión (JWT) no sirve ni en la URL ni en la cabecera del video | 1 | con PostgreSQL |
| Sin token, con token vacío o inventado: 401 | 1 | con PostgreSQL |
| El token de video no sirve en otras rutas (revisión, resultados, perfil, reinicio y pedir token) | 1 | con PostgreSQL |
| Si el video se borró, responde 404 aunque el token sea válido | 1 | con PostgreSQL |
| Corregir: solo se guardan los segundos que llegan; el resto queda como estaba (totales y origen) | 1 | con PostgreSQL |
| El origen solo puede ser confirmada, corregida o dudosa; otro valor da 400 | 1 | con PostgreSQL |
| Un cuerpo con un error no guarda ni los segundos buenos | 1 | con PostgreSQL |
| El Administrador corrige; otra cuenta da 403 sin cambiar nada; sin sesión da 401 | 1 | con PostgreSQL |
| Solo se corrige un análisis completado (409) | 1 | con PostgreSQL |
| Sin video no se corrige: 409 con mensaje | 1 | con PostgreSQL |
| `puedeCorregir` es verdadero para quien subió el video y para el Administrador, y falso para otra cuenta | 1 | con PostgreSQL |
| Sin video, `puedeCorregir` es falso incluso para quien subió, y no hay enlace al video | 1 | con PostgreSQL |
| Corregir una conducta activa sube el nivel a agrupado; quitarla lo baja a preciso | 1 | con PostgreSQL |
| El nivel agrupado del worker baja si la revisión quita la última conducta activa | 1 | con PostgreSQL |
| Una corrección rechazada (conducta inválida) no cambia el nivel | 1 | con PostgreSQL |
| Solo se reinicia un análisis completado; otro estado da 409 con su nombre | 1 | con PostgreSQL |
| Con el video borrado, reiniciar da 409 y no toca nada | 1 | con PostgreSQL |
| Otra cuenta da 403; sin sesión da 401 | 1 | con PostgreSQL |
| Un análisis inexistente o de otro experimento da 404 antes que 403 | 1 | con PostgreSQL |
| El creador reinicia: el análisis queda en cola sin resultados, reportes ni archivos viejos, y el video se conserva (202) | 1 | con PostgreSQL |
| Un Administrador también puede reiniciar y no toca otros análisis del experimento | 1 | con PostgreSQL |
| Reiniciar un análisis ya reiniciado da 409 | 1 | con PostgreSQL |

**Suma del área: 28** (todas con PostgreSQL).

---

## Área 6 · Notificaciones y correo

| Caso comprobado | Pruebas | Dónde corre |
|---|---|---|
| Si falla el envío del correo de la temporal al crear la cuenta, la cuenta queda creada (**correo simulado con error**) | 1 | con PostgreSQL |
| Si falla el correo al restablecer, responde 200 con la temporal y esta sirve (**correo simulado con error**) | 1 | con PostgreSQL |
| El contador de no leídas cuenta todas las notificaciones de la cuenta | 1 | con PostgreSQL |
| El contador no pasa del tope de la lista (la lista trae 100, el contador dice 105) | 1 | con PostgreSQL |

**Suma del área: 4** (todas con PostgreSQL). Las pruebas del worker que dejan el correo pendiente con su asunto están en el área 6 del worker, fuera de este documento.

---

## Área 8 · Base de datos (concurrencia y candados)

| Caso comprobado | Pruebas | Dónde corre |
|---|---|---|
| Dos administradores que se desactivan a la vez: nunca se queda el sistema sin administrador; solo pasa una | 1 | con PostgreSQL |
| Quitarse el rol uno al otro a la vez: tampoco se queda el sistema sin administrador | 1 | con PostgreSQL |
| Crear la misma cuenta dos veces a la vez: se crea una sola (201 y 409) | 1 | con PostgreSQL |
| El mismo enlace de recuperación usado dos veces a la vez: solo funciona una | 1 | con PostgreSQL |
| Cambio de nombre y notificación del worker a la vez: el candado de cuenta (`FOR NO KEY UPDATE`) deja pasar la notificación; con `FOR UPDATE` el mismo cruce falla con `lock_timeout` (55P03) | 1 | con PostgreSQL |
| Editar el nombre mientras el worker crea notificaciones: no se traba ni pierde nada (20 vueltas, 20 notificaciones) | 1 | con PostgreSQL |
| Dos reinicios de un análisis a la vez: solo pasa uno (202 y 409) | 1 | con PostgreSQL |

**Suma del área: 7** (todas con PostgreSQL).

---

## Total

| Área | Suma |
|---|---|
| 1 · Cuentas y sesión | 35 |
| 2 · Experimentos y carga de video | 17 |
| 3 · Worker y pipeline | 19 |
| 4 · Resultados y reportes | 109 |
| 5 · Revisión segundo a segundo | 28 |
| 6 · Notificaciones y correo | 4 |
| 8 · Base de datos (concurrencia) | 7 |
| **Total** | **219** |

**Total del backend anterior: 219** (35 + 17 + 19 + 109 + 28 + 4 + 7 = 219). Coincide con 268 − 16 − 4 − 29.

---

## Qué archivos corresponden a cada área

Todos bajo `backend-spring/src/test/java/com/ipn/mx/fst/service/`, salvo el de gráficas PDF.

| Área | Archivo | Pruebas en el área |
|---|---|---|
| 1 | `AdministracionYRecuperacionTest.java` | 23 (de 31; las 8 restantes están en las áreas 6 y 8) |
| 1 | `ArreglosE50Test.java` | 7 (de 14; las demás, en las áreas 2, 4 y 6) |
| 1 | `ReglaAdministradoresTest.java` | 5 |
| 2 | `ListaDeExperimentosTest.java` | 14 |
| 2 | `ArreglosE50Test.java` | 3 |
| 3 | `EstadoConPausaTest.java` | 5 |
| 3 | `PausaServiceUmbralTest.java` | 4 |
| 3 | `ModeloActualDeDesarrolloTest.java` | 5 |
| 3 | `ModeloDelAnalisisTest.java` | 5 |
| 4 | `BloquesDeCincoTest.java` | 12 |
| 4 | `BloquesDeResultadosTest.java` | 5 |
| 4 | `EstadisticosTest.java` | 6 |
| 4 | `GraficasDeResultadosTest.java` | 10 |
| 4 | `GraficasEndpointTest.java` | 2 |
| 4 | `ReporteAlmacenTest.java` | 5 |
| 4 | `ReporteAlmacenVersionTest.java` | 3 (sin commit) |
| 4 | `ReporteCsvTest.java` | 17 |
| 4 | `ReporteXlsxTest.java` | 7 |
| 4 | `ReportesCsvXlsxTest.java` | 18 |
| 4 | `ExcelDelExperimentoTest.java` | 4 |
| 4 | `SegundosDeResultadosTest.java` | 4 |
| 4 | `SegundosYExcelTest.java` | 3 |
| 4 | `ArreglosE50Test.java` | 2 (las del PDF) |
| 4 | `SinVerYNivelTest.java` | 2 (las de totales) |
| 4 | `graficas/GraficasPdfTest.java` | 9 (sin commit) |
| 5 | `RevisionYVideoTest.java` | 18 |
| 5 | `SinVerYNivelTest.java` | 3 (las de nivel) |
| 5 | `ReinicioDeAnalisisTest.java` | 7 (la de concurrencia está en el área 8) |
| 6 | `AdministracionYRecuperacionTest.java` | 2 (las de correo) |
| 6 | `ArreglosE50Test.java` | 2 (los contadores de notificaciones) |
| 8 | `AdministracionYRecuperacionTest.java` | 6 (concurrencia y candados) |
| 8 | `ReinicioDeAnalisisTest.java` | 1 (dos reinicios a la vez) |

**Verificación por archivo** (de `grep -c "@Test"`): `AdministracionYRecuperacionTest` 31 = 23 + 2 + 6; `ArreglosE50Test` 14 = 7 + 3 + 2 + 2; `ReinicioDeAnalisisTest` 8 = 7 + 1; `SinVerYNivelTest` 5 = 3 + 2; `GraficasPdfTest` 9 (una se omite sin `FST_PDF_EJEMPLO`); el resto de archivos entran completos en un área.

---

## Dónde no cuadra con el resumen de capítulo 7 (`09_resumen_cap7.md`)

La suma por área no coincide con la tabla 2 del `09` en cinco áreas. El total sí coincide. Las diferencias vienen de cómo se reparten las pruebas, porque el `09` cuenta algunas en más de un área:

- **Área 1:** el `09` dice 43 (38 con PostgreSQL, 5 sin base). Aquí son 35: las 8 de concurrencia y de correo se pasaron a las áreas 6 y 8, para que cada prueba cuente una sola vez.
- **Área 3:** el `09` dice 28 (14 con PostgreSQL). Aquí son 19 (5 con PostgreSQL). No encuentro en los archivos del backend 14 pruebas con PostgreSQL de esta área; si el `09` las cuenta, vienen de otro archivo, y habría que revisar cuál.
- **Área 5:** el `09` dice 29. Aquí son 28, porque la prueba de dos reinicios a la vez pasó al área 8.
- **Área 6:** el `09` dice 16 con PostgreSQL. Aquí son 4 con PostgreSQL. Las 12 restantes son del worker (`test_correos.py`, fuera de este documento) y no son pruebas de backend.
- **Área 8:** el `09` dice 122 (las del backend con PostgreSQL, en 11 archivos). Aquí son 7, porque el resto de esas pruebas ya están en su área de contenido. Para la cifra total de pruebas con PostgreSQL: 123 de las 219 anteriores, más 49 nuevas, son 172; la prueba de PDF de ejemplo, que se omite sin `FST_PDF_EJEMPLO`, hace 173 omitidas sin base.

Lo que sí coincide: el área 2 (17), el área 4 (109), y el total de 95 que corren sin base de datos entre las 219 anteriores.
