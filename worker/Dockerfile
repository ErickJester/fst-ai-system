# Worker REAL: analiza los videos con el modelo de fst_auto (pipeline/ + modelos/fst_cnn_seg.pt).
# El contexto del build es la RAÍZ del repositorio (ver docker-compose.yml y backend-spring/docker-compose.yml), porque copia
# pipeline/ y worker/ (el código del worker: la cola, las pausas, los avisos y el análisis). El modelo no va en
# la imagen: se monta en /data/modelos.
# Imagen base con versión EXACTA (Debian 13 «trixie»), no un tag flotante.
FROM python:3.12.16-slim-trixie
# ffmpeg: ffprobe mide el video y ffmpeg hace la copia web.mp4. DejaVu: la fuente de Pillow no tiene
# acentos y el PDF de diagnóstico los necesita.
RUN apt-get update \
 && apt-get install -y --no-install-recommends ffmpeg fonts-dejavu-core \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
# torch y torchvision de la rueda de CPU (unos cientos de MB menos que la de CUDA; aquí no hay GPU), con versión exacta.
RUN pip install --no-cache-dir torch==2.14.1+cpu torchvision==0.29.1+cpu --index-url https://download.pytorch.org/whl/cpu
COPY worker/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
# Cliente de PostgreSQL para el respaldo diario de la base (D-18). pg_dump tiene que ser de la versión del servidor (16)
# o mayor; Debian 13 trae el 17, y el build falla si algún día la imagen base trajera uno menor. Va aquí, después de las
# bibliotecas de Python, para que cambiarlo no reinstale torch.
RUN apt-get update \
 && apt-get install -y --no-install-recommends postgresql-client \
 && rm -rf /var/lib/apt/lists/* \
 && test "$(pg_dump --version | sed 's/[^0-9]*\([0-9]*\).*/\1/')" -ge 16
# Usuario sin privilegios: el worker no corre como root. Con carpeta personal para los cachés de las bibliotecas.
RUN useradd --system --uid 10001 --create-home --shell /usr/sbin/nologin app
# El código va al final para que cambiarlo no reinstale las bibliotecas
COPY pipeline ./pipeline
# Sin las pruebas (test_*.py): no hacen falta en la imagen. probar_correo.py lo usa portable\probar_correo.bat.
COPY worker/cola.py worker/comun.py worker/diagnostico.py worker/progreso.py worker/tareas_programadas.py \
     worker/tiempos.py worker/vigilancia.py worker/worker.py worker/probar_correo.py ./
# El contenedor arranca como root SOLO para arreglar los permisos de los volúmenes y se baja a «app» antes de lanzar el
# worker. Los volúmenes de instalaciones anteriores (cuando el worker corría como root) tienen archivos de root: el find solo
# toca los que no son de «app», así que en un arranque normal no cuesta nada, y así el worker puede seguir escribiendo en
# ellos (y en la carpeta de respaldos, que es del equipo). Un error del find (carpeta que no existe) se ignora.
# -u: sin búfer, para que el log salga al momento en docker compose logs
CMD ["sh", "-c", "find /data/archivos /data/respaldos ! -user app -exec chown app:app {} + 2>/dev/null; exec setpriv --reuid=app --regid=app --init-groups python -u worker.py"]
