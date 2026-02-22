# FST System — Sistema de Análisis de Nado Forzado

## Inicio rápido (solo tracker, sin Docker)

### 1. Instalar dependencias

```bash
pip install opencv-python-headless numpy
```

### 2. Ejecutar con un video

```bash
python run_tracker.py mi_video.mp4
```

Esto genera:
- `mi_video_tracked.mp4` — video con bounding boxes de colores sobre cada rata
- `mi_video_tracking.json` — coordenadas (x, y, w, h) de cada rata por frame

### Opciones

```bash
# Forzar layout (por defecto se auto-detecta)
python run_tracker.py mi_video.mp4 --layout 1x4
python run_tracker.py mi_video.mp4 --layout 2x2
```

---

## Sistema web completo (con Docker)

### 1. Configurar

```bash
cp .env.example .env
# Editar .env si es necesario
```

### 2. Levantar

```bash
docker compose up --build
```

Esto levanta:
- **backend** (Flask API) en `http://localhost:5000`
- **worker** (procesamiento asíncrono de videos)
- **postgres** (base de datos)
- **frontend** (React) en `http://localhost:5173`

### 3. Usar

1. Abrir `http://localhost:5173`
2. Crear una sesión
3. Subir un video (día 1 o día 2)
4. Lanzar análisis
5. Ver resultados (tiempos de nado, inmovilidad, escape por rata)

---

## Estructura del proyecto

```
├── run_tracker.py              ← Script standalone (sin Docker)
├── docker-compose.yml
├── .env.example
├── backend/
│   ├── app/
│   │   ├── main.py             ← Flask API
│   │   ├── models.py           ← Modelos SQLAlchemy
│   │   ├── db.py               ← Conexión a BD
│   │   ├── schema.py           ← Inicialización de tablas
│   │   ├── storage.py          ← Gestión de archivos
│   │   └── config.py           ← Configuración
│   ├── pipeline/
│   │   ├── tracker.py          ← Módulo de tracking (detección + bbox)
│   │   └── run_analysis.py     ← Pipeline completo (tracking + clasificación)
│   ├── worker/
│   │   └── worker.py           ← Worker asíncrono (polling de jobs)
│   ├── requirements.txt
│   └── Dockerfile
└── frontend/
    ├── src/
    │   ├── App.jsx
    │   └── main.jsx
    ├── index.html
    ├── package.json
    ├── vite.config.js
    └── Dockerfile
```
