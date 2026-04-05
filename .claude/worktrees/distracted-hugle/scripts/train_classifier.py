"""
Entrenamiento del clasificador CNN de conducta FST.

Uso básico:
    python scripts/train_classifier.py --arch resnet18 --epochs 30

Requiere dataset con crops etiquetados:
    dataset/behavior/
      train/
        swim/       *.jpg
        immobile/   *.jpg
        escape/     *.jpg
      val/
        swim/       *.jpg
        immobile/   *.jpg
        escape/     *.jpg

Para generar ese dataset a partir de videos etiquetados por la heurística:
    python scripts/train_classifier.py --extract-crops \\
        --video ruta/video.mp4 \\
        --analysis-json ruta/analisis.json

Referencia: backend/pipeline/classifier.py
"""

import argparse
import json
import os
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

# ── validación de dependencias ────────────────────────────────────────────────
try:
    import torch
    import torch.nn as nn
    from torch.utils.data import DataLoader, WeightedRandomSampler
    import torchvision.models as tv_models
    import torchvision.transforms as T
    from torchvision.datasets import ImageFolder
except ImportError:
    print("ERROR: PyTorch/torchvision no instalados.")
    print("  pip install torch torchvision")
    sys.exit(1)

import cv2
import numpy as np

CLASSES     = ["swim", "immobile", "escape"]
NUM_CLASSES = len(CLASSES)
INPUT_SIZE  = 224
IMAGENET_MEAN = [0.485, 0.456, 0.406]
IMAGENET_STD  = [0.229, 0.224, 0.225]


# ── modelo ────────────────────────────────────────────────────────────────────

def build_model(arch: str, freeze_backbone: bool) -> nn.Module:
    if arch == "resnet18":
        model = tv_models.resnet18(weights="IMAGENET1K_V1")
        in_features = 512
    elif arch == "resnet50":
        model = tv_models.resnet50(weights="IMAGENET1K_V2")
        in_features = 2048
    else:
        raise ValueError(f"Arquitectura no soportada: {arch}")

    model.fc = nn.Linear(in_features, NUM_CLASSES)

    if freeze_backbone:
        for name, param in model.named_parameters():
            if not name.startswith("fc"):
                param.requires_grad = False

    return model


# ── transforms ────────────────────────────────────────────────────────────────

def get_transforms():
    train_tf = T.Compose([
        T.Resize((INPUT_SIZE, INPUT_SIZE)),
        T.RandomHorizontalFlip(),
        T.RandomRotation(10),
        T.ColorJitter(brightness=0.3, contrast=0.3, saturation=0.2),
        T.ToTensor(),
        T.Normalize(mean=IMAGENET_MEAN, std=IMAGENET_STD),
    ])
    val_tf = T.Compose([
        T.Resize((INPUT_SIZE, INPUT_SIZE)),
        T.CenterCrop(INPUT_SIZE),
        T.ToTensor(),
        T.Normalize(mean=IMAGENET_MEAN, std=IMAGENET_STD),
    ])
    return train_tf, val_tf


# ── sampler balanceado ────────────────────────────────────────────────────────

def make_weighted_sampler(dataset: ImageFolder) -> WeightedRandomSampler:
    class_counts = Counter(dataset.targets)
    weights = [1.0 / class_counts[t] for t in dataset.targets]
    return WeightedRandomSampler(weights, num_samples=len(weights), replacement=True)


# ── entrenamiento ─────────────────────────────────────────────────────────────

