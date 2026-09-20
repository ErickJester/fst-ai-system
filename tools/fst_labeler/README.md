# Herramienta de etiquetado semi-automatico FST

FST = *forced swim test* (prueba de nado forzado).

Tooling **interno** para construir el conjunto de entrenamiento del sistema de
analisis conductual (TT 2026-B066, ESCOM-IPN / Laboratorio de Bioquimica
Estructural, ENMyH-IPN). Su producto final es un CSV de clips etiquetados
(nado, inmovilidad, escalamiento) listo para entrenar el clasificador de
produccion. **No es el sistema entregable del trabajo terminal** y no se
integra con el backend del sistema principal.

## Clases (definiciones operativas)

| Clase | Definicion |
|---|---|
| Escalamiento | Movimiento hacia delante de las patas anteriores sobre las paredes de la camara. |
| Natacion (nado) | Movimiento de desplazamiento en la camara de natacion. |
| Inmovilidad | El especimen no hace mayores intentos por escapar, excepto los movimientos necesarios para mantener el hocico fuera del agua. |

## Instalacion y ejecucion

```bash
cd tools/fst_labeler
pip install -r requirements.txt
python app.py --videos-dir /ruta/a/mis/videos
```

Abre `http://127.0.0.1:5055/`: ahi esta el visor cuadro a cuadro. La carpeta de videos tambien se puede fijar con
la variable de entorno `FST_VIDEOS_DIR`. **No hay rutas de laboratorio
codificadas en el programa**; si no se indica ninguna, se usa `videos/` dentro
de esta carpeta.

Opciones: `--host`, `--port`, `--jpeg-quality`, `--seek-forward-max`,
`--visor-max-ancho`, `--visor-prefetch`, `--visor-salto-cuadros`, `--debug`.

## Estado por modulos

| Modulo | Contenido | Estado |
|---|---|---|
| 1 | Servidor de cuadros y metadata | **listo** |
| 2 | Visor cuadro a cuadro en el navegador | **listo** |
| 3 | Dibujo de region de interes (ROI) y linea de agua | **listo** |
| 4 | Capa 0 + Capa 2: detector de movimiento por umbral | pendiente |
| 5 | Capa 1: modelo 3D preentrenado (Della Valle et al. 2025) | pendiente |
| 6 | Capa 3 simplificada: reglas geometricas sin pose | pendiente |
| 7 | Capa 5: consenso y cola de discrepancias | pendiente |
| 8 | Interfaz de revision humana | pendiente |
| 9 | Suavizado temporal y exportacion a CSV | pendiente |

## Modulo 1 — interfaz HTTP

Toda la metadata se lee del archivo real con OpenCV. Nada se asume: si el
contenedor no declara FPS (cuadros por segundo) o total de cuadros, se reporta
como desconocido y se marca en el campo `aviso`.

| Metodo y ruta | Devuelve |
|---|---|
| `GET /` | Indice de la interfaz HTTP en JSON. |
| `GET /api/salud` | Estado del servidor, carpeta de videos y version de OpenCV. |
| `GET /api/videos` | Videos disponibles (no los abre ni los decodifica). |
| `GET /api/videos/<video_id>/metadata` | FPS, total de cuadros, resolucion, codec, duracion. |
| `GET /api/videos/<video_id>/metadata?conteo_exacto=1` | Igual, pero contando los cuadros decodificables uno por uno (una pasada completa al archivo). |
| `GET /api/videos/<video_id>/frame/<n>` | El cuadro `n` (base 0) en JPEG. |

Parametros del cuadro: `calidad` (1-100) y `max_ancho` (reescala solo para
transporte; los encabezados `X-Ancho-Original` y `X-Alto-Original` conservan la
resolucion real). El encabezado `X-Indice-Cuadro` repite el cuadro entregado.

El `video_id` es la ruta del archivo relativa a la carpeta de videos
(por ejemplo `sesion_03/especimen_07.mp4`). Las rutas que intentan salir de esa
carpeta se rechazan.

### Exactitud del cuadro

Un salto (*seek*) en un contenedor comprimido aterriza en el cuadro clave mas
cercano, no en el cuadro pedido. El lector lleva la posicion por su cuenta y
avanza decodificando hasta el cuadro exacto, de modo que `frame/137` entrega
siempre el cuadro 137 del archivo. Si el contenedor ni siquiera permite saltar
con precision, el lector reabre el archivo y avanza desde el inicio: mas lento,
pero exacto.

