# Sistema FST

Aplicación web para gestionar experimentos del test de nado forzado (FST):
usuarios, experimentos, subida de videos por día, cola de análisis y
resultados por animal.

| Servicio | Tecnología | Puerto |
|---|---|---|
| `frontend` | React + Vite | 5173 |
| `api` | Flask + SQLAlchemy | 8000 |
| `db` | PostgreSQL 16 | 5432 |
| `worker` | Python (placeholder del pipeline) | — |

```bash
docker-compose up --build
```

**El pipeline de análisis de video todavía no está implementado.** El
`worker` solo muestra en su log que ahí irá; los videos subidos quedan en cola.
Lo que el pipeline debe escribir está descrito en `backend/worker/worker.py`,
y el esquema de la base de datos en `db_schema.puml`.

Manual completo: [MANUAL.md](MANUAL.md).