def train(args):
    data_dir = Path(args.data_dir)
    train_dir = data_dir / "train"
    val_dir   = data_dir / "val"

    if not train_dir.exists():
        print(f"ERROR: No se encontró {train_dir}")
        print("  Crea las carpetas train/swim, train/immobile, train/escape con crops.")
        sys.exit(1)

    train_tf, val_tf = get_transforms()

    train_ds = ImageFolder(str(train_dir), transform=train_tf)
    val_ds   = ImageFolder(str(val_dir),   transform=val_tf) if val_dir.exists() else None

    print(f"Clases detectadas: {train_ds.classes}")
    print(f"Train: {len(train_ds)} imgs  |  Val: {len(val_ds) if val_ds else 'N/A'} imgs")

    # verificar orden de clases (debe coincidir con CLASSES)
    expected = sorted(CLASSES)
    if train_ds.classes != expected:
        print(f"ADVERTENCIA: orden de clases {train_ds.classes} != esperado {expected}")
        print("  El modelo usará el orden del directorio — asegúrate que coincide con classifier.py")

    sampler    = make_weighted_sampler(train_ds)
    train_dl   = DataLoader(train_ds, batch_size=args.batch_size, sampler=sampler,
                            num_workers=0, pin_memory=True)
    val_dl     = DataLoader(val_ds, batch_size=args.batch_size, shuffle=False,
                            num_workers=0) if val_ds else None

    # dispositivo
    if args.device:
        device = torch.device(args.device)
    elif torch.cuda.is_available():
        device = torch.device("cuda")
    elif hasattr(torch.backends, "mps") and torch.backends.mps.is_available():
        device = torch.device("mps")
    else:
        device = torch.device("cpu")
    print(f"Dispositivo: {device}")

    model = build_model(args.arch, args.freeze_backbone).to(device)

    # pesos de clase inversos para pérdida (combate desbalance)
    class_counts = Counter(train_ds.targets)
    total = len(train_ds.targets)
    class_weights = torch.tensor(
        [total / (NUM_CLASSES * class_counts.get(i, 1)) for i in range(NUM_CLASSES)],
        dtype=torch.float32,
    ).to(device)

    criterion = nn.CrossEntropyLoss(weight=class_weights)
    optimizer = torch.optim.Adam(
        filter(lambda p: p.requires_grad, model.parameters()),
        lr=args.lr
    )
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=args.epochs)

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    best_val_acc = 0.0
    best_epoch   = 0

    for epoch in range(1, args.epochs + 1):
        # entrenamiento
        model.train()
        train_loss = 0.0
        train_correct = 0
        for imgs, labels in train_dl:
            imgs, labels = imgs.to(device), labels.to(device)
            optimizer.zero_grad()
            out  = model(imgs)
            loss = criterion(out, labels)
            loss.backward()
            optimizer.step()
            train_loss    += loss.item() * imgs.size(0)
            train_correct += (out.argmax(1) == labels).sum().item()

        train_loss /= len(train_ds)
        train_acc   = train_correct / len(train_ds) * 100

        # validación
        val_acc = float("nan")
        if val_dl is not None:
            model.eval()
            val_correct = 0
            with torch.no_grad():
                for imgs, labels in val_dl:
                    imgs, labels = imgs.to(device), labels.to(device)
                    out = model(imgs)
                    val_correct += (out.argmax(1) == labels).sum().item()
            val_acc = val_correct / len(val_ds) * 100

            if val_acc > best_val_acc:
                best_val_acc = val_acc
                best_epoch   = epoch
                torch.save(model.state_dict(), str(output_path))
                print(f"  ✓ Nuevo mejor modelo guardado ({val_acc:.1f}%)")

        scheduler.step()
        print(f"Época {epoch:3d}/{args.epochs}  "
              f"loss={train_loss:.4f}  train_acc={train_acc:.1f}%  "
              f"val_acc={val_acc:.1f}%")

    if val_dl is None:
        # sin validación: guardar el modelo final
        torch.save(model.state_dict(), str(output_path))
        print(f"\nModelo guardado en: {output_path}")
    else:
        print(f"\nMejor época: {best_epoch}  val_acc={best_val_acc:.1f}%")
        print(f"Modelo guardado en: {output_path}")

    # matriz de confusión final (en val si existe, sino en train)
    _print_confusion(model, val_dl or train_dl, device, train_ds.classes)


def _print_confusion(model, dataloader, device, class_names):
    model.eval()
    conf = [[0] * NUM_CLASSES for _ in range(NUM_CLASSES)]
    with torch.no_grad():
        for imgs, labels in dataloader:
            imgs, labels = imgs.to(device), labels.to(device)
            preds = model(imgs).argmax(1)
            for t, p in zip(labels.cpu(), preds.cpu()):
                conf[t.item()][p.item()] += 1

    print("\nMatriz de confusión (filas=real, cols=predicho):")
    header = "           " + "  ".join(f"{c:>10}" for c in class_names)
    print(header)
    for i, row in enumerate(conf):
        total_row = sum(row)
        acc = row[i] / total_row * 100 if total_row > 0 else 0
        row_str = "  ".join(f"{v:>10}" for v in row)
        print(f"  {class_names[i]:>10}  {row_str}  ({acc:.0f}%)")


# ── extracción de crops ───────────────────────────────────────────────────────