### Video sintetico de prueba

```bash
python scripts/generar_video_prueba.py --fps 30 --segundos 60
```

Escribe `videos/prueba_sintetica.mp4` con **el numero de cuadro impreso en cada
cuadro**, mas `videos/prueba_sintetica_verdad.json` con los rangos de cuadros de
cada conducta simulada, la region de interes sugerida y la linea de agua (que
aparece inclinada a proposito: la camara puede no estar nivelada). Sirve para
probar la herramienta; **no sustituye material real de laboratorio**.

## Modulo 2 — visor cuadro a cuadro

Interfaz en `static/`: HTML, CSS y JavaScript sin framework, sin Node.js y sin
paso de compilacion. Se sirve en `http://127.0.0.1:5055/`.

| Archivo | Contenido |
|---|---|
| `static/index.html` | Estructura de la pagina. |
| `static/css/estilos.css` | Presentacion (tema oscuro: un fondo claro altera la percepcion del contraste del video). |
| `static/js/api.js` | Unica parte que conoce las rutas del servidor. |
| `static/js/visor.js` | Motor: pedir cuadros, pedir por adelantado, reproducir. |
| `static/js/dibujo.js` | Editor de regiones de interes y linea de agua. |
| `static/js/app.js` | Cableado de controles, atajos y dibujo de la pagina. |

El elemento del cuadro actual es un `<img>` con una capa `<canvas>` encima,
dentro de un marco que se ajusta al tamano real de la imagen mostrada para que
el dibujo no quede desplazado.

### Controles

Cuadro anterior y siguiente, salto de varios cuadros (tamano configurable),
primer y ultimo cuadro, barra de posicion, ir a un cuadro por numero,
reproduccion continua, pausa y multiplicador de velocidad (1x es la velocidad
real del archivo).

| Atajo | Accion |
|---|---|
| Flecha izquierda / derecha | Cuadro anterior / siguiente |
| Shift + flecha izquierda / derecha | Salto de varios cuadros |
| Espacio | Reproducir o pausar |
| Inicio / Fin | Primer / ultimo cuadro conocido |

Las teclas no actuan mientras el foco esta en un campo de texto. `R`, `W` y `Z`
quedan libres a proposito: son del Modulo 3.

### Reproduccion a velocidad real

La reproduccion se rige por reloj de pared, no por un contador de cuadros: en
cada paso se calcula que cuadro *deberia* verse segun el tiempo transcurrido y
se salta a el. Si los cuadros no alcanzan a llegar, se omiten los intermedios
antes que estirar el tiempo, de modo que 10 s de reproduccion son 10 s de
video. Los cuadros omitidos se cuentan y se muestran en la barra de estado en
lugar de disimularse.

Medido contra el video sintetico de 30 FPS: 1.60 s de reloj real corresponden
al cuadro 47, es decir 1.57 s de video (un cuadro de diferencia). Cada cuadro
cuesta ~2 ms de extremo a extremo, asi que el limite practico es la tasa de
refresco del navegador. Si la pestana pasa a segundo plano el navegador frena
su refresco y se ven menos cuadros, pero la posicion en el tiempo sigue siendo
correcta al volver.

Los cuadros que vienen se piden por adelantado de uno en uno y hacia delante:
el servidor mantiene un solo lector por video y lo serializa, asi que pedir en
paralelo no lo acelera y ademas puede obligarlo a retroceder, que es la
operacion cara en un contenedor comprimido.

### FPS y total de cuadros: nada se supone

- Si el archivo declara FPS confiables, se usan y se indica «leido del
  archivo». Si no, la reproduccion continua queda **desactivada** y se pide el
  valor real a mano; la navegacion cuadro a cuadro sigue funcionando.
- El total de cuadros se muestra como «(declarado)» mientras venga de la
  metadata del contenedor. El boton **Verificar total de cuadros** hace el
  conteo exacto (una pasada completa al archivo) y quita la marca.
- Si el servidor no logra entregar un cuadro, el visor toma el anterior como
  final del video y lo dice, en vez de fingir que el video es mas largo.

### Criterio de verificacion

1. `python scripts/generar_video_prueba.py --fps 30 --segundos 20`
2. `python app.py` y abrir `http://127.0.0.1:5055/`.
3. Elegir `prueba_sintetica.mp4` y navegar con flechas, con los botones y con
   «Ir al cuadro».

