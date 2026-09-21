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
| 4 | Capa 0 + Capa 2: detector de movimiento por umbral | **listo** |
| 5 | Capa 1: modelo 3D preentrenado (Della Valle et al. 2025) | **listo** |
| 6 | Capa 3 simplificada: reglas geometricas sin pose | **listo** |
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

## Modulo 4 — deteccion de movimiento por umbral

Capa 2 del pipeline (equivalente a DBscorer, reimplementado en Python con
OpenCV, sin MATLAB) mas la compuerta de cordura de la Capa 0.

| Archivo | Contenido |
|---|---|
| `fst_labeler/deteccion_movimiento.py` | El detector y la compuerta de cordura. |
| `fst_labeler/estabilizacion.py` | Seguimiento del movimiento de la camara. |
| `static/js/deteccion.js` | Panel de la interfaz y reporte. |

Rutas: `POST /api/videos/<video_id>/deteccion-movimiento` y
`POST /api/videos/<video_id>/estabilidad-camara`. Las regiones viajan en el
cuerpo de la peticion; **no se guardan en el servidor**, el navegador sigue
siendo su dueno hasta que el Modulo 8 introduzca sqlite.

### Por que sustraccion de fondo y no diferencia directa entre cuadros

Medido sobre material real del laboratorio (1280x720, 29.45 FPS, 5.3 min): la
diferencia entre cuadros consecutivos cambia el **37 %** del encuadre por
encima de 20 niveles de gris, y esa senal no viene del especimen. Viene de la
banda de agua agitada, que cruza los cuatro cilindros, y del temblor de la
camara sobre los bordes verticales de alto contraste. Compensar la traslacion
de la camara reduce esa senal solo un **2.8 %**: no alcanza.

La mediana temporal del recorte lo resuelve. El cilindro, la mesa y el nivel
medio del agua estan en casi todos los cuadros y sobreviven a la mediana; el
especimen esta en un sitio distinto en cada cuadro y desaparece de ella.
Restar esa mediana deja al especimen como un blanco solido. Es ademas lo que
hace el metodo original, que tambien parte de sustraccion de fondo.

Despues se aplica apertura morfologica y se conserva el **componente conexo
mayor**: el especimen es un cuerpo unico, y lo demas es reflejo, burbuja o
ruido de compresion. La puntuacion de cada par de cuadros es el area del XOR
entre mascaras consecutivas, dividida entre el area de la region.

### La camara se mueve

Los videos se graban con camara de mano. Medido sobre material real: la camara
se reencuadra unos **205 px en los primeros ~18 s** mientras se acomoda, y
despues conserva una deriva residual de **25 px de mediana y 78 px de
maxima**. Sobre un cilindro de ~300 px de ancho, 78 px es una cuarta parte del
cilindro.

El boton **Buscar inicio estable** recorre el video y devuelve el primer
cuadro a partir del cual la camara ya no se reacomoda. No usa una constante de
segundos a omitir: compara cada muestra contra la dispersion normal de la
segunda mitad del propio video. Sobre el material de prueba devolvio el cuadro
330 (11.2 s), con deriva posterior de 20.9 px de mediana y 68.7 px de maxima.

Durante el analisis, la transformacion de la camara se reestima cada segundo y
las esquinas de la region se arrastran con ella. El estimador se apoya en lo
que queda **fuera** de las regiones: si estas encierran los cilindros, lo de
fuera es la mesa, las paredes y el fondo, que no se mueven.

### Dos compuertas que avisan en vez de callar

**Capa 0, prior del laboratorio.** El porcentaje de bloques inmoviles se
contrasta contra **23.2-52.4 %**, que es el rango de la Tabla 4 de la tesis de
la Seccion de Posgrado (ENMyH-IPN) sobre especimenes Wistar hembra en el ensayo de
5 min, seis grupos experimentales. **No se usa el prior de ~75 % que circula
en la descripcion del proyecto**: ese valor no corresponde a este laboratorio,
cuyo maximo registrado es 52 %, y una compuerta anclada ahi marcaria como
sospechoso todo analisis correcto. Bogdanova et al. (Physiol Behav, 2017)
confirman que no hay un prior universal: en Wistar control la inmovilidad
publicada cubre todo el rango de 0 a 300 s segun el estudio.

