# Especificación de las gráficas de resultados (M1A en pantalla, M1B en el PDF)

Fuente de los datos: `GET /experiments/{id}/results/charts`. Un solo cálculo en el backend (`GraficasDeResultados`). La pantalla y el PDF dibujan con este mismo JSON; ningún lado recalcula medias, desviaciones ni varianzas.

Versión: borrador de M1A, paso 1 (datos). Lo que aquí dice es la regla; si el código difiere, el código se corrige.

## 1. Contrato JSON

Envoltorio habitual `{ "data": ..., "error": null, "status": 200 }`. Todos los números son decimales con punto; la pantalla y el PDF los muestran con coma.

```
{
  "dia": 2,
  "conductas": ["nado activo", "inmovilidad", "escalamiento", "conducta activa", "sin ver"],
  "minutos": [1, 2, 3, 4, 5],
  "grupos": [ Grupo, ... ]          // control, luego referencia, luego experimentales; por id dentro de cada tipo
}

Grupo = {
  "idGrupo": 1,
  "etiqueta": "Control",
  "tipo": "control" | "referencia" | "tratamiento experimental",   // valor crudo de la base
  "tratamiento": "agua",
  "n": 3,                          // especímenes con Día 2 analizado en el grupo (todas sus tandas)
  "nTotal": 4,                     // cilindros de todas sus tandas
  "faltantes": [ { "letra": "B", "nCilindros": 1, "estado": "procesando" | "en cola" | "en pausa" | "error" | "cancelado" | null } ],
                                   // tandas sin Día 2 completado; estado null = sin video
  "nivelMixto": false,             // true si sus especímenes tienen niveles de clasificación distintos
  "duracionMediaS": 296.4 | null,  // media de la duración real analizada por espécimen (suma de las cinco categorías)
  "especimenes": [ { "letra": "A", "numeroCilindro": 1, "nivelClasif": "preciso", "duracionS": 300 } ],
  "porConducta": { "<conducta>": Estadisticos + { "valoresS": [3, 0, ...] } } | null,
                                   // valoresS en el mismo orden que "especimenes"
  "porMinuto": [ MinutoGrupo ] | null,   // minutos 1 a 5, siempre cinco filas
  "porNivel": [ { "letra": "A", "nivelClasif": "preciso", "n": 2, "porConducta": { "<conducta>": Estadisticos } } ] | null
                                   // solo si nivelMixto: una fila por tanda
}

Estadisticos = {
  "n": 2,
  "media": 15.0 | null,            // null si n = 0
  "de": 7.1 | null,                // desviación muestral (n − 1); null si n < 2
  "varianza": 50.0 | null,         // varianza muestral (n − 1); null si n < 2
  "cv": 0.471 | null               // DE ÷ media, fracción (0,471 = 47,1 %); null si n < 2 o si la media es 0
}

MinutoGrupo = {
  "minuto": 1,
  "n": 2,                          // especímenes con segundos en ese minuto (un video corto no aporta al minuto 4 ni 5)
  "segundosMin": 60 | null,        // segundos reales del minuto, el menor entre esos especímenes
  "segundosMax": 60 | null,        // el mayor
  "incompleto": false,             // true si algún espécimen tiene menos de 60 s en ese minuto
  "porConducta": { "<conducta>": Estadisticos }    // n, media, DE, varianza y cv de los especímenes con segundos en el minuto
}
```

Reglas del contrato:
- Las cinco categorías siempre van: `nado activo`, `inmovilidad`, `escalamiento`, `conducta activa` y `sin ver`. «Sin ver» no es una conducta; sus valores cuentan en el total de segundos.
- Un valor que no existe va `null`. Nunca se manda `0` para algo que no se midió.
- Un grupo sin datos aparece igual, con `n: 0` y los estadísticos en `null`.
- Un grupo con `nivelMixto: true` lleva `null` en `duracionMediaS`, `porConducta` y `porMinuto`. Sus números van por tanda en `porNivel`.
- Los grupos con tandas pendientes se marcan con `faltantes`; sus especímenes no entran al promedio.
- Redondeo (lo hace el backend): media, DE, varianza y duración a un decimal; CV a tres decimales. Los cálculos de fondo no se redondean.

## 2. Reglas comunes a las seis

**Alcance.** Todos los grupos del experimento, con el Día 2 analizado. La gráfica no depende de la tanda que se vea en la pantalla. Lo dice el título: «Día 2 · todos los grupos del experimento».

