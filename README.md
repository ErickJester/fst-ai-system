# Sistema FST

Aplicación web para gestionar experimentos del test de nado forzado (FST):
usuarios, experimentos, subida de videos por día, cola de análisis y
resultados por animal.

| Servicio | Tecnología | Puerto |
|---|---|---|
| `web` | React (Vite) servido por nginx | 8088 (el único que se publica) |
| `api` | Spring Boot (`backend-spring/`) | solo dentro de Docker |
| `db` | PostgreSQL 16 | solo dentro de Docker |
| `worker` | Python, analiza los videos con el modelo (`worker/`) | — |

El `docker-compose.yml` de esta carpeta es el compose **definitivo** del sistema. Se usa con
`portable\iniciar.bat` (doble clic; ver [portable/LEEME.md](portable/LEEME.md)), que llama a
`docker compose -f ..\docker-compose.yml --env-file .env up -d --build` desde la carpeta `portable` para
que tome `portable\.env`. El compose de desarrollo es otro: `backend-spring/docker-compose.yml`.

El análisis de video lo hace el `worker` (`worker/`) con el pipeline de `pipeline/`; el esquema de la base de
datos está en `backend-spring/db/init/001_esquema.sql` y su diagrama en `db_schema.puml`.

Manual completo: [MANUAL.md](MANUAL.md).
