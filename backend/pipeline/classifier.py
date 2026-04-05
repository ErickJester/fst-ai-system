"""
Clasificador CNN de conducta FST.

Arquitectura dual:
  Primario  — ResNet-18  (rápido, liviano)
  Respaldo  — ResNet-50  (más preciso, activa cuando confianza < umbral)

Clases: swim (0) | immobile (1) | escape (2)

Uso:
    clf = FSTClassifier()
    if clf.is_available:
        label, conf, source = clf.classify_frame(frame, det)

Si torch/torchvision no están instalados, o si los pesos no existen,
`is_available` es False y el módulo no lanza excepción al importarse.
"""

from __future__ import annotations

import os
from dataclasses import dataclass
from typing import Optional, Tuple

import cv2
import numpy as np

# ── importación opcional de torch ─────────────────────────────────────────────
_TORCH_AVAILABLE = False
try:
    import torch
    import torch.nn as nn
    import torchvision.models as tv_models
    _TORCH_AVAILABLE = True
except ImportError:
    pass

# ── constantes ────────────────────────────────────────────────────────────────
CLASSES       = ["swim", "immobile", "escape"]
NUM_CLASSES   = len(CLASSES)
INPUT_SIZE    = 224          # estándar ResNet
IMAGENET_MEAN = [0.485, 0.456, 0.406]
IMAGENET_STD  = [0.229, 0.224, 0.225]

PRIMARY_WEIGHTS  = os.path.join("weights", "fst_resnet18.pt")
FALLBACK_WEIGHTS = os.path.join("weights", "fst_resnet50.pt")

PRIMARY_CONF_THR  = 0.65    # confianza mínima de ResNet-18; si < → activa ResNet-50
FALLBACK_CONF_THR = 0.40    # confianza mínima de ResNet-50; si < → usa igual el mejor resultado


@dataclass
class ClassifierResult:
    label:      str
    confidence: float
    source:     str   # "resnet18" | "resnet50" | "heuristic" | "no_det"


