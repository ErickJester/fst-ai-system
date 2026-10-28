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

El `docker-compose.yml` de esta carpeta es el compose **definitivo** del sistema (producción: una PC del laboratorio con
Windows y Docker Desktop, a la que entran las demás PCs de la red). Corre con **imágenes ya construidas** y con versión
(`fst-api`, `fst-worker`, `fst-web`): `portable\empaquetar.ps1` las construye y las guarda en un `.tar` junto a un zip chico, y
en la PC de destino `portable\iniciar.bat` (doble clic; ver [portable/LEEME.md](portable/LEEME.md)) las carga con
`docker load` y prende todo con `docker compose ... up -d --no-build`, sin internet ni construir nada. El compose de
desarrollo es otro: `backend-spring/docker-compose.yml`.

El análisis de video lo hace el `worker` (`worker/`) con el pipeline de `pipeline/`; el esquema de la base de
datos está en `backend-spring/db/init/001_esquema.sql` y su diagrama en `db_schema.puml`.

Manual completo: [MANUAL.md](MANUAL.md).
