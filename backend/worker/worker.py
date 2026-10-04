"""
Worker de análisis — PENDIENTE.

Aquí irá el pipeline de análisis de video. Por ahora el worker solo
arranca y lo avisa; no toma jobs, así que los videos subidos quedan en
cola (status QUEUED, "En espera" en el frontend).

Cuando se implemente, el pipeline debe, por cada job QUEUED:
  1. Marcarlo RUNNING (stage/progress_pct alimentan la barra de progreso).
  2. Analizar video.path con el layout del experimento.
  3. Guardar un AnalysisConfig (pipeline_name, pipeline_version) y ligarlo al job.
  4. Por animal: Subject (rat_idx), ROI opcional, Animal y sus BehaviorSegment
     (start_s, end_s, label, confidence).
  5. Opcional: job.output_video_path con un video anotado.
  6. Marcarlo DONE (o FAILED con error) y crear la Notification.

El API (GET /experiments/<id>/results) y la página de resultados ya leen
esas tablas.
"""

import time

MESSAGE_EVERY_SECONDS = 300


def main():
    while True:
        print(
            "[worker] Aquí irá el pipeline de análisis. "
            "Por ahora no se procesan videos: quedan en cola.",
            flush=True,
        )
        time.sleep(MESSAGE_EVERY_SECONDS)


if __name__ == "__main__":
    main()