**Separacion de la distribucion.** Otsu siempre devuelve un corte, incluso
sobre una distribucion de una sola joroba. El delator es la razon entre
varianza entre grupos y varianza total: una distribucion normal cortada en su
media da exactamente 2/pi = 0.64, y una con dos modos separados da bastante
mas. Por debajo de 0.75 el reporte avisa que el porcentaje no es interpretable
todavia.

Ninguna de las dos descarta el resultado: lo marcan y explican por que.

### Lo que esta capa NO decide

Distingue **inmovil de activo**, dos clases. No separa nado de escalamiento.
El Modulo 7 tiene que tratarla como un votante sobre el eje de inmovilidad, no
como un votante de tres clases.

### Criterio de verificacion

Con el video real del laboratorio, tres regiones sobre los cilindros,
analizando desde el cuadro 330:

- 62 bloques de 5 s por region (147 cuadros a 29.4524 FPS), **70 s** de
  computo para 9006 cuadros por tres regiones.
- Umbral de actividad 0.02459, calculado por Otsu sobre los bloques de las
  tres regiones juntas.
- Region 1: 71.0 % de inmovilidad; region 2: 77.4 %; region 3: 77.4 %.
- Las tres salieron **sospechosas**, y con razon: el laboratorio mide entre
  23 y 52 %. El detector sin calibrar sobreestima la inmovilidad unos 25
  puntos. Las dos compuertas lo dijeron en vez de entregar el numero en
  silencio.

Un solo umbral para todo el video, juntando los bloques de las regiones: el
umbral describe las condiciones de grabacion, no al especimen. Uno por region
normalizaria a cada especimen contra si mismo y todos saldrian cerca del 50 %,
borrando justo la diferencia entre especimenes que el experimento mide.

Hacer clic en un bloque del reporte lleva el visor a ese punto del video.

## Modulo 5 — modelo 3D preentrenado (Capa 1)

Red residual convolucional 3D de Della Valle et al. (2025). Predice **las tres
conductas** por clip de 3 s, con confianza. Es la primera capa que separa nado
de escalamiento.

| Archivo | Contenido |
|---|---|
| `fst_labeler/modelo_3d.py` | Preprocesamiento, carga de pesos e inferencia. |
| `static/js/modelo.js` | Panel de la interfaz. |

Rutas: `POST /api/videos/<video_id>/modelo-3d` y `GET /api/modelo-3d/estado`,
que dice si hay pesos y TensorFlow sin cargar nada.

### De donde bajar los pesos, y por que importa

De **Zenodo**, DOI 10.5281/zenodo.14638257, que declara **CC-BY-4.0**. El
repositorio de GitHub tiene los mismos archivos pero **sin licencia declarada**,
lo que por omision significa todos los derechos reservados. Citar a los autores,
como exige CC-BY.

El deposito completo pesa 8.2 GB, casi todo dataset de entrenamiento. Solo hace
falta `ML_FST/src/TrainedModels/classes3/Model/model.h5`, **10.7 MB**. Se deja
en `modelo_3drcnn/`, fuera del control de versiones.

### TensorFlow

Los autores declaran TensorFlow 2.7.0, de 2021, que no tiene ruedas para
Python 3.10 o superior. Sus pesos son **Keras 2.6**, y TensorFlow 2.16 en
adelante trae Keras 3, que no los carga. La combinacion verificada es:

```
pip install tensorflow==2.17.1 tf-keras==2.17.0
```

cargando con `tf_keras` y `TF_USE_LEGACY_KERAS=1`. TensorFlow se importa dentro
de la funcion de carga, no al principio del archivo, para que los Modulos 1 a 4
sigan funcionando sin el.

### El preprocesamiento sale del codigo, no del articulo