class FSTClassifier:
    """
    Clasificador de conducta FST con ResNet-18 (primario) y ResNet-50 (respaldo).

    Parameters
    ----------
    primary_weights   : ruta al state_dict de ResNet-18 (fst_resnet18.pt)
    fallback_weights  : ruta al state_dict de ResNet-50 (fst_resnet50.pt)
    primary_conf_thr  : umbral de confianza para activar el respaldo
    fallback_conf_thr : umbral mínimo de confianza del respaldo (informativo)
    device            : "" → autodetectar; "cpu" | "cuda" | "mps"
    input_size        : tamaño del crop redimensionado (cuadrado)
    bbox_expand       : fracción de expansión del bbox alrededor de la rata
    """

    def __init__(
        self,
        primary_weights:   str   = PRIMARY_WEIGHTS,
        fallback_weights:  str   = FALLBACK_WEIGHTS,
        primary_conf_thr:  float = PRIMARY_CONF_THR,
        fallback_conf_thr: float = FALLBACK_CONF_THR,
        device:            str   = "",
        input_size:        int   = INPUT_SIZE,
        bbox_expand:       float = 0.10,
    ) -> None:
        self.primary_weights   = primary_weights
        self.fallback_weights  = fallback_weights
        self.primary_conf_thr  = primary_conf_thr
        self.fallback_conf_thr = fallback_conf_thr
        self.input_size        = input_size
        self.bbox_expand       = bbox_expand
        self.is_available      = False

        if not _TORCH_AVAILABLE:
            return

        # dispositivo
        if device:
            self.device = torch.device(device)
        elif torch.cuda.is_available():
            self.device = torch.device("cuda")
        elif hasattr(torch.backends, "mps") and torch.backends.mps.is_available():
            self.device = torch.device("mps")
        else:
            self.device = torch.device("cpu")

        self._primary  = self._load_model(primary_weights,  "resnet18")
        self._fallback = self._load_model(fallback_weights, "resnet50")

        if self._primary is None and self._fallback is None:
            return   # ningún peso disponible

        self.is_available = True

    # ── construcción y carga ──────────────────────────────────────────────────

    def _build_resnet(self, arch: str) -> "nn.Module":
        """Construye un ResNet con cabeza de 3 clases."""
        if arch == "resnet18":
            model = tv_models.resnet18(weights=None)
            in_features = 512
        elif arch == "resnet50":
            model = tv_models.resnet50(weights=None)
            in_features = 2048
        else:
            raise ValueError(f"Arquitectura no soportada: {arch}")
        model.fc = nn.Linear(in_features, NUM_CLASSES)
        return model

    def _load_model(self, weights_path: str, arch: str) -> "Optional[nn.Module]":
        """Carga pesos desde disco. Retorna None si el archivo no existe."""
        if not os.path.isfile(weights_path):
            return None
        try:
            model = self._build_resnet(arch)
            state = torch.load(weights_path, map_location=self.device)
            # acepta tanto state_dict directo como checkpoint con clave "model"
            if isinstance(state, dict) and "model" in state:
                state = state["model"]
            model.load_state_dict(state)
            model.to(self.device)
            model.eval()
            return model
        except Exception as exc:
            print(f"[FSTClassifier] Error cargando {weights_path}: {exc}")
            return None

    # ── preprocesamiento ──────────────────────────────────────────────────────

    def preprocess(self, crop_bgr: np.ndarray) -> "torch.Tensor":
        """
        BGR numpy → tensor normalizado listo para inferencia.

        Pasos:
          1. BGR → RGB
          2. padding a cuadrado (zero-pad centrado)
          3. resize a (input_size, input_size)
          4. float32, escala [0,1]
          5. normalización ImageNet
          6. batch dim + move to device
        """
        rgb = cv2.cvtColor(crop_bgr, cv2.COLOR_BGR2RGB)

        h, w = rgb.shape[:2]
        side = max(h, w)
        pad_top    = (side - h) // 2
        pad_bottom = side - h - pad_top
        pad_left   = (side - w) // 2
        pad_right  = side - w - pad_left
        sq = cv2.copyMakeBorder(
            rgb, pad_top, pad_bottom, pad_left, pad_right,
            cv2.BORDER_CONSTANT, value=(0, 0, 0)
        )

        resized = cv2.resize(sq, (self.input_size, self.input_size),
                             interpolation=cv2.INTER_LINEAR)

        arr = resized.astype(np.float32) / 255.0
        mean = np.array(IMAGENET_MEAN, dtype=np.float32)
        std  = np.array(IMAGENET_STD,  dtype=np.float32)
        arr  = (arr - mean) / std

        # HWC → CHW → batch
        tensor = torch.from_numpy(arr.transpose(2, 0, 1)).unsqueeze(0)
        return tensor.to(self.device)

    # ── inferencia ────────────────────────────────────────────────────────────

    def _infer(self, model: "nn.Module", tensor: "torch.Tensor") -> Tuple[str, float]:
        """Ejecuta un modelo y retorna (label, confidence)."""
        with torch.no_grad():
            logits = model(tensor)
            probs  = torch.softmax(logits, dim=1)[0]
        idx        = int(probs.argmax().item())
        confidence = float(probs[idx].item())
        label      = CLASSES[idx]
        return label, confidence

    def classify_crop(self, crop_bgr: np.ndarray) -> ClassifierResult:
        """
        Clasifica un crop BGR del rat.

        Cascada:
          1. ResNet-18 → si confianza ≥ primary_conf_thr → retorna
          2. ResNet-50 (si disponible) → retorna
          3. Si solo hay ResNet-18, retorna su resultado aunque la confianza sea baja
        """
        if not self.is_available or crop_bgr is None or crop_bgr.size == 0:
            return ClassifierResult("unknown", 0.0, "no_det")

        tensor = self.preprocess(crop_bgr)

        # ResNet-18
        if self._primary is not None:
            label18, conf18 = self._infer(self._primary, tensor)
            if conf18 >= self.primary_conf_thr or self._fallback is None:
                return ClassifierResult(label18, conf18, "resnet18")
        else:
            label18, conf18 = "unknown", 0.0

        # ResNet-50 (respaldo)
        label50, conf50 = self._infer(self._fallback, tensor)
        return ClassifierResult(label50, conf50, "resnet50")

    def classify_frame(self, frame: np.ndarray, det) -> ClassifierResult:
        """
        Extrae el crop del frame usando la detección y clasifica.

        `det` debe tener atributos .x, .y, .w, .h (coordenadas absolutas del bbox).
        Retorna ClassifierResult con source="no_det" si det es None o el crop es inválido.
        """
        if not self.is_available or det is None:
            return ClassifierResult("unknown", 0.0, "no_det")

        fh, fw = frame.shape[:2]
        exp_x = int(det.w * self.bbox_expand)
        exp_y = int(det.h * self.bbox_expand)

        x1 = max(0, det.x - exp_x)
        y1 = max(0, det.y - exp_y)
        x2 = min(fw, det.x + det.w + exp_x)
        y2 = min(fh, det.y + det.h + exp_y)

        if x2 - x1 < 8 or y2 - y1 < 8:
            return ClassifierResult("unknown", 0.0, "no_det")

        crop = frame[y1:y2, x1:x2]
        return self.classify_crop(crop)