**Orden de grupos.** Control, referencia, experimentales. Dentro de cada tipo, por `idGrupo`. Igual en las seis gráficas, en la tabla y en la leyenda.

**Textos obligatorios.** Cada gráfica incluye:
- Nota de la barra de error: «La barra ± DE muestra la dispersión entre especímenes; no indica si una diferencia es significativa.»
- Nota de origen: «Los datos son sobre la salida del modelo; su exactitud todavía no está medida.»
- El n de cada grupo, como «n = 3» (y «n = 3 de 4» si hay tandas pendientes).

**Grupos sin datos o con pendientes.** Sin barra ni punto. Se escribe «sin datos» (n = 0) o «n = 2 de 4 · 2 especímenes pendientes (Tanda B procesando)», con el mismo texto que la comparación actual. La etiqueta del grupo se conserva en el eje, para que no desaparezca.

**Grupo con niveles mezclados.** No se promedia junto. En G1 el dibujo no pone barra y la tabla trae una fila por tanda, con su nivel y sus números (`porNivel`); la nota dice: «Mezcla niveles de clasificación; no se promedia junto.» En G2 a G6 el grupo no se dibuja y la nota es la misma.

**Formas y patrones además del color** (el PDF se imprime en blanco y negro).

| Grupo | Relleno o color | Patrón | Línea (G2) | Marcador (G2, G6 texto) |
|---|---|---|---|---|
| Control | `--color-text` | sólido | sólida | círculo |
| Referencia | `--color-accent` | rayado diagonal 45° | discontinua | rombo |
| Experimental (1.º, 2.º, …) | `--color-neutral-400` | punteado; luego vertical; luego horizontal | punteada; luego trazo-punto; luego larga | triángulo; luego cuadrado; luego cruz |

| Conducta | Relleno | Patrón |
|---|---|---|
| Nado activo | `--color-text` | sólido |
| Inmovilidad | `--color-accent` | sólido |
| Escalamiento | `--color-neutral-400` | sólido |
| Conducta activa | `--color-accent-300` | rayado diagonal (el mismo del matiz de los bloques) |
| Sin ver | `--color-divider` | sólido con borde de `--color-text` |

Los tokens están en `frontend/src/styles/app.css`. El tema es claro; no hay modo oscuro.

**Tabla accesible.** Cada gráfica trae una tabla con los mismos números que dibuja (uno por celda). Los nombres de columna van con el texto de la gráfica, no solo con color.

**Escalas.** Todas empiezan en 0. La escala de segundos de los totales (G1) es de 0 a 300 s, la ventana del Día 2; solo crece, en múltiplos de 30 s, si un grupo la rebasa con su media + DE o con un espécimen (hoy el análisis no pasa de 300 s, así que no crece). Los ejes de G1 muestran marcas cada quinta parte de la escala. Las de minutos (G2, G3 y G6) usan 0 a 60 s, porque el minuto es el máximo que se puede medir. El minuto 5 de un video corto se dibuja con sus segundos reales y se marca como incompleto; no se completa a 60 s.

**Barras de error.** Un solo tipo: media ± DE, con tope en 0 y en el máximo de la escala. Si la DE es `null`, no se dibuja la barra y el valor dice «± no aplica». Nunca se dibuja una barra de cero para algo que no existe.

**Nombre de la descarga.** `g{N}_{conducta}` (sin acentos, espacios por guion bajo): `g1_inmovilidad`, `g1_nado_activo`, `g2_escalamiento`. Para las gráficas sin conducta única, `g4_apilada`, `g5_variabilidad`, `g6_mapa`.

## 3. Las seis gráficas

### G1 · Barras agrupadas: media por grupo