Leyendo `src/functionsDatagenerator.py` y `Predictor/main.py` de los autores
aparecen varias cosas que la descripcion del proyecto tenia distintas:

- El tensor es **(75, 128, 64, 1)**: 128 de alto y 64 de ancho. La descripcion
  dice "75x64x128"; alimentarlo transpuesto da basura.
- La resta del fondo es `cv2.subtract` en BGR --saturada, no valor absoluto--
  y **despues** se pasa a gris.
- En prediccion **no aplican desenfoque**: la funcion trae 5 por omision pero
  el programa principal pasa 1, y solo se aplica por encima de 1.
- La normalizacion es min-max sobre el clip completo, no division entre 255.
- El orden de esquinas es horario desde la superior izquierda, el mismo que
  normaliza el Modulo 3.

### Dos adaptaciones para nuestro material

**Remuestreo a 25 Hz.** Su codigo lee el FPS del archivo y acto seguido lo
sustituye por 25 fijo, y toma 75 cuadros **consecutivos**. En sus videos, que
son de 25 Hz, eso son 3 s. En los nuestros, de 29.45 Hz, serian 2.55 s y el
movimiento entre cuadros se veria mas lento del que el modelo aprendio, lo que
sesga hacia inmovilidad. Por omision se remuestrea; `remuestrear=False`
reproduce su comportamiento literal.

**Fondo por mediana temporal.** Ellos restan el primer cuadro del archivo, que
en su protocolo esta vacio porque empiezan a grabar antes de meter al
especimen; por eso su `start_time_array` va de 7 a 28 s. En nuestro material
eso no se cumple: en el primer cuadro hay cilindros que ya tienen especimen y
otro al que lo estan soltando, y ademas la camara se mueve despues. La mediana
temporal del recorte enderezado estima lo mismo y si existe siempre.

### Criterio de verificacion

**Contra la puntuacion manual de los autores.** Se alimentaron al modelo los
100 tensores ya preprocesados del especimen Rat-0 de su Exp1 y se compararon
contra su anotacion humana:

| Clase | Soporte | Precision | Recall | F1 |
|---|---|---|---|---|
| inmovilidad | 38 | 0.87 | 0.89 | 0.88 |
| nado | 28 | 0.80 | 0.86 | 0.83 |
| escalamiento | 34 | 0.97 | 0.88 | 0.92 |

**88.0 % de acuerdo global**, consistente con el 86 +/- 4 % que reportan. Eso
verifica la carga de los pesos, la orientacion del tensor y la correspondencia
entre la salida softmax y las conductas, que no es la alfabetica: el indice 0
es nado, el 1 inmovilidad y el 2 escalamiento.

**Sobre video, en la configuracion literal de los autores.** Su video
`Trial195.mpg` con su ROI y su `start_time` de 28 s: 100 clips en 23 s, 60 %
nado, 31 % inmovilidad, 9 % escalamiento, confianza media 0.85.

**Sobre material del laboratorio.** Video de 1280x720 a 29.45 Hz, tres
regiones, desde el cuadro 330: 100 clips por region en 58 s.

| Region | nado | inmovilidad | escalamiento | Confianza media | Clips bajo 0.70 |
|---|---|---|---|---|---|
| 1 | 63 % | 20 % | 17 % | 0.834 | 24 |
| 2 | 62 % | 23 % | 15 % | 0.810 | 31 |
| 3 | 43 % | 39 % | 18 % | 0.801 | 34 |

La confianza baja de 0.85 a 0.81 y un tercio de los clips queda por debajo de
0.70, que es el umbral con el que el sistema principal detiene el analisis
(RN-11). Es la caida esperable por el cambio de montaje, y el reporte la
declara en vez de callarla.

Comparado con lo que mide el laboratorio (inmovilidad 23-52 %, nado 17-45 %,
escalamiento 31-37 %), el modelo **sobreestima el nado y subestima el
escalamiento**. Y discrepa fuerte con el detector del Modulo 4, que sobre el
mismo video daba 71-77 % de inmovilidad. Esa discrepancia es precisamente lo
que el Modulo 7 tiene que arbitrar.

