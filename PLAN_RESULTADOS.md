# Plan: gráficas en la pantalla de resultados y en el PDF

Para: `orquestador_web`. Él planea, reparte y revisa; ejecutan `programador` (Sonnet) y `miniprogramador1/2` (Haiku).
Actualizado: 8-nov-2026, con las decisiones del usuario. Versión del sistema: 1.6.0.

## 1. Alcance

**Se hace:** M1A (galería de seis gráficas en pantalla, con selector) y M1B (esas gráficas en el PDF, con índice lateral de marcadores). M0 ya está hecho.

**No se hace ahora:** estadística con pruebas (ANOVA, Dunnett, etc.), tratamiento estructurado, comparar entre experimentos, permisos privados, bitácora. Decisión del usuario: solo se entrega lo que el Dr. Sandino pidió desde el inicio, más las gráficas. Esos módulos quedan descritos en la sección 12 como trabajo futuro.

**No se toca el esquema de la base de datos ni ningún diagrama.** Todo lo de este plan se calcula al momento con los datos que ya existen.

## 2. Qué pidió el Dr. Sandino (fuente: `entrevista_sandino.docx` y las dos transcripciones)

| Lo que pidió | Dónde | Estado |
|---|---|---|
| Tiempo total por conducta, por animal y por sesión | P9 | Ya existe (tabla por espécimen) |
| Desglose por minuto (minutos 1 a 5) «para la dinámica farmacológica» | P9 | Existe como tabla; **M1A** lo agrega en gráfica |
| Comparar un grupo contra los demás con media, desviación estándar y varianza (mínimo tres grupos; solo Día 2) | PA, P11 | Existe en la columna derecha; **M1A** lo agrega en gráficas |
| Archivo que abra en Excel: tiempo en la cabecera y una columna por conducta, en segundos | P10 | Ya existe (CSV y XLSX) |
| Que todos los usuarios vean todos los experimentos | P18 | **Ya se cumple**: `listar` en `ExperimentoServiceImpl` no filtra por usuario. Editar o eliminar sigue limitado al creador o a un Administrador (decisión C-03). Sin cambios |
| Menos variabilidad entre analistas (hoy 15 a 20 %; meta 5 a 10 %) | P8, P13 | **No lo cubre ninguna gráfica.** G5 mide la variación entre animales, no entre analistas. Medirlo exige que varias personas analicen el mismo video; queda fuera |

Lo que **no** pidió: gráficas, pruebas estadísticas ni comparar entre experimentos. Las gráficas se presentan como un extra. El documento LaTeX **no se toca ahora**; el usuario lo editará después.

## 3. Reglas del equipo (obligatorias)

- Los agentes **no le preguntan al usuario**. Toda duda va al orquestador, que decide. Lo único que es del usuario está en la sección 10.
- **No se edita LaTeX ni diagramas** (`.puml`, `db_schema.puml`, `Diagram - FST local.svg`). Si algo obliga a cambiarlos, el orquestador le avisa al usuario y espera su confirmación.
- **No se cambia el esquema de la base ni `migraciones.sql`.** Si una tarea parece necesitarlo, se detiene y el orquestador decide.
- **Commits**: en español, formato `feat(frontend): …` o `fix(...)`, solo mensaje técnico. **Nunca** `Co-Authored-By` ni mención a Claude, Anthropic o IA, ni en commits, ni en PR, ni en comentarios o docstrings (reglas de `CLAUDE.md`). Identidad del repo: `ErickJester <id634296@gmail.com>`.
- **Versión**: la calcula `tools/version.py` desde los commits (`feat` sube el minor, `fix` el patch). Después de commitear: `python tools/version.py --escribir` y commitear los archivos de versión. No se modifica el script ni las reglas de versión.
- **Push**: solo cuando el usuario lo pida. Directo a `main`, con rebase, sin pull request.
- Cada tarea termina con pruebas verdes (sección 8) y su entrada en el `CHECKLIST.md` de su carpeta.
- Un solo agente toca un archivo a la vez. El orquestador asigna archivos, no solo tareas.

## 4. Estado actual (verificado en el código)