El video de prueba lleva **el numero de cuadro impreso en cada cuadro**: el
numero de la imagen y el indicador «cuadro N de M» deben coincidir siempre.

## Modulo 3 — regiones de interes y linea de agua

ROI = *region of interest* (region de interes): el contorno de una camara de
natacion dentro del cuadro.

### Cuadrilatero, no rectangulo

La ROI se dibuja como **cuadrilatero de cuatro esquinas**, no como rectangulo
alineado a los ejes. Dos razones:

- La camara web puede no estar nivelada, y entonces el contorno del recipiente
  no sale alineado a los ejes. Es la misma razon por la que la linea de agua
  son dos puntos y no una horizontal.
- El Modulo 5 tiene que **rectificar** el contorno a un rectangulo con una
  transformacion de perspectiva, tal como hace el preprocesamiento del modelo
  preentrenado, y eso exige las cuatro esquinas por separado.

Las cuatro esquinas se normalizan al guardarlas: quedan en sentido horario
empezando por la mas cercana a la superior izquierda, marcada con el tirador
relleno. Asi cuatro clics en cualquier orden producen el mismo cuadrilatero, no
uno cruzado en forma de mono, y todos los recortes salen orientados igual.

### Hasta cuatro regiones

El montaje del laboratorio graba **hasta cuatro especimenes por tanda en una
sola toma lateral**, asi que la herramienta acepta hasta cuatro regiones y las
**numera de izquierda a derecha** por la posicion de su centro. La numeracion se
recalcula al soltar el arrastre, no durante el gesto. Cada region lleva **su
propia linea de agua**: el nivel no tiene por que ser el mismo en las cuatro.

### Coordenadas

Todo se guarda en **pixeles del archivo original**. El cuadro que viaja al
navegador puede venir reescalado y ademas se muestra al tamano que quepa en la
ventana; unas coordenadas en pixeles de pantalla quedarian atadas al tamano del
navegador y no serviran para medir nada en los Modulos 4 a 6.

### Controles

| Atajo | Accion |
|---|---|
| `R` | Nueva region: marca sus cuatro esquinas |
| `W` | Linea de agua de la region activa: marca sus dos extremos |
| `Z` | Deshacer |
| `Supr` | Borrar la region activa |
| `Esc` | Cancelar el dibujo en curso |

Con el raton: arrastrar una esquina la mueve; arrastrar desde dentro mueve la
region entera; un clic dentro la selecciona. Deshacer revierte el gesto
completo, no cada paso del raton.

### Persistencia

Las regiones viven **por video y en la pagina**, como pide el plan de modulos:
siguen ahi al navegar entre cuadros y al cambiar de video y volver. Todavia no
se guardan en disco — la base sqlite llega en el Modulo 8, y hasta entonces
**recargar la pagina las pierde**.

### Criterio de verificacion

Con `videos/prueba_sintetica.mp4` (cuyo archivo de verdad sugiere la region
`x=102, y=48, 436x384` y la linea de agua `(102,172)-(538,187)`, inclinada a
proposito):

1. `R` y cuatro clics en las esquinas del recipiente, **en cualquier orden**.
   Las esquinas quedan guardadas en sentido horario desde la superior
   izquierda.
2. `W` y dos clics sobre la superficie del agua. La linea queda inclinada y se
   prolonga punteada hasta los bordes del cuadrilatero.
3. Navegar cuadros con las flechas: el dibujo sigue ahi, sin desplazarse.
4. Arrastrar una esquina y pulsar `Z`: vuelve exactamente a donde estaba y la
   linea de agua no se toca.

## Parametros a calibrar

Los valores por defecto de `fst_labeler/config.py` son **puntos de partida a
calibrar** con los videos del laboratorio, no resultados experimentales:
calidad JPEG 85, `seek_forward_max` 60 cuadros, 4 lectores abiertos,
ancho maximo de transporte 960 px, 8 cuadros pedidos por adelantado y
salto de 10 cuadros.

## Dependencias

Flask, OpenCV y NumPy. La interfaz es HTML, CSS y JavaScript servidos tal
cual. TensorFlow queda comentado en `requirements.txt` hasta el
Modulo 5, para que la instalacion de los modulos 1-4 no arrastre dependencias
que todavia no se usan. Sin Node.js, sin contenedores, sin paso de compilacion
de interfaz.