## Modulo 6 — reglas geometricas sin pose (Capa 3)

Las tres definiciones operativas del laboratorio son geometricas, asi que se
pueden escribir como reglas sobre coordenadas. Esta capa las escribe.

| Archivo | Contenido |
|---|---|
| `fst_labeler/reglas_geometricas.py` | Medicion, las tres reglas y el voto por bloque. |
| `static/js/reglas.js` | Panel de la interfaz. |

Ruta: `POST /api/videos/<video_id>/reglas-geometricas`.

### Puntos clave sin modelo de pose

El plan original de esta capa pedia nariz, base de la cola y las dos patas
anteriores con DeepLabCut o SLEAP. Entrenar eso es un proyecto en si mismo y
queda fuera de la herramienta. Aqui los puntos clave salen de la **misma
mascara del especimen que segmenta el Modulo 4**:

| Punto clave | De donde sale |
|---|---|
| Centroide | Centro de masa de la mascara. |
| Caja del cuerpo | Extremos de la mascara; su relacion alto/ancho es la verticalidad. |
| Cuerpo anterior | Franja superior de la mascara (un tercio, configurable). Cuando el especimen trepa, las patas anteriores son lo mas alto y lo mas pegado a la pared que hay en la mascara. |
| Masa sobre el agua | Fraccion de la mascara por encima de la linea de agua del Modulo 3. |

Es un sustituto, no un equivalente: la mascara no distingue una pata de la
cabeza, y un especimen que asoma el hocico para respirar produce masa sobre el
agua igual que uno que trepa. Por eso la regla de escalamiento exige las tres
senales a la vez.

### Las tres reglas

```
escalamiento  proximidad_pared < umbral  Y  verticalidad > umbral
              Y  sobre_agua > umbral   (si la region tiene linea de agua)
inmovilidad   desplazamiento < umbral
nado          el resto
```

**El escalamiento se comprueba antes que la inmovilidad**, y no es un detalle
de implementacion. Un especimen trepando apenas mueve su centro de masa --sube
y baja pegado al cilindro-- asi que la regla de inmovilidad lo atrapa antes de
que la de escalamiento llegue a verlo. La definicion operativa lo prohibe:
inmovilidad es «no hacer mayores intentos por escapar», y trepar es
precisamente un intento de escapar. Sobre el video sintetico de prueba la fase
de escalamiento tiene el desplazamiento **mas bajo** de las tres, y con el
orden inverso el 100 % del video salia inmovil.

Sin linea de agua dibujada la tercera senal no se puede evaluar y la regla
decide con las otras dos. El reporte lo avisa: es una regla mas debil.

### Unidades: ningun umbral en pixeles

Las dimensiones del recipiente cambian entre montajes, asi que nada se mide en
pixeles crudos. Todo va sobre el recorte enderezado y normalizado:

| Medida | Unidad |
|---|---|
| `desplazamiento` | Fracciones del **alto** de la region **por segundo**. Por segundo y no por cuadro, para que no dependa del FPS del archivo. |
| `proximidad_pared` | Fraccion del **ancho** de la region. Menor es mas pegado. |
| `verticalidad` | Alto/ancho de la caja del cuerpo. Adimensional. |
| `sobre_agua` | Fraccion del area del cuerpo. Adimensional. |

### Ningun umbral trae valor de fabrica

Los cuatro se calculan por Otsu sobre la distribucion del propio video, igual
que el umbral de actividad del Modulo 4, y el reporte declara **cuanto separa
cada corte**: Otsu devuelve uno aunque la distribucion tenga una sola joroba.
Se pueden fijar a mano uno por uno desde el panel.

La cola se recorta al percentil 99 antes del corte. Las medidas por cuadro
tienen cola larga --un salto de segmentacion mueve el centroide de golpe-- y
Otsu reparte sus niveles entre el minimo y el maximo, asi que un solo valor
extremo arrastra el corte hacia arriba. Medido sobre el video sintetico: sin
recorte el corte de desplazamiento salia en 0.83 con la mediana de la fase de
nado en 0.35, y todo el video caia del lado inmovil.