- **M0 está hecho** (commit `5d822c3`). `ResultadosPage.jsx` quedó en 160 líneas y las secciones viven en `frontend/src/components/resultados/`: `BarraReportes`, `Bloques`, `CabeceraResultados`, `ComparacionGrupos`, `DesglosePorMinuto`, `PanelLateral`, `SinResultados`, `TablaEspecimenes` y `constantes.js`.
- Gráficas: **ninguna librería** en `frontend/package.json` (solo react, react-dom, react-router-dom). Todo se dibuja con `div` y estilos.
- La comparación entre grupos (`getGroupComparison`) trae por conducta solo `media_s`, `de_s` y `n`. **No trae los valores por espécimen ni los datos por minuto de cada grupo.** Hay que ampliar el backend, sin tocar el esquema.
- Las «pestañas» de resultados solo hacen scroll; no cambian el contenido.
- PDF: iText **5.5.13.3** (`com.itextpdf:itextpdf`). CSV y XLSX con Apache POI. En modo desarrollador los reportes llevan el modelo del análisis (v1.5.0).
- El modelo por segundo todavía no tiene una exactitud medida. La validación en Colab (5 partes) la va a dar.

## 5. Decisiones técnicas que toma el orquestador

| Tema | Recomendación | Por qué |
|---|---|---|
| Gráficas en pantalla | SVG propio en React, sin librería | La versión portable corre sin internet; menos dependencias; control de colores y accesibilidad |
| Gráficas en el PDF | Dibujarlas en Java con iText (`PdfContentByte`, vectorial), con los **mismos datos** que la pantalla | El PDF se genera en el servidor y también se descarga por la API, sin navegador |
| Datos de las gráficas | Un solo servicio del backend que calcula todo (media, DE, varianza, coeficiente de variación, valores por espécimen, datos por minuto) y que consumen pantalla y PDF | Los números tienen que ser idénticos en todos lados |
| Riesgo de dibujar dos veces | Especificación corta de cada gráfica (ejes, escala, orden de grupos, colores, texto) y una prueba con números fijos en ambos lados | Evitar que la pantalla y el PDF se parezcan solo a medias |
| Colores | Los tokens de `styles/app.css`, y además formas o rayados para distinguir grupos | El PDF se imprime en blanco y negro y no todos distinguen colores |
| Alternativa textual | Cada gráfica con su tabla o texto accesible | La prueba de accesibilidad (axe) ya existe y no debe romperse |

## 6. Orden

```
M0 ✅ ──► M1A ──► M1B
```

Como `programador` es uno solo, hace el backend, la especificación de datos y G1 (el patrón). Los minis empiezan cuando G1 esté revisada, salvo `miniprogramador2`, que puede adelantar «Descargar imagen».

## 7. Módulos

Asignación: **P** = programador, **m1/m2** = miniprogramadores (tareas acotadas con archivos y contrato de datos exactos).

### M0: Dividir `ResultadosPage.jsx` ✅ HECHO
Commit `5d822c3`. La página se ve igual y quedó en 160 líneas.

### M1A: Gráficas en pantalla, con selector
**Idea:** no llenar la pantalla. Las gráficas viven en una galería de la que se elige qué ver.

**Las seis gráficas** (todas del Día 2, por grupo):

| Id | Gráfica | Usa |
|---|---|---|
| G1 | Barras agrupadas: una barra por grupo con media y ± DE; una conducta a la vez o las tres juntas. Con puntos por espécimen como capa que se puede apagar | media, DE, valores por espécimen |
| G2 | Curva por minuto: minutos 1 a 5, una línea por grupo con banda ± DE | media, DE por minuto |
| G3 | Barras por minuto: en cada minuto, un grupo junto al otro | media, DE por minuto |
| G4 | Barra apilada por grupo: la media de **todas** las categorías (nado, inmovilidad, escalamiento, conducta activa y «sin ver») suma la duración real analizada, no un 300 fijo | media |
| G5 | Variabilidad **entre animales de un grupo**: varianza y coeficiente de variación (DE ÷ media) por grupo y conducta. No mide la variabilidad entre analistas | varianza, DE, media |
| G6 | Mapa de calor: filas = grupos, columnas = minutos, color = media de una conducta | media por minuto |

