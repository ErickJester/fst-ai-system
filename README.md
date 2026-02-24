# FST Rat Tracker — YOLO (WSL) Setup + Dataset + Training

Este README documenta **exactamente** el flujo que estamos usando tras cambiar a **YOLO**:

- **Una sola clase**: `rat`
- **Identidad por ROI (zona fija)**:
  - ROI1 → Rata 1
  - ROI2 → Rata 2
  - ROI3 → Rata 3
  - ROI4 → Rata 4
- **Política “siempre bbox”** (en el tracker):
  - Si falta detección en un ROI, se **congela** el último bbox por algunos frames con confianza que decae.
  - El pipeline no recibe “huecos” de bbox (mientras haya habido al menos 1 detección previa).

---

## 0) Requisitos

- WSL (Ubuntu recomendado).
- Para abrir LabelImg con ventana:
  - Windows 11 con **WSLg** (normalmente ya viene), o
  - si no hay GUI disponible, usa un etiquetador web (ver sección alternativa).

---

## 1) Paquetes base (apt)

```bash
sudo apt update
sudo apt install -y ffmpeg git curl
```

- `ffmpeg`: extraer frames (imágenes) desde un video.

---

## 2) Instalar Miniconda (sin pip)

> Esto instala conda en tu usuario (no toca el sistema).

### 2.1 Descargar e instalar

**x86_64 (lo normal):**
```bash
cd ~
curl -fsSL -o miniconda.sh https://repo.anaconda.com/miniconda/Miniconda3-latest-Linux-x86_64.sh
bash miniconda.sh -b -p "$HOME/miniconda3"
```

**ARM (si `uname -m` da `aarch64`):**
```bash
cd ~
curl -fsSL -o miniconda.sh https://repo.anaconda.com/miniconda/Miniconda3-latest-Linux-aarch64.sh
bash miniconda.sh -b -p "$HOME/miniconda3"
```

### 2.2 Activar conda
```bash
"$HOME/miniconda3/bin/conda" init bash
source ~/.bashrc
```

### 2.3 (Recomendado) no activar `base` automáticamente
```bash
conda config --set auto_activate_base false
source ~/.bashrc
```

---

## 3) Crear entorno del proyecto (Python 3.11)

> Importante: evitamos Python 3.13 porque suele romper dependencias como PyTorch.

```bash
conda create -n fst-yolo python=3.11 -y
conda activate fst-yolo
```

---

## 4) Evitar el error ToS (Terms of Service) en conda

Si conda exige aceptar ToS por `defaults`, la forma más simple es usar **solo conda-forge en este entorno**:

```bash
conda config --env --remove channels defaults
conda config --env --add channels conda-forge
conda config --env --set channel_priority strict
conda config --env --show channels
```

La salida debe mostrar **solo** `conda-forge`.

---

## 5) Instalar dependencias (YOLO + OpenCV + PyTorch)

```bash
conda install ultralytics opencv numpy pytorch torchvision torchaudio -y
```

Verifica que todo esté:

```bash
python -c "import torch, cv2; from ultralytics import YOLO; print('torch', torch.__version__); print('cv2', cv2.__version__); print('ultralytics OK')"
yolo --help
```

---

## 6) Estructura del dataset (YOLO)

En la raíz del proyecto:

```bash
mkdir -p dataset/images/all dataset/labels/all
mkdir -p dataset/images/train dataset/images/val
mkdir -p dataset/labels/train dataset/labels/val
```

---

## 7) Extraer frames desde un video

Ejemplo: 2 imágenes por segundo (ajusta `fps=` según necesites):

```bash
ffmpeg -i data/videos/corto.mp4 -vf fps=2 dataset/images/all/%06d.jpg
```

- Si salen demasiadas: usa `fps=1`.
- Si salen pocas: usa `fps=3` o `fps=4`.

---

## 8) Instalar LabelImg (para etiquetar cajas)

```bash
conda activate fst-yolo
conda install -c conda-forge labelimg -y
```

Ejecuta:

```bash
labelImg
```

Si no se encuentra:
```bash
hash -r
labelImg
```

---

## 9) Si LabelImg se cierra (bug de floats / Qt)

A veces Qt manda coordenadas con decimales y LabelImg truena al dibujar.

### 9.1 Intento rápido: forzar escala 1.0
Ejecuta LabelImg así:

```bash
export QT_AUTO_SCREEN_SCALE_FACTOR=0
export QT_ENABLE_HIGHDPI_SCALING=0
export QT_SCALE_FACTOR=1
export QT_SCALE_FACTOR_ROUNDING_POLICY=RoundPreferFloor
labelImg
```

### 9.2 Parche (si sigue fallando)
Si aparece error en:
`.../site-packages/libs/canvas.py`

Cambia llamadas de dibujo para forzar `int(...)`, por ejemplo:

