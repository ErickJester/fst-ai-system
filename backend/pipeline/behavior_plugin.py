"""
Punto de conexión para un clasificador de conducta externo (plugin).

Este sistema NO incluye clasificador. Solo define el contrato que un
clasificador debe cumplir para que el worker lo ejecute después del tracking
y sus resultados se guarden y se muestren sin tocar el resto del código.

Activación
----------
Variable de entorno FST_BEHAVIOR_PLUGIN = "modulo.importable:fabrica"
  - Sin la variable (o vacía) → no hay clasificación; el sistema solo hace tracking.
  - Con la variable pero el import falla → error explícito (el job falla),
    nunca un fallback silencioso.

`fabrica()` se llama sin argumentos y devuelve un objeto que cumple
`BehaviorClassifier`.

Contrato
--------
    class MiClasificador:
        name: str        # p. ej. "fst-etiquetador"
        version: str     # p. ej. hash de commit o versión del modelo

        def classify(self, video_path: str, tracking: dict) -> list[BehaviorSegment]:
            ...

`tracking` es el dict que devuelve pipeline.tracker.track_video (el mismo
contenido que *_tracking.json): fps, frame_size, layout, rois[{rat_idx,x,y,w,h,
waterline_y}], detections[...], stats. El clasificador puede usarlo o ignorarlo
y trabajar directo sobre el video.

Identidad de los animales: rat_idx es 0-based y sigue el orden de
`tracking["rois"]` (izquierda → derecha en layouts 1xN; fila por fila en 2x2).
Si el clasificador numera distinto (p. ej. 1-based), el adaptador convierte.

Las etiquetas (`label`) son texto libre definido por el clasificador; el
sistema las agrega y las muestra tal cual, sin conocerlas de antemano.
"""

from __future__ import annotations

import importlib
import os
from dataclasses import dataclass
from typing import List, Optional, Protocol, runtime_checkable

ENV_VAR = "FST_BEHAVIOR_PLUGIN"


@dataclass(frozen=True)
class BehaviorSegment:
    """Intervalo [start_s, end_s) en que un animal mostró una conducta."""
    rat_idx: int
    start_s: float
    end_s: float
    label: str
    confidence: Optional[float] = None


@runtime_checkable
class BehaviorClassifier(Protocol):
    name: str
    version: str

    def classify(self, video_path: str, tracking: dict) -> List[BehaviorSegment]:
        ...


def load_classifier() -> Optional[BehaviorClassifier]:
    """Carga el plugin indicado en FST_BEHAVIOR_PLUGIN, o None si no hay."""
    spec = (os.getenv(ENV_VAR) or "").strip()
    if not spec:
        return None
    module_name, sep, factory_name = spec.partition(":")
    if not sep or not module_name or not factory_name:
        raise ValueError(f"{ENV_VAR} debe tener la forma 'modulo:fabrica', no {spec!r}")

    factory = getattr(importlib.import_module(module_name), factory_name)
    clf = factory()
    if not isinstance(clf, BehaviorClassifier):
        raise TypeError(
            f"{spec} no cumple BehaviorClassifier (requiere name, version y classify())"
        )
    return clf


def validate_segments(segments: List[BehaviorSegment], n_rats: int) -> None:
    """Rechaza salidas del plugin que no respetan el contrato."""
    for s in segments:
        if not isinstance(s, BehaviorSegment):
            raise TypeError(f"Se esperaba BehaviorSegment, llegó {type(s).__name__}")
        if not 0 <= s.rat_idx < n_rats:
            raise ValueError(f"rat_idx {s.rat_idx} fuera de rango (hay {n_rats} ROIs)")
        if s.end_s <= s.start_s or s.start_s < 0:
            raise ValueError(f"Intervalo inválido [{s.start_s}, {s.end_s}) para rata {s.rat_idx}")
        if not s.label:
            raise ValueError(f"Segmento sin etiqueta para rata {s.rat_idx}")
        if s.confidence is not None and not 0.0 <= s.confidence <= 1.0:
            raise ValueError(f"Confianza fuera de [0, 1]: {s.confidence}")