- **Pregunta:** ¿se separa un grupo del control en el tiempo total de una conducta en el Día 2?
- **Datos:** `porConducta.<c>.media`, `.de`, `.n`, `.valoresS`.
- **Ejes:** X, un grupo por posición (orden de §2); el nombre se parte en dos renglones o se recorta con «…» según el ancho de su columna, y el nombre completo queda en `<title>` y en la tabla. Y, segundos, de 0 a `escala` (§2).
- **Selección de conducta:** una conducta (`nado activo`, `inmovilidad` o `escalamiento`) o las tres juntas. En modo de tres juntas, cada grupo tiene tres barras en el orden de §2 para la conducta, con su relleno de la tabla de conductas. «Conducta activa» no va en las barras (ver abajo). «Sin ver» no va en G1.
- **Barra:** `media`, con su barra de error ± DE. Etiqueta sobre la barra: «12,3 ± 2,1 s».
- **Puntos por espécimen:** capa opcional, encendida por defecto. Un punto por cada valor de `valoresS`, con jitter horizontal determinista (para que el mismo espécimen caiga siempre en el mismo sitio).
- **Conducta activa fuera de las barras:** si algún grupo tiene `porConducta["conducta activa"].media > 0`, una nota visible debajo de la gráfica: «Conducta activa, fuera de las barras (segundos medios por espécimen): Control 4,2 s · Dosis 1 2,0 s». Solo aparecen los grupos con media > 0. Si todos son 0, no aparece la nota.
- **Textos del título:** «Tiempo por conducta · Día 2». Subtítulo: «Media ± DE de los especímenes, en segundos analizados. El eje llega a 300 s, la ventana del Día 2».
- **Leyenda:** las cuatro conductas con su relleno y las formas de grupo.
- **Descarga:** `g1_<conducta>` o `g1_tres_conductas`.

### G2 · Curva por minuto: una línea por grupo

- **Pregunta:** ¿cuándo se separa un grupo del control dentro del Día 2 (dinámica por minuto)?
- **Datos:** `porMinuto[m].porConducta.<c>.media` y `.de`, `.n`, `.incompleto`, `.segundosMin`.
- **Ejes:** X, minutos 1 a 5. Un minuto sin ningún espécimen (`n = 0` en todos los grupos) se deja como hueco con su número. Y, segundos por minuto, 0 a 60.
- **Líneas:** una por grupo, en el orden de §2, para la conducta elegida (una a la vez; el selector tiene las cuatro conductas).
- **Banda:** entre `media − DE` y `media + DE`, recortada a [0, 60], con la forma de relleno del grupo (sombreado tenue). Sin DE, no hay banda.
- **Puntos:** un marcador por minuto. Un minuto incompleto se dibuja con marcador hueco y su etiqueta «min 5 · 30 s de 60».
- **Textos:** título «Curva por minuto · Día 2», subtítulo «Media por minuto de la conducta elegida, con banda ± DE». Nota: «Un minuto con menos de 60 s en algún espécimen es incompleto; sus segundos son los reales.»
- **Descarga:** `g2_<conducta>`.

### G3 · Barras por minuto: un grupo junto al otro

- **Pregunta:** lo mismo que G2, pero con la comparación directa en cada minuto.
- **Datos:** igual que G2.
- **Ejes:** X, minutos 1 a 5, con el hueco de G2. Y, 0 a 60 s.
- **Barras:** dentro de cada minuto, una barra por grupo, en el orden de §2, con su relleno de grupo. Barra de error ± DE; sin DE, sin barra.
- **Minuto incompleto:** la barra de ese grupo en ese minuto va con el rayado de bloque incompleto y su etiqueta «36 s de 60».
- **Selector:** una conducta a la vez.
- **Textos:** título «Barras por minuto · Día 2».
- **Descarga:** `g3_<conducta>`.

### G4 · Barra apilada por grupo: la duración analizada

- **Pregunta:** ¿cómo se reparte el tiempo de cada grupo entre las cinco categorías, incluido lo que no se vio?
- **Datos:** `porConducta.<c>.media` de las cinco categorías y `duracionMediaS`.
- **Ejes:** X, un grupo por columna (orden de §2). Y, segundos, de 0 a `escala`, donde `escala` es el múltiplo de 30 s igual o inmediatamente mayor que el máximo de `duracionMediaS` de los grupos que se dibujan. Nunca 300 fijo: el video puede durar menos.
- **Segmentos:** en el orden de `conductas` (nado, inmovilidad, escalamiento, conducta activa, sin ver), desde abajo. La altura de cada segmento es su media. La suma de los segmentos es `duracionMediaS`.
- **Etiquetas:** dentro de un segmento, su número si mide al menos el 8 % de la escala; si no, sin número (está en la tabla).
- **Textos:** título «Reparto de la duración analizada · Día 2». Subtítulo: «Media por espécimen de cada categoría; la suma es la duración analizada».
- **Descarga:** `g4_apilada`.

### G5 · Variabilidad entre especímenes: coeficiente de variación y varianza