**Reglas de datos que valen para las seis** (el orquestador las fija en la especificación antes de dibujar):
- **Categorías:** además de las tres conductas existen «conducta activa» y «sin ver». G1 define qué hace con «activa» (mostrarla aparte o dejarla fuera con nota). G4 incluye las cinco.
- **Duración real:** los videos pueden durar menos de 300 s y el minuto 5 puede ser incompleto. Las escalas de G2, G3 y G6 y el último minuto usan la duración real, no 300 fijo.
- **Cuando el cálculo no existe, se dice y no se dibuja un cero:** el coeficiente de variación no existe si la media es 0 (posible en escalamiento); la DE muestral no existe con un solo animal. En esos casos se muestra «no aplica» y se omite el valor.
- Un grupo sin datos o con tandas pendientes se señala, como ya hace la comparación actual.

**Cómo se elige (comportamiento que el orquestador debe respetar):**
- Una pestaña **«Gráficas»** dentro de la pantalla de resultados. Por defecto muestra **una sola gráfica grande**.
- Una fila de **selectores** (uno por gráfica, con su nombre) para saltar a la que se quiere ver.
- Flechas **← →** (botones y teclado) para pasar a la siguiente o a la anterior.
- Un modo **«Ver varias»**: se marcan las que se quieren y se muestran juntas. Máximo **3 en escritorio y 1 en teléfono**.
- La elección se recuerda en el navegador de cada persona. Usar `localStorage` con `try/catch`; si falla, que todo funcione igual.
- Cada gráfica trae: título, leyenda, el n de cada grupo, qué significa la barra de error («± DE»), una tabla con los mismos números y un botón **«Descargar imagen»**.

| Tarea | Quién |
|---|---|
| Pestañas reales: la vista activa va en `?vista=` y cada pestaña muestra solo su contenido | P |
| **Ancho de teléfono:** hoy la página mide 903 px a 390 px de ancho. La pestaña «Gráficas» y la página de resultados deben verse sin desplazamiento horizontal a 390 px | P |
| Backend: servicio y endpoint únicos con, por grupo del Día 2, por conducta: media, DE, varianza, coeficiente de variación, n y valores por espécimen; y por minuto (1 a 5): media y DE. Sin cambios al esquema. Pruebas con PostgreSQL. Usar la misma varianza muestral (n − 1) que ya usa la tabla | P |
| Marco de la galería: `GaleriaGraficas`, selectores, flechas, modo «Ver varias», memoria de la elección, tabla accesible, leyenda y tooltip comunes, escalas y ejes compartidos | P |
| G1 completa (es el **patrón** que copian los demás) | P |
| G2 y G3 copiando el patrón | m1 |
| G4 y G5 copiando el patrón | m2 |
| G6 | m1, cuando G3 esté revisada |
| Utilidad «Descargar imagen» (SVG a PNG) y su botón | m2 (puede adelantarla) |
| Pruebas de cada componente con datos de ejemplo; prueba axe sobre la pestaña | cada autor |

**Listo cuando:** se puede pasar de una gráfica a otra sin que la pantalla se llene, la elección se recuerda, las seis se ven bien con 1, 2 y 3 grupos, con grupos sin datos y a 390 px de ancho, y axe pasa.

### M1B: Gráficas en el PDF, con índice lateral
**Idea:** el PDF del experimento lleva **las seis gráficas** (no hay selección ahí) y un **índice interno de marcadores**, el que aparece en el panel izquierdo del lector de PDF. Ese índice **no es una página del documento**: no se imprime ni ocupa hoja.

| Tarea | Quién |
|---|---|
| Dibujar G1 a G6 en el PDF con iText (`PdfContentByte`, vectorial) usando el mismo servicio de datos de M1A y la especificación de cada gráfica | P (G1 y G2), m1 (G3, G4), m2 (G5, G6) |
| Sección «Gráficas» del PDF, cada una con su título, leyenda, n y nota de la barra de error | P |
| **Marcadores** (outline) del PDF, con jerarquía: Resumen · Resultados por grupo · Gráficas (con las seis como hijas) · Tabla por espécimen · Desglose por minuto · (en modo desarrollador: Modelo del análisis). Cada marcador salta a su página | P |
| Que el lector abra el PDF **con el panel de marcadores desplegado** (preferencia de visor `PageModeUseOutlines` de iText 5; verificarla) | P |
| Si hay un solo grupo o faltan datos, omitir la gráfica que no aplique y su marcador, sin romper el PDF | P |
| **PDF ya guardados:** los reportes se guardan (salvo en modo desarrollador). Un PDF generado antes de M1B no trae gráficas ni marcadores. El orquestador verifica cómo se guardan y los invalida o regenera para que ningún usuario reciba uno viejo | P |
| Prueba que lea los marcadores del PDF (`SimpleBookmark` de iText 5) y verifique títulos, jerarquía y que apuntan a páginas válidas | m1 |
| Prueba que verifique que cada gráfica existe en el PDF y que los números de la tabla coinciden con los de la pantalla | m2 |

