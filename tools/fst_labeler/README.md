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

Abre `http://127.0.0.1:5055/`. La carpeta de videos tambien se puede fijar con
la variable de entorno `FST_VIDEOS_DIR`. **No hay rutas de laboratorio
codificadas en el programa**; si no se indica ninguna, se usa `videos/` dentro
de esta carpeta.

Opciones: `--host`, `--port`, `--jpeg-quality`, `--seek-forward-max`, `--debug`.

## Estado por modulos

| Modulo | Contenido | Estado |
|---|---|---|
| 1 | Servidor de cuadros y metadata | **listo** |
| 2 | Visor cuadro a cuadro en el navegador | pendiente |
| 3 | Dibujo de region de interes (ROI) y linea de agua | pendiente |
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

## Parametros a calibrar

Los valores por defecto de `fst_labeler/config.py` son **puntos de partida a
calibrar** con los videos del laboratorio, no resultados experimentales:
calidad JPEG 85, `seek_forward_max` 60 cuadros, 4 lectores abiertos.

## Dependencias

Flask, OpenCV y NumPy. TensorFlow queda comentado en `requirements.txt` hasta el
Modulo 5, para que la instalacion de los modulos 1-4 no arrastre dependencias
que todavia no se usan. Sin Node.js, sin contenedores, sin paso de compilacion
de interfaz.