- **Pregunta:** ¿qué tan dispersos son los especímenes de un mismo grupo en cada conducta?
- **Datos:** `porConducta.<c>.cv`, `.varianza`, `.n`. Solo las cuatro conductas (no «sin ver»).
- **Ejes:** X, una agrupación por conducta; dentro, una barra por grupo (orden de §2). Y, CV (fracción), de 0 a `escala`, donde `escala` es el múltiplo de 0,1 igual o inmediatamente mayor que el máximo de `cv` que se dibuja; si todo es `null`, sin eje y solo el mensaje.
- **Barras:** la altura es el `cv`. Etiqueta sobre la barra: «CV 47 %». Si `cv` es `null`: sin barra y la etiqueta «no aplica».
- **Varianza:** no se dibuja como barra. Siempre va en la tabla. Debajo de cada barra solo si la columna mide al menos 46 unidades; si no, se omite y una nota dice «La varianza de cada grupo está en la tabla.». Si es `null`: «no aplica».
- **Etiquetas según el ancho de columna:** al menos 40, «CV 47 %»; de 24 a menos de 40, «47 %»; menos de 24, sin etiqueta (el dato queda en `<title>` y en la tabla). «no aplica», «sin datos» y «nivel mixto» pasan a «n/a», «—» y «mixto» por debajo de 40, y no se escriben por debajo de 24.
- **Textos:** título «Variabilidad entre especímenes · Día 2». Nota obligatoria: «Mide la variación entre especímenes de cada grupo, no entre analistas.»
- **Descarga:** `g5_variabilidad`.

### G6 · Mapa de calor: grupos por minuto

- **Pregunta:** ¿en qué grupo y en qué minuto cambia la conducta elegida?
- **Datos:** `porMinuto[m].porConducta.<c>.media`, `.n`, `.incompleto`.
- **Ejes:** filas, grupos (orden de §2); columnas, minutos 1 a 5 (con el hueco de G2).
- **Color de celda:** la media de la conducta elegida, en seis rangos fijos de 10 s: 0–10, 10–20, 20–30, 30–40, 40–50, 50–60. Los rangos usan `--color-accent-100`, `--color-accent-300` y `--color-accent-700`, con mezclas de `--color-text` para los intermedios. Cada rango lleva además un patrón distinto (punteado, rayado, cuadriculado…), para el blanco y negro.
- **Celda con número:** el valor en segundos con un decimal, siempre visible (no solo al pasar el cursor).
- **Celda sin datos:** `n = 0` → rayado gris sin número, con la leyenda «sin datos».
- **Minuto incompleto:** celda con borde discontinuo y el valor en cursiva con la etiqueta «inc.».
- **Grupo mixto:** la fila entera va con la nota «mezcla niveles» y sin celdas.
- **Textos:** título «Mapa por minuto · Día 2». Leyenda de los seis rangos con su patrón.
- **Descarga:** `g6_mapa_<conducta>`.

## 4. La galería (marco común de M1A)

Lo que comparten las seis gráficas y lo escribe el `programador` en el paso 2.

- **Una gráfica grande por defecto.** Una fila de selectores (uno por gráfica, con su nombre: «G1 · Barras por grupo», «G2 · Curva por minuto», …) y flechas ← → (botones y teclado) que pasan a la siguiente o a la anterior. Las flechas dan la vuelta: después de la última va la primera, y al revés. Los botones solo se deshabilitan si hay una sola gráfica.
- **Teclado.** ← y → cambian la gráfica solo si el foco no está en un `input`, `select` o `textarea`. No se capturan esas teclas en esos campos.
- **Ver varias.** Un modo «Ver varias» marca las gráficas que se quieren juntas. Máximo 3 en escritorio y 1 en teléfono. Teléfono = ancho de ventana menor a 720 px (propuesta; el orquestador la confirma).
- **Memoria.** La elección (gráfica activa, modo y gráficas marcadas) se guarda con `localStorage` y `try/catch`. Si falla, todo funciona igual con los valores por defecto.
- **Conducta elegida.** La conducta de G1 a G3 y G6 se elige en la misma barra de la galería y se recuerda con la misma memoria.
- **Cada gráfica** trae título, subtítulo, leyenda, n por grupo, las dos notas obligatorias de §2, la tabla accesible y el botón «Descargar imagen» (`<BotonDescargarImagen svgRef={ref} nombre="g1_inmovilidad" />`).
- **Ancho de 390 px.** Sin desplazamiento horizontal de página. Las tablas que no caben se desplazan dentro de su contenedor.

## 4 bis. Cómo agregar una gráfica

Para quien copie G1 como patrón (los minis de G2 a G6, y M1B en el PDF).