**Listo cuando:** al abrir el PDF aparece a la izquierda el índice con las seis gráficas y cada una lleva a su página; el índice no sale impreso; el PDF se ve bien en blanco y negro; y los números coinciden con los de la pantalla.

## 8. Pruebas obligatorias antes de dar algo por terminado

- Frontend (desde `frontend/`): `npm run lint` y `npm test`, incluida la prueba de accesibilidad (axe).
- Backend: las pruebas contra PostgreSQL real (hoy son 178 y deben seguir pasando).
- Worker: sus pruebas corren dentro de Docker.
- Verificación visual: levantar el preview, abrir la pantalla, revisar consola y red sin errores, y probar ancho de teléfono. Para el PDF, abrirlo de verdad y comprobar el panel de marcadores y las gráficas.
- Reconstruir el contenedor `web` con `--no-cache` si el cambio es de interfaz (ya pasó que el bundle quedó viejo).

## 9. Formato de entrega de cada programador

1. Qué hizo, en 3 a 5 líneas.
2. Archivos tocados.
3. Qué pruebas corrió y su resultado **real** (si algo falló, decirlo con la salida).
4. Qué no pudo verificar.
5. Dudas para el orquestador (nunca para el usuario).

El orquestador revisa el diff y las pruebas antes de commitear; no confía en el resumen.

## 10. Decisiones del usuario

**Ya resueltas (8-nov-2026):**
1. Luz verde a M1A.
2. Todos ven todos los experimentos; no se agrega la opción de privado (necesitaría una columna nueva y el usuario no quiere tocar los diagramas).
3. Las gráficas se presentan como extra; el documento LaTeX se edita después.
4. No se cambia la base de datos ni los diagramas.
5. Solo se entrega lo que el Dr. Sandino pidió, más las gráficas.
6. La versión y huella del modelo en los reportes se quedan solo en modo desarrollador.
7. Las reglas de versión no cambian.

**Siguen siendo solo del usuario:** cuándo hacer push y cuándo desplegar al VPS, y cualquier cambio a LaTeX o diagramas.

## 11. Qué no se debe afirmar (para que sea defendible)

- No decir que una conducta es «exacta»: la clasificación del modelo no es 100 % exacta y su exactitud todavía no está medida. Hasta que haya F1 por conducta, todo se presenta como «sobre la salida del modelo».
- Una barra de error de ± DE no dice si una diferencia es significativa. Las gráficas no incluyen pruebas; decirlo en la nota de cada una.
- G5 mide la variación entre animales, no entre analistas. No presentarla como la reducción de variabilidad que pidió el Dr. Sandino.
- Un fármaco estimulante puede reducir la inmovilidad solo por aumentar la actividad, y este sistema no lo detecta; hace falta una prueba de actividad motora aparte.

## 12. Trabajo futuro (NO se hace ahora)

Descrito para que quede claro qué se dejó fuera y por qué. Nadie lo implementa sin una instrucción nueva del usuario.

| Módulo | Qué sería | Por qué queda fuera |
|---|---|---|
| Estadística dentro de un experimento | ANOVA con Dunnett o Kruskal-Wallis con Dunn, normalidad, tamaño del efecto, aviso de sensibilidad del ensayo | Sandino pidió media, DE y varianza; él hace su estadística por fuera. Habría que escribir las pruebas a mano en Java y verificarlas contra scipy |
| Tratamiento y experimento estructurados | Compuesto, dosis, vía, vehículo, cepa, sexo, edad o peso, temperatura del agua | Cambia el esquema y los diagramas |
| Comparar entre experimentos | Vista «Comparar» normalizada contra el control de cada experimento, curva dosis-respuesta | Depende de lo anterior; Sandino no lo pidió |
| Experimentos privados y consistencia entre usuarios | Marca de privado, medida de consistencia | Cambia el esquema; Sandino pidió que todos vean todo |
| Rigor y trazabilidad | Bitácora de cambios con detección de alteraciones, registro de asignación al azar y de análisis a ciegas, paquete reproducible | Cambia el esquema y los diagramas |