Los cuatro umbrales se calculan sobre **todos** los cuadros medidos. Una
version previa calculaba los tres del escalamiento solo sobre los cuadros
activos, suponiendo que trepar es una forma de actividad; es falso por la
misma razon que obliga a comprobar el escalamiento primero, y el corte de
verticalidad se calculaba sin ver ni un solo cuadro de trepada.

### Voto por bloque

Cada cuadro recibe una clase y el bloque se queda con la predominante, como
manda Detke et al. (1995). El **margen del voto** viaja en el reporte: es lo
que el Modulo 7 puede comparar contra la confianza del modelo del Modulo 5. Un
empate se resuelve por el orden de las reglas y se marca; un bloque empatado
deberia entrar a la cola de revision aunque las demas capas coincidan con el.

### Limite de la independencia entre capas

Esta capa mide sobre la misma mascara que el Modulo 4, a proposito. Lo que las
hace fuentes distintas es la **regla de decision**, no la segmentacion: la
Capa 2 mira cuanta area cambia entre cuadros y decide inmovil o activo; esta
mira donde esta esa area y que forma tiene, y decide entre las tres conductas.
Segmentar dos veces por separado haria que discreparan por el recorte y no por
la conducta, y el consenso del Modulo 7 no podria distinguir una cosa de la
otra. Es un limite real de la independencia entre capas y hay que tenerlo
presente al leer el consenso.

### Criterio de verificacion

Con `videos/prueba_sintetica.mp4` (20 s a 30 FPS), cuyo archivo de verdad
declara cuatro fases que caen exactamente en cuatro bloques de 5 s: nado,
nado, inmovilidad, escalamiento.

Con los cuatro umbrales en automatico, **3 de 4 bloques correctos**:

| Bloque | Esperado | Predicho | Margen | Desplazamiento | Verticalidad |
|---|---|---|---|---|---|
| 1 | nado | inmovilidad | 0.51 | 0.323 | 0.52 |
| 2 | nado | nado | 0.52 | 0.387 | 0.52 |
| 3 | inmovilidad | inmovilidad | 0.92 | 0.174 | 0.52 |
| 4 | escalamiento | escalamiento | **1.00** | 0.110 | **1.62** |

El bloque que falla lo hace con 76 votos contra 73: el corte automatico de
desplazamiento cae en 0.3275 y la mediana del bloque en 0.3231. El reporte ya
lo habia avisado --la razon de varianzas del desplazamiento es 0.73, por
debajo del 0.75 que se exige-- y el margen de 0.51 lo declara como bloque
repartido. **Fijando el umbral de desplazamiento a mano en 0.25 salen los
cuatro bloques correctos**, que es justo lo que el aviso recomienda hacer.

El video sintetico sirve para verificar que la cadena funciona de extremo a
extremo; **no sustituye material real de laboratorio** y ajustar las reglas
para acertar cuatro de cuatro sobre el seria sobreajustar a un artefacto.

## Parametros a calibrar

Los valores por defecto de `fst_labeler/config.py` son **puntos de partida a
calibrar** con los videos del laboratorio, no resultados experimentales:
calidad JPEG 85, `seek_forward_max` 60 cuadros, 4 lectores abiertos,
ancho maximo de transporte 960 px, 8 cuadros pedidos por adelantado,
salto de 10 cuadros, umbral de binarizacion 40, bloque de 5 s, 40 muestras
de fondo y reestimacion de camara cada 30 cuadros.

## Dependencias

Flask, OpenCV y NumPy. La interfaz es HTML, CSS y JavaScript servidos tal
cual. TensorFlow queda comentado en `requirements.txt` hasta el
Modulo 5, para que la instalacion de los modulos 1-4 no arrastre dependencias
que todavia no se usan. Sin Node.js, sin contenedores, sin paso de compilacion
de interfaz.
