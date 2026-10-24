# pipeline/: el modelo de fst_auto, tal cual

Este código **no es de este repositorio**: es una copia, sin cambios, del proyecto `fst_auto`
(carpeta hermana `../fst_auto`), rama **`wip/correccion-recuadro`**, commit **`6b27226`** (23-oct-2026): es `9844e57`
(2026-10-18, "Versiones del codigo de Colab y hojas ciegas de AcuBN2 y Acu-YBN-2") más la opción `--correccion` de
`etiquetar.py` (cambian `lib.py` y `etiquetar.py`). Sin `--correccion` da exactamente lo mismo que `9844e57`. Esa rama
todavía no está en `main` de fst_auto. Lo usa el worker real (`worker/worker.py`) para analizar los videos.

Archivos copiados:

```
lib.py  segundos.py  etiquetar.py
cnn/fst_cnn.py  cnn/fst_cnn_seg.py  cnn/recortar.py  cnn/recortar_seg.py  cnn/probar_seg.py
```

## Regla: aquí no se edita nada

Si el modelo o el código cambian, se cambia en `fst_auto` y se **vuelve a copiar** (los archivos de arriba y
`fst_cnn_seg.pt`), anotando aquí el commit nuevo. Así este repositorio y `fst_auto` nunca se separan y
el análisis del sistema sigue dando lo mismo que el análisis del laboratorio.

El modelo entrenado (`fst_cnn_seg.pt`, unos 125 MB) **no va a git**: vive en `modelos/` en la raíz del repo
(carpeta ignorada). `backend-spring/docker-compose.yml` la monta en el worker como `/data/modelos`.

## Los dos pasos

Se corren desde esta carpeta (`pipeline/`); los archivos se importan entre sí. `<nombre>` es el nombre del
video sin extensión.

1. **Cilindros y medidas** (sin `--modelo`: solo encuentra los tubos y mide al animal):

   ```
   python etiquetar.py VIDEO --tubos N --salida DIR
   ```

   Deja en `DIR`: `<nombre>_rasgos.csv` (recuadro `bx0..by1` de cada tubo, por bloque y por espécimen),
   `<nombre>_cuadros.csv` (medidas por fotograma) y `<nombre>_control.jpg` (las líneas sobre los tubos:
   hay que mirarla).

2. **Clasificación por segundo** con la red:

   ```
   python cnn/probar_seg.py DIR/<nombre>_rasgos.csv --red ../modelos/fst_cnn_seg.pt --video VIDEO
   ```

   Deja `DIR/<nombre>_cnnseg.csv` (un renglón por espécimen y segundo; la columna `conducta` vale
   `inmovilidad`, `nado`, `escalamiento` o `activa`) y `<nombre>_cnnseg_resumen.csv`.

En CPU tarda unos 5 a 10 minutos por video. La red se carga con `preentrenada=False`: no descarga nada de
internet. Dependencias: `worker/requirements.txt` (numpy 2, opencv, pandas, scikit-learn 1.9) más torch y
torchvision de la rueda de CPU.