def extract_crops(args):
    """
    Extrae crops del rat desde un video usando el JSON de análisis heurístico.

    Para cada ventana en behavior_windows, extrae el crop del frame central
    y lo guarda en dataset/behavior/train/{label}/ o val/{label}/.

    Esto permite entrenar el CNN usando la heurística como bootstrapping.
    Revisa manualmente los crops antes de entrenar.
    """
    video_path = Path(args.video)
    json_path  = Path(args.analysis_json)
    out_dir    = Path(args.data_dir)

    if not video_path.exists():
        print(f"ERROR: Video no encontrado: {video_path}")
        sys.exit(1)
    if not json_path.exists():
        print(f"ERROR: JSON no encontrado: {json_path}")
        sys.exit(1)

    with open(json_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    fps      = data.get("fps", 30.0)
    windows  = data.get("behavior_windows", [])
    dets_all = data.get("detections", [])

    # índice de detecciones por frame
    dets_by_frame: dict = {}
    for d in dets_all:
        fn = d.get("frame_num", -1)
        dets_by_frame.setdefault(fn, []).append(d)

    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        print(f"ERROR: No se puede abrir el video: {video_path}")
        sys.exit(1)

    # usar 80% train / 20% val
    n_windows = len(windows)
    n_val     = max(1, int(n_windows * 0.2))
    val_idxs  = set(range(n_windows - n_val, n_windows))

    saved = Counter()
    for idx, win in enumerate(windows):
        split    = "val" if idx in val_idxs else "train"
        t_mid    = (win["t_start"] + win["t_end"]) / 2.0
        frame_no = int(t_mid * fps)

        cap.set(cv2.CAP_PROP_POS_FRAMES, frame_no)
        ok, frame = cap.read()
        if not ok:
            continue

        fh, fw = frame.shape[:2]
        rats   = win.get("rats", {})
        for rat_idx_str, rat_data in rats.items():
            label = rat_data.get("behavior", "unknown")
            if label not in CLASSES:
                continue

            # buscar detección más cercana al frame central
            det = _find_det(dets_by_frame, frame_no, int(rat_idx_str))
            if det is None:
                continue

            # expandir bbox 10%
            x, y, w, h = det["x"], det["y"], det["w"], det["h"]
            exp_x = int(w * 0.10)
            exp_y = int(h * 0.10)
            x1 = max(0, x - exp_x)
            y1 = max(0, y - exp_y)
            x2 = min(fw, x + w + exp_x)
            y2 = min(fh, y + h + exp_y)

            if x2 - x1 < 8 or y2 - y1 < 8:
                continue

            crop = frame[y1:y2, x1:x2]
            dest = out_dir / split / label
            dest.mkdir(parents=True, exist_ok=True)
            stem = video_path.stem
            fname = dest / f"{stem}_w{idx:04d}_r{rat_idx_str}.jpg"
            cv2.imwrite(str(fname), crop)
            saved[label] += 1

    cap.release()
    print("Crops extraídos:")
    for label in CLASSES:
        print(f"  {label}: {saved[label]}")
    print(f"  Total: {sum(saved.values())}")
    print(f"  Guardados en: {out_dir}")
    print("\nREVISA los crops manualmente antes de entrenar.")


def _find_det(dets_by_frame: dict, target_frame: int, rat_idx: int):
    """Busca la detección de la rata en frames cercanos (±5)."""
    for offset in range(0, 6):
        for sign in (0, 1, -1):
            fn = target_frame + sign * offset
            for d in dets_by_frame.get(fn, []):
                if d.get("roi_idx", d.get("rat_idx", -1)) == rat_idx:
                    return d
    return None


# ── CLI ───────────────────────────────────────────────────────────────────────

def parse_args():
    parser = argparse.ArgumentParser(
        description="Entrenamiento del clasificador CNN de conducta FST",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    subparsers = parser.add_subparsers(dest="command")

    # ── sub-comando: train ──
    p_train = subparsers.add_parser("train", help="Entrenar clasificador")
    p_train.add_argument("--arch",            default="resnet18", choices=["resnet18", "resnet50"])
    p_train.add_argument("--data-dir",        default="dataset/behavior")
    p_train.add_argument("--epochs",          type=int,   default=30)
    p_train.add_argument("--batch-size",      type=int,   default=32)
    p_train.add_argument("--lr",              type=float, default=1e-4)
    p_train.add_argument("--freeze-backbone", action="store_true",
                         help="Congelar backbone, solo entrenar la cabeza fc")
    p_train.add_argument("--output",          default=None,
                         help="Ruta de salida del modelo (default: weights/fst_{arch}.pt)")
    p_train.add_argument("--device",          default="",
                         help="cuda | cpu | mps | '' (autodetectar)")

    # ── sub-comando: extract-crops ──
    p_ext = subparsers.add_parser("extract-crops",
                                  help="Extraer crops de video usando JSON de análisis")
    p_ext.add_argument("--video",         required=True, help="Ruta al video .mp4")
    p_ext.add_argument("--analysis-json", required=True, help="JSON generado por run_analysis")
    p_ext.add_argument("--data-dir",      default="dataset/behavior",
                       help="Directorio raíz de salida (se crean train/ y val/)")

    # compatibilidad: si se llama sin sub-comando usa train por defecto
    args, _ = parser.parse_known_args()
    if args.command is None:
        args = parser.parse_args(["train"] + sys.argv[1:])

    return args


def main():
    args = parse_args()

    if args.command == "extract-crops":
        extract_crops(args)
        return

    # train
    if args.output is None:
        args.output = f"weights/fst_{args.arch}.pt"

    print(f"Arquitectura : {args.arch}")
    print(f"Dataset      : {args.data_dir}")
    print(f"Épocas       : {args.epochs}")
    print(f"Batch size   : {args.batch_size}")
    print(f"LR           : {args.lr}")
    print(f"Freeze BB    : {args.freeze_backbone}")
    print(f"Salida       : {args.output}")
    print("─" * 50)
    train(args)


if __name__ == "__main__":
    main()
