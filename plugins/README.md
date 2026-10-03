# Plugins de conducta

El sistema hace **tracking** (YOLO + ROIs). La clasificación de conducta vive
en otro repositorio y se conecta aquí como plugin, sin modificar el backend,
el worker, la base de datos ni el frontend.

```
video ──► tracker (este repo) ──► tracking dict ──► plugin.classify() ──► [BehaviorSegment]
                                                         ▲ otro repo
```

El contrato está en [`backend/pipeline/behavior_plugin.py`](../backend/pipeline/behavior_plugin.py).

## Cómo se conecta

1. Pon el plugin en una carpeta dentro de `plugins/` (clon del repo o copia):

   ```
   plugins/
     mi_plugin/                 ← carpeta del plugin (ignorada por git)
       requirements.txt         ← opcional: el worker lo instala al arrancar
       ...
     mi_plugin_adapter.py       ← módulo importable con la fábrica
   ```

   `/plugins` se monta en el worker y está en `PYTHONPATH`, así que cualquier
   módulo o paquete en esta carpeta se puede importar.

2. Indica la fábrica en `.env` (junto a `docker-compose.yml`):

   ```
   FST_BEHAVIOR_PLUGIN=mi_plugin_adapter:create
   ```

3. `docker-compose up -d --force-recreate worker`. El log del worker dice qué
   plugin cargó, o `sin plugin de conducta: solo tracking`.

Si la variable apunta a algo que no importa o no cumple el contrato, **el
worker no arranca**: es intencional, para no analizar sin clasificador sin
darse cuenta.

## Contrato

```python
from pipeline.behavior_plugin import BehaviorSegment

class MiClasificador:
    name = "mi-clasificador"
    version = "abc1234"          # commit o versión del modelo; queda en analysis_configs

    def classify(self, video_path: str, tracking: dict) -> list[BehaviorSegment]:
        ...
        return [
            BehaviorSegment(rat_idx=0, start_s=0.0, end_s=1.0, label="nado", confidence=0.93),
            ...
        ]

def create():
    return MiClasificador()
```

| Campo | Regla |
|---|---|
| `rat_idx` | 0-based, en el orden de `tracking["rois"]` (izquierda → derecha en 1xN) |
| `start_s`, `end_s` | segundos desde el inicio del video, `0 <= start_s < end_s` |
| `label` | texto libre; el sistema no conoce las clases y las muestra tal cual |
| `confidence` | `None` o un valor en `[0, 1]` |

`tracking` contiene `fps`, `frame_size`, `layout`, `rois` (`rat_idx, x, y, w, h,
waterline_y`), `detections` (bbox por frame y rata) y `stats`. El plugin puede
usarlo o trabajar directo sobre `video_path`.

Los segmentos se guardan en `behavior_segments`. `GET /experiments/<id>/behavior`
devuelve los segundos por etiqueta (total y por minuto), y la página de resultados
muestra la pestaña **Conducta** solo cuando existen.

## Nota para `fst-etiquetador`

Su salida por segundo (`*_segundos.csv`: `especimen, segundo, inicio_s, clase,
confianza, ...`) encaja así:

| `_segundos.csv` | `BehaviorSegment` |
|---|---|
| `especimen` (1-based) | `rat_idx = especimen - 1` |
| `inicio_s` | `start_s` |
| `inicio_s + 1` | `end_s` |
| `clase` | `label` |
| `confianza` | `confidence` |

Antes de confiar en el mapeo, verifica que `especimen` y `rat_idx` cuentan los
cilindros en el mismo orden: ese repo detecta su propia geometría y no usa las
ROIs del tracker.
