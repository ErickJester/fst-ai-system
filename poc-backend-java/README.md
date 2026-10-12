# Prueba de concepto: el backend FST en Spring Boot

Replica en Java/Spring Boot una parte del backend de Flask para **medir** lo que
cuesta la migración, en lugar de estimarla. No sustituye a `backend/`: es una
rama y una carpeta aparte, y se puede borrar sin tocar nada del sistema.

## Qué cubre

3 entidades de las 11 (`User`, `Experiment`, `Video`, más `Job` porque la subida
de video lo crea) y 6 endpoints de los 24:

| Endpoint | Caso probado |
|---|---|
| `GET /health` | respuesta vacía |
| `POST /auth/login` | credenciales válidas, usuario inexistente, campos vacíos |
| `POST /experiments` | alta correcta, nombre vacío, usuario inexistente |
| `GET /experiments` | listado |
| `POST /experiments/{id}/videos` | subida, día inválido, día duplicado (RN-03) |
| `GET /experiments/{id}/videos` | listado |

Corre en el puerto **8082** contra la **misma base** que el backend de Python,
para poder comparar los dos lado a lado.

## Resultado

**12 de 12 casos con el mismo código HTTP y la misma forma de respuesta**, tras
dos ciclos de corrección. Las fechas coinciden hasta el microsegundo:

```
flask  : {"created_at": "2026-10-11T05:36:57.498921", "id": 3, ...}
spring : {"created_at": "2026-10-11T05:36:58.579846", "id": 4, ...}
```

## Lo que se aprendió (y no estaba previsto)

**1. `ddl-auto=validate` pasó a la primera.** El esquema que creó SQLAlchemy es
mapeable desde JPA sin tocar la base. Los tipos `ENUM` nativos de PostgreSQL
(`role`, `day`, `jobstatus`) funcionan con `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`
más `columnDefinition`. Era el riesgo grande y no se materializó.

**2. Pero `validate` no protege de columnas no mapeadas.** La tabla `jobs` tiene
`progress_pct` como `NOT NULL`, con el valor por omisión en el modelo de
SQLAlchemy y no en PostgreSQL. Al no mapearla, Hibernate la dejaba fuera del
INSERT y la subida de video fallaba con 500. `validate` no avisa: solo comprueba
las columnas que sí declaraste. **En la migración completa hay que revisar las
columnas `NOT NULL` de las 11 tablas una por una.**

**3. Spring Boot 4.1 trae Jackson 3** (`tools.jackson`, no `com.fasterxml`).
La propiedad `spring.jackson.serialization.write-dates-as-timestamps` ya no
existe y la aplicación no arranca si se deja. Es la versión que da Initializr
hoy, así que buena parte de los ejemplos que hay en línea son de Jackson 2.

**4. Las fechas difieren por dos motivos a la vez.** Flask guarda
`datetime.utcnow()`: UTC con microsegundos. `LocalDateTime.now()` da hora local
con nanosegundos — seis horas de diferencia y tres dígitos de más. Se iguala con
`LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS)`, en
`domain/Reloj.java`.

## Cuánto costaría la migración completa

La prueba tomó alrededor de 45 minutos de reloj, incluidos los dos ciclos de
diagnóstico. Extrapolando a lo que falta:

| Trabajo | Estimación |
|---|---|
| 7 entidades restantes y sus repositorios | 1–2 h |
| Endpoints de listado y administración | 2–3 h |
| `/results` y `/status` (agregación por minuto y por conducta) | 3–4 h |
| Reportes PDF y CSV | 2–3 h |
| Pruebas de contrato de los 24 endpoints | 2–3 h |
| Integración con el frontend real | 2–4 h |

**Entre 12 y 20 horas: de dos a tres días de trabajo.**

El dato que más informa no es el total, sino la proporción: **3 diferencias en
12 casos, todas de una sola causa raíz**, resueltas en un ciclo. Extrapolado a
24 endpoints son unas 6 a 8 diferencias de 3 o 4 causas distintas. Es un volumen
manejable, no una cadena de sorpresas.

## Lo que esta prueba NO demuestra

- **El worker sigue siendo Python.** Esta prueba no lo toca. Un API en Java y un
  worker en Python que comparten PostgreSQL es viable, pero no está probado aquí.
- **No hay autenticación de verdad**, igual que en el backend de Flask: el login
  no verifica la contraseña y el token es un texto fijo. La prueba replica esa
  deuda tal cual para que la comparación sea justa, no la resuelve.
- **El frontend real solo se probó con peticiones `curl`**, no en el navegador.
  Ver la sección siguiente.

## Prueba con el frontend de Vanesa (11 oct 2026)

Frontend: `origin/feat/frontend-v2-servicios` (7 oct, 43 commits por delante de
`main`), con `VITE_USE_MOCKS=false` y la API en el puerto 8082.

| Endpoint | Resultado |
|---|---|
| `POST /auth/login` | Compatible: devuelve `token` y `user` con `role` y `must_change_password` |
| `GET /experiments` | Responde (lista vacía) |
| `POST /experiments` | Falla: `{"error":"name required"}`. El frontend manda `titulo`, `fecha_inicio`, `especie`, `notas` |
| `/experiments/:clave`, `/queue`, `/notifications`, `/admin/users`, `/admin/system` | 404, no existen en la PoC |

La PoC replica el backend de Flask de `main`, y ese backend tampoco implementa el
contrato nuevo de Vanesa (`PARA_ERICK.md`, 25 rutas; la PoC cubre unas 3).

Desajustes ya visibles:

- `users` no tiene `apellidos` ni `identificador`, que el frontend usa.
- No hay autenticación real (contraseña, cambio, recuperación, temporal), y el
  frontend ya la tiene en pantalla.

**Conclusión:** no migrar el backend actual tal cual. Conviene implementar el
contrato nuevo directamente en Spring Boot, empezando por auth y administración
de usuarios. El pipeline y el worker se quedan en Python.

## Cómo correrla

```bash
cd poc-backend-java
DATA_DIR=./data ./mvnw spring-boot:run      # queda en http://localhost:8082
```

Necesita la base del sistema levantada (`docker compose up -d db`) y un usuario
en la tabla `users` para probar el login. El script de comparación contra el
backend de Flask está en el historial de la conversación que generó esta prueba.
