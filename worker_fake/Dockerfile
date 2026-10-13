FROM python:3.12-slim
# DejaVu: la fuente que trae Pillow no tiene acentos; el PDF de diagnóstico y la imagen de pausa los necesitan
RUN apt-get update \
 && apt-get install -y --no-install-recommends fonts-dejavu-core \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY *.py ./
# -u: sin búfer, para que el log salga al momento en docker compose logs
CMD ["python", "-u", "worker_fake.py"]