**Piezas compartidas** (en `graficas/`):
- `PanelGrafica`: el marco. Recibe `titulo`, `subtitulo`, `nombre` (nombre de la descarga), `svgRef` (el `ref` del `<svg>`), `controles`, `leyenda`, `notas` y `tabla`. Pone las dos notas obligatorias (`NOTA_DE` y `NOTA_ORIGEN`) y el botón «Descargar imagen». El `<svg>` va como hijo.
- `Patrones`: el `<defs>` con los patrones `rayado`, `punteado`, `vertical` y `horizontal`. Recibe `prefijo`, único por gráfica (`patronId(prefijo, nombre)`); con «Ver varias» hay varias gráficas en la página. Los patrones van dentro del mismo `<svg>`, para que el PNG los conserve.
- `MuestraLeyenda`: una muestra de 12 × 12 px con color y patrón. Toda leyenda de grupo o de conducta usa esta muestra, no solo `.sw`: la gráfica no depende del color.
- `estilos.js`: `estilosDeGrupos(grupos)` (forma, patrón y trazo de cada grupo), `CONDUCTAS_GRAFICA`, `textoMediaDe`, `textoN`, `numero` (coma decimal), `NOTA_MIXTO`.

**Pasos:**
1. Crear `G2NombreGrafica.jsx` (la carpeta `graficas/` tiene una por archivo). Recibe `datos` (el contrato de `GET /results/charts`), las opciones que controle (la conducta, `puntos`…) y `prefijo`. Dentro: `const svgRef = useRef(null)`, y devuelve `<PanelGrafica … svgRef={svgRef}>` con el `<svg ref={svgRef} role="img" aria-labelledby=…>` como hijo.
2. El `<svg>` lleva `<title>` y `<desc>` con ids que empiezan por `prefijo`, y `<Patrones prefijo={prefijo} />` como primer hijo.
3. Un grupo sin datos (`n = 0`) o con niveles mezclados (`nivelMixto`) no se dibuja promediado: se señala con su nota, como G1. Un `null` no se dibuja como cero.
4. Los nombres de grupo van con `renglonesDeEtiqueta` (en `G1BarrasGrupos.jsx`), y el nombre completo en `<title>`.
5. Registrarla en `GRAFICAS` (`GaleriaGraficas.jsx`): poner `disponible: true` y añadir su bloque en el render (`{id === 'g2' && <G2…/>}`). Mientras no esté lista, `disponible: false` deja la entrada con «Disponible pronto».
6. La escala en minutos es 0 a 60 s (el minuto), con la regla de incompleto (menos de 60 s) de §3.

**Qué probar** (en `GaleriaGraficas.test.jsx` o en su propio `*.test.jsx`):
- Con 1, 2 y 3 grupos; con uno sin datos y con uno de nivel mixto (la tabla con una fila por tanda).
- Con seis grupos de nombre largo: cada renglón cabe en la columna y el nombre completo está en `<title>`.
- La leyenda con patrón: al menos una muestra con `url(#…)` cuando el grupo lo lleva.
- Los `id` de patrón no se repiten al mostrar dos gráficas en la misma página.
- Axe sin violaciones con el caso mixto y el de sin datos.
- Los números de la tabla son los del contrato (sin recalcular).

## 4 ter. Cómo agregar una gráfica al PDF (M1B)

El PDF lo dibuja el backend (`backend-spring/src/main/java/com/ipn/mx/fst/service/graficas/`), con iText 5 y vectorial. Los datos son los mismos que la pantalla (`GraficasDeResultados`): el PDF no calcula medias, desviaciones ni varianzas.

**Firma exacta** (una clase por gráfica, en su archivo):

```java
public interface GraficaPdf {
    String NOTA_DE = "La barra ± DE muestra la dispersión entre especímenes; no indica si una diferencia es significativa.";
    String NOTA_ORIGEN = "Los datos son sobre la salida del modelo; su exactitud todavía no está medida.";

    record Muestra(String texto, EstiloPdf estilo) { }

    String id();                                           // "g3": id del marcador y del destino
    String titulo();                                       // título de la sección y texto del marcador hijo
    String subtitulo();
    boolean aplica(Map<String, Object> datos);             // false = no se dibuja ni tiene marcador
    float alto();                                          // alto del dibujo en puntos (sin leyenda ni notas)
    List<Muestra> leyenda(Map<String, Object> datos);      // una muestra por grupo o conducta que se dibuja
    List<String> notas(Map<String, Object> datos);         // las notas de la gráfica, incluidas NOTA_DE y NOTA_ORIGEN
    void dibujar(DibujanteGraficas d, Map<String, Object> datos);
}
```

