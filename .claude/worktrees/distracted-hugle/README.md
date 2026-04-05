# FST Rat Tracker

Sistema web para análisis automático de conducta en el **Prueba de Nado Forzado (FST)** — Forced Swim Test. Detecta y rastrea ratas en video, clasifica su conducta por ventanas de 1 segundo y presenta los resultados en una interfaz web.

---

## Qué hace el sistema

1. El usuario sube un video del experimento FST desde la interfaz web.
2. Un worker de background lo procesa automáticamente:
   - Detecta y rastrea cada rata usando **YOLOv8** con pesos personalizados.
   - Clasifica la conducta por ventanas de 1 segundo: **Nado**, **Inmovilidad**, **Escape**.
   - Genera un video anotado y un JSON con detalle por frame.
3. La interfaz muestra los resultados con gráficas y tablas por rata.

### Conductas clasificadas

| Conducta | Descripción |
|---|---|
| **Nado** | Movimiento activo en el agua |
| **Inmovilidad** | Sin movimiento sostenido (indicador de desesperanza) |
| **Escape** | Postura vertical / intentos de salir del cilindro |

---

## Arquitectura

```
frontend (React)  ──►  backend API (Flask)  ──►  PostgreSQL
                              │
                         worker (bg)
                              │
                    pipeline de análisis
                    ├── tracker.py      (YOLO + ByteTrack)
                    ├── classifier.py   (ResNet-18 / ResNet-50)
                    └── run_analysis.py (clasificación + salida)
```

### Componentes

| Componente | Tecnología | Puerto |
|---|---|---|
| Frontend | React 18 + Vite | 5173 |
| API | Flask + SQLAlchemy | 8000 |
| Worker | Python (background) | — |
| Base de datos | PostgreSQL 16 | 5432 |

---

## Interfaz web

**Dashboard** — lista de experimentos con estado, filtro y búsqueda.

**Nuevo experimento** — wizard de 3 pasos:
1. Metadatos (nombre, tratamiento, especie, número de animales, duración).
2. Subida de video (Day 1 requerido, Day 2 opcional; .mp4/.avi/.mov hasta 2 GB).
3. Confirmación y envío.

**Progreso** — polling en vivo del estado del job (QUEUED → RUNNING → DONE).

**Resultados** — por rata:
- Gráfica de barras apiladas (nado / inmovilidad / escape).
- Tabla con segundos y porcentaje por conducta.
- Vista detallada por ventana de 1 segundo.

---

## Pipeline de análisis

```
video
  │
  ├── compute_rois()         detección automática de ROIs por cilindro
  ├── build_bg()             modelo de fondo (mediana de primeros 60 frames)
  ├── StableWaterline        línea de agua por ROI (EMA α=0.12)
  │
  ├─ por frame ─────────────────────────────────────────────────────
  │   ├── YOLOEngine         YOLOv8 + ByteTrack  →  bbox por rata
  │   ├── detect_classic_roi fallback clásico si YOLO no detecta
  │   ├── PhysicalGate       filtra bboxes físicamente implausibles
  │   ├── ROIState           política freeze (hasta 20 frames, conf. decayente)
  │   └── FSTClassifier      voto CNN por frame (si hay pesos)
  │
  └─ por ventana (1 s) ──────────────────────────────────────────────
      ├── CNN disponible →   voto mayoritario de frames  ("cnn")
      └── sin CNN        →   árbol heurístico            ("heuristic")
              escape:    aspect_ratio > 1.6 AND motion ≥ 6.5
              immobile:  motion < 6.5 AND disp < 8 px AND pos_std < 20 px
              swim:      resto
```

### Clasificador CNN (dual)

| Modelo | Rol | Pesos |
|---|---|---|
| ResNet-18 | Primario (rápido) | `weights/fst_resnet18.pt` |
| ResNet-50 | Respaldo (conf < 0.65) | `weights/fst_resnet50.pt` |

Si los pesos no existen, la clasificación recae automáticamente en la heurística.

---

## Ejecución con Docker

```bash
docker-compose up
```

Levanta los 4 servicios (db, api, worker, frontend).
Accede en: **http://localhost:5173**

### Variables de entorno relevantes

| Variable | Default |
|---|---|
| `DATABASE_URL` | `postgresql+psycopg2://fst:fst@db:5432/fst` |
| `DATA_DIR` | `/data` |
| `UPLOAD_MAX_MB` | `2048` |
| `VITE_API_BASE` | `http://localhost:8000` |

---

## Ejecución desde CLI (sin web)

```bash
# análisis completo con video anotado y JSON
python run_analysis.py data/videos/video.mp4 \
    --output-video salida.mp4 \
    --output-json resultado.json

# tracker solamente (sin clasificación de conducta)
python run_tracker.py data/videos/video.mp4
```

---

## Entrenamiento del clasificador CNN

Si tienes videos ya analizados por la heurística, puedes bootstrap-ear el dataset:

```bash
# 1. extraer crops etiquetados desde el JSON de análisis
python scripts/train_classifier.py extract-crops \
    --video data/videos/video.mp4 \
    --analysis-json resultado.json

# 2. revisar manualmente dataset/behavior/train/{swim,immobile,escape}/

# 3. entrenar ResNet-18
python scripts/train_classifier.py train --arch resnet18 --epochs 30
# → guarda en weights/fst_resnet18.pt

# 4. entrenar ResNet-50 (opcional, para el respaldo)
python scripts/train_classifier.py train --arch resnet50 --epochs 30
```

---

## Entrenamiento del detector YOLO

Si necesitas re-entrenar el modelo de detección de ratas (`weights/rat.pt`):

### Requisitos
```bash
conda create -n fst-yolo python=3.11 -y
conda activate fst-yolo
conda install ultralytics opencv numpy pytorch torchvision -y
```

### Extraer frames y etiquetar
```bash
# extraer 2 fps del video
ffmpeg -i data/videos/video.mp4 -vf fps=2 dataset/images/all/%06d.jpg

# etiquetar con LabelImg (formato YOLO, clase única: rat)
labelImg
```

### Separar train/val y entrenar
```bash
# separar 90/10
python - <<'PY'
import pathlib, random
imgs = list(pathlib.Path("dataset/images/all").glob("*.jpg"))
random.shuffle(imgs)
n_val = max(1, len(imgs) // 10)
for p in imgs[:n_val]:  p.rename(f"dataset/images/val/{p.name}")
for p in imgs[n_val:]:  p.rename(f"dataset/images/train/{p.name}")
PY

# entrenar desde YOLOv8n base
yolo train data=dataset.yaml model=yolov8n.pt epochs=150 imgsz=960

# copiar mejor modelo
cp runs/detect/train/weights/best.pt weights/rat.pt
```

`dataset.yaml`:
```yaml
path: dataset
train: images/train
val:   images/val
names:
  0: rat
```

**Cantidad mínima recomendada**: 250 imágenes etiquetadas.

---

## Estructura del proyecto

```
fst-ai-system/
├── backend/
│   ├── app/            API Flask (rutas, modelos DB, configuración)
│   ├── pipeline/       tracker.py, run_analysis.py, classifier.py
│   └── worker/         worker.py (polling de jobs)
├── frontend/
│   └── src/
│       └── pages/      Dashboard, NewExperiment, Progress, Results
├── scripts/
│   └── train_classifier.py
├── weights/
│   └── rat.pt          modelo de detección
├── dataset/            imágenes y etiquetas YOLO
├── docker-compose.yml
├── run_analysis.py     CLI
└── run_tracker.py      CLI (tracker solo)
```
