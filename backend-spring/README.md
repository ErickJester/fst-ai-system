# Backend FST en Spring Boot

API REST del sistema FST (TT 2026-B066). Sigue la guía `INSTRUCCIONES-WEB.md` y la estructura de la
práctica de clase `ApiEventos` (controller, service, repository, domain y utilerias).

## Qué hay hasta ahora

Solo lo que se ha visto en clase: CRUD, PDF y correo.

| Sección de la guía | Estado |
|---|---|
| 0. Base y Docker | Compose con `db`, `backend` y `mailhog` (perfil `dev`), `.env.example` y `/health` |
| 1. Base de datos | El SQL del Anexo B en `db/init/001_esquema.sql` y las 12 entidades JPA (`intervalo`, `presenta` y `conducta` ya no existen: los totales por minuto se calculan desde `segundo` y la conducta es el dominio `tipo_conducta`). Hibernate no crea las tablas: `ddl-auto=validate` comprueba que coinciden al arrancar |
| 4. Experimentos | Solo el CRUD (`GET`, `POST`, `PUT`, `DELETE /experiments`). Sin grupos, tandas ni carga de video |
| 8. Reportes | Solo el PDF de resultados (`GET /experiments/{id}/report/pdf`), con iText y en memoria |
| 9. Correo | `EmailService` por SMTP. Al crear un experimento se manda un correo **de demostración** al creador |
| 10. `worker_fake` | En `../worker_fake` (Python), servicio `worker` de este compose. Ver su README |

Falta todo lo demás: inicio de sesión (JWT, bcrypt), administración de cuentas, carga de video, progreso,
resultados, revisión, CSV y XLSX y la lectura de notificaciones. Mientras no exista la carga de video,
`worker_fake/simular_carga.py` encola videos de prueba.

Diferencias con la práctica:
- PostgreSQL en lugar de MySQL.
- El esquema lo crea el SQL, no Hibernate (`validate` en lugar de `update`): el SQL trae dominios,
  `CHECK` y llaves foráneas que Hibernate no genera.
- Sin inicio de sesión, el creador del experimento (`idInstitucional`) llega en el cuerpo del `POST`.
  `db/init/002_dev_usuario.sql` crea un usuario de desarrollo (`DEV0000001`) para poder probar.

## Cómo correrlo

Necesita Docker Desktop encendido.

```
cp .env.example .env
docker compose --profile dev up --build
```

| Qué | Dónde |
|---|---|
| API | http://localhost:8084 |
| Swagger | http://localhost:8084/documentacion/swagger-ui.html |
| Correos de prueba (MailHog) | http://localhost:8025 |
| PostgreSQL | `localhost:5433` (usuario y base `fst`) |

El esquema solo se aplica la primera vez. Para empezar de cero: `docker compose down -v` (**borra los datos**).

## Probar

Crear un experimento:

```
POST /experiments
{"idInstitucional": "DEV0000001", "nombre": "Exp. A", "fecha": "2026-10-01", "notas": ""}
```

| Caso | Respuesta |
|---|---|
| Datos válidos | 201, y un correo en MailHog |
| Nombre vacío o sin fecha | 400 con la lista de `errores` |
| `idInstitucional` que no existe | 404 |
| Nombre repetido | 409 |
| `GET`, `PUT` o `DELETE` de un id que no existe | 404 |
| `DELETE` de un experimento con grupos | 409 |

El PDF de un experimento sin análisis dice "Sin resultados". Para ver la tabla con datos:

```
docker compose exec -T db psql -U fst -d fst < db/pruebas/datos_prueba_pdf.sql
```

Imprime el id del experimento de prueba; abre `http://localhost:8084/experiments/{id}/report/pdf`.
