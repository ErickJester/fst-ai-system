# Worker REAL: analiza los videos con el modelo de fst_auto (pipeline/ + modelos/fst_cnn_seg.pt).
# El contexto del build es la RAÍZ del repositorio (ver backend-spring/docker-compose.yml), porque copia
# pipeline/ y worker/ (el código del worker: la cola, las pausas, los avisos y el análisis). El modelo no va en
# la imagen: se monta en /data/modelos.
FROM python:3.12-slim
# ffmpeg: ffprobe mide el video y ffmpeg hace la copia web.mp4. DejaVu: la fuente de Pillow no tiene
# acentos y el PDF de diagnóstico los necesita.
RUN apt-get update \
 && apt-get install -y --no-install-recommends ffmpeg fonts-dejavu-core \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
# torch y torchvision de la rueda de CPU (unos cientos de MB menos que la de CUDA; aquí no hay GPU)
RUN pip install --no-cache-dir torch torchvision --index-url https://download.pytorch.org/whl/cpu
COPY worker/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
# Cliente de PostgreSQL para el respaldo diario de la base (D-18). pg_dump tiene que ser de la versión del servidor (16)
# o mayor; Debian 13 trae el 17, y el build falla si algún día la imagen base trajera uno menor. Va aquí, después de las
# bibliotecas de Python, para que cambiarlo no reinstale torch.
RUN apt-get update \
 && apt-get install -y --no-install-recommends postgresql-client \
 && rm -rf /var/lib/apt/lists/* \
 && test "$(pg_dump --version | sed 's/[^0-9]*\([0-9]*\).*/\1/')" -ge 16
# El código va al final para que cambiarlo no reinstale las bibliotecas
COPY pipeline ./pipeline
# Sin las pruebas (test_*.py): no hacen falta en la imagen
COPY worker/cola.py worker/comun.py worker/diagnostico.py worker/progreso.py worker/tareas_programadas.py \
     worker/tiempos.py worker/vigilancia.py worker/worker.py ./
# -u: sin búfer, para que el log salga al momento en docker compose logs
CMD ["python", "-u", "worker.py"]