**Piezas** (todas en `service/graficas/`):
- `DibujanteGraficas`: el dibujo. Coordenadas locales con el origen arriba a la izquierda de la caja, en puntos. Métodos: `linea`, `rectangulo`, `patron`, `marca`, `texto`, `poligono`, `anchoTexto`. `patron` dibuja sobre el rectángulo ya hecho; la barra se pinta con `rectangulo` y después con `patron`.
- `EstiloPdf`: estilos en grises. `EstiloPdf.grupo(tipo, indiceExperimental)` (control sólido; referencia rayada; experimentales rotan entre punteado, vertical y horizontal) y `EstiloPdf.conducta(clave)`. Patrones `Patron` (RAYADO, PUNTEADO, VERTICAL, HORIZONTAL); formas `Forma` (CIRCULO, CUADRADO, TRIANGULO, ROMBO, CRUZ); trazos `Trazo` (SOLIDA, DISCONTINUA, LARGA).
- `DatosGraficas`: lectura del mapa, sin cálculo. `grupos`, `tieneDatos`, `estadisticos`, `valoresS`, `numero` (coma decimal), `mediaDe`, `textoN`, `escalaSegundos`.
- `SeccionGraficasPdf.GRAFICAS`: la lista de las gráficas en su orden. `SeccionGraficasPdf.escribir` escribe la sección «Gráficas», su leyenda, el dibujo y las notas, y el marcador de cada gráfica que aplica.
- `MarcadoresPdf`: los marcadores y sus destinos con nombre. El orden del PDF es fijo: Resumen · Resultados por grupo · Gráficas (las que aplican) · Tabla por espécimen · Desglose por minuto · Modelo del análisis (solo en modo desarrollador).

**Pasos:**
1. Implementar la clase de la gráfica (G3 a G6 ya existen como esqueleto que no aplica): `aplica`, `alto`, `leyenda`, `notas` y `dibujar`.
2. En `dibujar`, leer solo del mapa `datos` (`DatosGraficas`). Un valor `null` no se dibuja como cero: se escribe «no aplica» o «sin datos», como en la pantalla.
3. Escala: 0 a 300 s en las de segundos del Día 2 (solo crece si un grupo la rebasa); 0 a 60 s en las de minuto. Un minuto con menos de 60 s en algún espécimen se dibuja con marcador hueco y su nota.
4. Leyenda: cada `Muestra` lleva el mismo `EstiloPdf` del dibujo, para que la leyenda también se distinga en blanco y negro.
5. Registrar la clase en `SeccionGraficasPdf.GRAFICAS`, en su posición (G1 a G6). Si la gráfica no aplica, no sale ni en el dibujo ni en los marcadores.
6. Si cambia lo que dibuja el PDF, subir `VERSION_PDF` en `ReporteAlmacen`: al arrancar se borran los PDF guardados de la versión anterior (ver `ReporteAlmacenVersionTest`).

**Qué probar** (en `GraficasPdfTest`, o en una prueba propia de la gráfica, sin base de datos):
- `aplica` es falso sin grupos con datos, y verdadero con ellos.
- Con 1, 2 y 3 grupos; con un grupo sin datos y con uno de nivel mixto: no lanza excepción, el sin datos y el mixto se señalan, y no se dibujan promediados.
- Con seis grupos de nombre largo: cada nombre cabe en su columna, o se parte o se recorta con «…».
- El marcador sale bajo «Gráficas», con su título, en su posición, y su destino es válido.
- Los números que se imprimen son los del mapa: se comprueban con `PdfTextExtractor` contra los valores de la prueba de `GraficasDeResultadosTest`.
- El PDF de ejemplo (`FST_PDF_EJEMPLO`) se abre y se revisa con un rasterizador (`pdftoppm`): rayados y puntos visibles, sin color.

## 5. Lo que este contrato no afirma

- No dice que una conducta sea exacta: la clasificación del modelo no es 100 % exacta y su exactitud todavía no está medida. Todo se presenta como «sobre la salida del modelo».
- Una barra ± DE no dice si una diferencia es significativa. No hay pruebas estadísticas en estas gráficas.
- G5 mide variación entre especímenes, no entre analistas.
- Un fármaco estimulante puede reducir la inmovilidad solo por aumentar la actividad, y este sistema no lo detecta.