**Antes**
```python
p.drawRect(left_top.x(), left_top.y(), rect_width, rect_height)
```

**Después**
```python
p.drawRect(int(left_top.x()), int(left_top.y()), int(rect_width), int(rect_height))
```

Lo mismo para `drawLine`, `drawEllipse`, etc. si vuelven a fallar.

---

## 10) Configurar LabelImg para formato YOLO

Dentro de LabelImg:

1) **Open Dir** → `dataset/images/all`  
2) **Change Save Dir** → `dataset/labels/all`  
3) Cambia el formato de guardado a **YOLO** (si ves “PascalVOC”, cámbialo a YOLO)  
4) En el panel derecho:
   - activa **Use default label**
   - escribe: `rat`
5) (Si existe) activa **Auto Save Mode**.

---

## 11) Etiquetado: cuántas cajas por imagen

Si una imagen tiene 4 ratas visibles (4 cilindros), debes dibujar:

- **4 cajas**
- todas con etiqueta `rat`

Reglas:
- Si la rata se ve: caja.
- Si está parcial: caja sobre lo visible.
- Si no aparece: no inventes.

---

## 12) Validación rápida de etiquetas

Tras etiquetar 2–3 imágenes:

```bash
ls dataset/labels/all | head
head -n 5 dataset/labels/all/000001.txt
```

Debe verse así (ejemplo):
```
0 0.52 0.61 0.18 0.35
0 0.26 0.59 0.16 0.33
...
```

---

## 13) Separar train/val (90%/10%)

### 13.1 Generar listas aleatorias
```bash
cd dataset/images/all
ls *.jpg | shuf > /tmp/all_images.txt
python - <<'PY'
import pathlib
p = pathlib.Path("/tmp/all_images.txt")
imgs = p.read_text().splitlines()
n = len(imgs)
val = set(imgs[: max(1, n//10)])
train = imgs[max(1, n//10):]
pathlib.Path("/tmp/val.txt").write_text("\n".join(sorted(val)))
pathlib.Path("/tmp/train.txt").write_text("\n".join(sorted(train)))
print("total", n, "train", len(train), "val", len(val))
PY
```

### 13.2 Mover imágenes
Desde la raíz del proyecto:

```bash
xargs -a /tmp/train.txt -I{} mv dataset/images/all/{} dataset/images/train/{}
xargs -a /tmp/val.txt   -I{} mv dataset/images/all/{} dataset/images/val/{}
```

### 13.3 Mover etiquetas correspondientes
```bash
python - <<'PY'
from pathlib import Path
import shutil

root = Path("dataset")

def move_labels(img_dir, lbl_dir):
    for img in img_dir.glob("*.jpg"):
        lab = root/"labels/all"/(img.stem + ".txt")
        if lab.exists():
            shutil.move(str(lab), str(lbl_dir/(img.stem + ".txt")))
        else:
            print("FALTA ETIQUETA:", img.name)

move_labels(root/"images/train", root/"labels/train")
move_labels(root/"images/val",   root/"labels/val")
PY
```

---

## 14) Crear `dataset.yaml`

En la raíz del proyecto crea `dataset.yaml` con:

```yaml
path: dataset
train: images/train
val: images/val
names:
  0: rat
```

---

## 15) Entrenar YOLO y generar `weights/rat.pt`

Entrenar (desde cero con base YOLOv8n):

```bash
conda activate fst-yolo
yolo train data=dataset.yaml model=yolov8n.pt epochs=100 imgsz=640
```

Al terminar:
- `runs/detect/train/weights/best.pt`

Copiar:

```bash
mkdir -p weights
cp runs/detect/train/weights/best.pt weights/rat.pt
```

---

## 16) Ejecutar el tracker (YOLO)

```bash
conda activate fst-yolo
python run_tracker.py "data/videos/corto.mp4"
```

Si tu `run_tracker.py` permite `--model`:

```bash
python run_tracker.py "data/videos/corto.mp4" --model weights/rat.pt
```

---

## 17) Mejorar con otro video (fine-tuning)

Si el modelo falla con un video distinto:

1) extrae frames SOLO de las partes donde falla,
2) etiqueta esas imágenes,
3) agrega al dataset y vuelve a entrenar **partiendo del modelo anterior**:

```bash
yolo train data=dataset.yaml model=weights/rat.pt epochs=50 imgsz=640
```

Guarda versiones:
- `weights/rat_v1.pt`, `weights/rat_v2.pt`, etc.

---

## Alternativa si NO puedes abrir LabelImg (sin GUI)

Usa un etiquetador web como **makesense.ai**, exporta en formato YOLO y coloca los `.txt` junto a las imágenes.

---

## Recomendación mínima de cantidad
- **150 imágenes** etiquetadas (mínimo realista).
- Mejor: **250**.
