# 04 · Base de datos (HEAD ba903f4, v1.9.2)

Fuentes: `backend-spring/db/init/001_esquema.sql` (esquema, PostgreSQL 16), `backend-spring/src/main/resources/db/migraciones.sql` (migraciones que corre el backend en cada arranque) y `backend-spring/db/init/002_dev_usuario.sql` (solo desarrollo).

**Desde:** los tres archivos son idénticos a los de `15489f4` (producción v1.0.0) salvo los fines de línea (`git show 15489f4:ruta` + `diff` ignorando CR). Por eso todo lo de abajo es `base`. Cifra clave: **nuevo = 0** en la base de datos.

**Cifras clave:** 12 tablas · 58 columnas · 12 llaves primarias · 13 llaves foráneas · 9 UNIQUE con nombre · 1 CHECK con nombre (+ CHECK en línea) · 9 dominios · 5 índices explícitos · 2 migraciones · 1 dato semilla de desarrollo.

## 1. Dominios (`001_esquema.sql`)

| ID | Dominio | Tipo base | Valores permitidos (CHECK) | Desde | Archivo:línea |
|---|---|---|---|---|---|
| D1 | `tipo_grupo` | varchar(24) | control, referencia, tratamiento experimental | base | 001_esquema.sql:9-10 |
| D2 | `sesion_video` | varchar(6) | 'Dia 1', 'Dia 2' | base | 001_esquema.sql:12-13 |
| D3 | `formato_reporte` | varchar(4) | CSV, XLSX, PDF | base | 001_esquema.sql:15-16 |
| D4 | `tipo_notificacion` | varchar(32) | analisis completado, error del pipeline, analisis en pausa, analisis cancelado, alerta de disco, video borrado | base | 001_esquema.sql:18-20 |
| D5 | `estado_analisis` | varchar(10) | en cola, procesando, en pausa, completado, error, cancelado | base | 001_esquema.sql:22-23 |
| D6 | `etapa_analisis` | varchar(16) | preprocesamiento, cilindros, clasificacion | base | 001_esquema.sql:25-26 |
| D7 | `nivel_clasif_analisis` | varchar(8) | preciso, agrupado | base | 001_esquema.sql:28-29 |
| D8 | `origen_segundo` | varchar(16) | maquina, confirmada, corregida, dudosa | base | 001_esquema.sql:31-32 |
| D9 | `tipo_conducta` | varchar(16) | nado activo, inmovilidad, escalamiento, conducta activa | base | 001_esquema.sql:34-35 |

## 2. Tablas, columnas, llaves y restricciones

Convención: `NN` = NOT NULL; `NULL` = admite nulo; `PK`, `FK → tabla(col)` con sus acciones; las acciones `ON UPDATE NO ACTION ON DELETE NO ACTION` son las que el archivo escribe en todas las FK.

### 2.1 usuario (`001_esquema.sql:40-49`)

| ID | Columna | Tipo | Nulo | Default | Restricción | Desde | Archivo:línea |
|---|---|---|---|---|---|---|---|
| U1 | idinstitucional | varchar(10) | NN | — | PK | base | 001_esquema.sql:41 |
| U2 | nombre | varchar(100) | NN | — | | base | :42 |
| U3 | apellidos | varchar(100) | NN | — | | base | :43 |
| U4 | correo | varchar(254) | NN | — | UNIQUE `uq_usuario_correo` | base | :44, :48 |
| U5 | contrasenahash | varchar(255) | NN | — | bcrypt (comentario) | base | :45 |
| U6 | activo | boolean | NN | `true` | | base | :46 |
| U7 | cambiorequerido | boolean | NN | `false` | | base | :47 |

### 2.2 administrador (`:51-54`)

| ID | Columna | Tipo | Nulo | Restricción | Desde | Archivo:línea |
|---|---|---|---|---|---|---|
| A1 | idinstitucional | varchar(10) | NN | PK y FK → usuario(idinstitucional) ON UPDATE NO ACTION ON DELETE NO ACTION | base | :52-53 |

### 2.3 experimento (`:59-67`)

| ID | Columna | Tipo | Nulo | Default | Restricción | Desde | Archivo:línea |
|---|---|---|---|---|---|---|---|
| E1 | idexperimento | integer | NN | identidad (`GENERATED ALWAYS AS IDENTITY`) | PK | base | :60 |
| E2 | idinstitucional | varchar(10) | NN | — | FK → usuario(idinstitucional), NO ACTION/NO ACTION | base | :61-62 |
| E3 | nombre | varchar(150) | NN | — | UNIQUE `uq_experimento_nombre` (nombre único en todo el sistema, RF-11) | base | :63, :66 |
| E4 | fecha | date | NN | — | | base | :64 |
| E5 | notas | text | NULL | — | | base | :65 |

### 2.4 grupo (`:69-77`)

| ID | Columna | Tipo | Nulo | Default | Restricción | Desde | Archivo:línea |
|---|---|---|---|---|---|---|---|
| G1 | idgrupo | integer | NN | identidad | PK | base | :70 |
| G2 | idexperimento | integer | NN | — | FK → experimento(idexperimento), NO ACTION/NO ACTION | base | :71-72 |
| G3 | etiqueta | varchar(100) | NN | — | UNIQUE `uq_grupo_etiqueta` (idexperimento, etiqueta) | base | :73, :76 |
| G4 | tipo | tipo_grupo (D1) | NN | — | | base | :74 |
| G5 | tratamiento | text | NN | — | | base | :75 |

### 2.5 tanda (`:79-86`)

| ID | Columna | Tipo | Nulo | Default | Restricción | Desde | Archivo:línea |
|---|---|---|---|---|---|---|---|
| T1 | idtanda | integer | NN | identidad | PK | base | :80 |
| T2 | idgrupo | integer | NN | — | FK → grupo(idgrupo), NO ACTION/NO ACTION | base | :81-82 |
| T3 | ordinal | smallint | NN | — | CHECK (ordinal ≥ 1) en línea; 1 = A, 2 = B | base | :83 |
| T4 | ncilindros | smallint | NN | — | CHECK (ncilindros BETWEEN 2 AND 4) en línea | base | :84 |
| — | | | | | UNIQUE `uq_tanda_ordinal` (idgrupo, ordinal) | base | :85 |

### 2.6 especimen (`:88-94`)

| ID | Columna | Tipo | Nulo | Default | Restricción | Desde | Archivo:línea |
|---|---|---|---|---|---|---|---|
| P1 | idespecimen | integer | NN | identidad | PK | base | :89 |
| P2 | idtanda | integer | NN | — | FK → tanda(idtanda), NO ACTION/NO ACTION | base | :90-91 |
| P3 | numerocilindro | smallint | NN | — | CHECK (BETWEEN 1 AND 4) en línea | base | :92 |
| — | | | | | UNIQUE `uq_especimen_cilindro` (idtanda, numerocilindro) | base | :93 |

### 2.7 video (`:101-113`)

| ID | Columna | Tipo | Nulo | Default | Restricción | Desde | Archivo:línea |
|---|---|---|---|---|---|---|---|
| V1 | idvideo | integer | NN | identidad | PK | base | :102 |
| V2 | idtanda | integer | NN | — | FK → tanda(idtanda), NO ACTION/NO ACTION | base | :103-104 |
| V3 | sesion | sesion_video (D2) | NN | — | | base | :105 |
| V4 | archivo | text | NULL | — | ruta dentro del volumen; nula cuando el video ya se borró | base | :106 |
| V5 | nombreoriginal | varchar(255) | NULL | — | añadida por migración (ver §4) | base | :107-109 |
| V6 | duracion | numeric(8,2) | NN | — | CHECK (duracion > 0), en segundos | base | :110 |
| V7 | fechacarga | timestamptz | NN | `now()` | | base | :111 |
| — | | | | | UNIQUE `uq_video_sesion` (idtanda, sesion) | base | :112 |

### 2.8 analisis (`:115-127`)

| ID | Columna | Tipo | Nulo | Default | Restricción | Desde | Archivo:línea |
|---|---|---|---|---|---|---|---|
| N1 | idanalisis | integer | NN | identidad | PK | base | :116 |
| N2 | idvideo | integer | NN | — | FK → video(idvideo), NO ACTION/NO ACTION; UNIQUE `uq_analisis_video` (un video, un análisis vigente) | base | :117-118, :124 |
| N3 | estado | estado_analisis (D5) | NN | `'en cola'` | | base | :119 |
| N4 | etapa | etapa_analisis (D6) | NN | `'preprocesamiento'` | valor de arranque (D-79) | base | :120 |
| N5 | nivelclasif | nivel_clasif_analisis (D7) | NN | `'preciso'` | valor de arranque (D-79) | base | :121 |
| N6 | fechaanalisis | timestamptz | NN | `now()` | fecha de encolado | base | :122 |
| N7 | rutadiagnostico | text | NULL | — | CHECK nombrado `ck_analisis_diagnostico`: `rutadiagnostico IS NULL OR estado = 'error'` | base | :123, :125-126 |

### 2.9 observacion (`:132-139`)

| ID | Columna | Tipo | Nulo | Default | Restricción | Desde | Archivo:línea |
|---|---|---|---|---|---|---|---|
| O1 | idobservacion | integer | NN | identidad | PK | base | :133 |
| O2 | idespecimen | integer | NN | — | FK → especimen(idespecimen), NO ACTION/NO ACTION | base | :134-135 |
| O3 | idvideo | integer | NN | — | FK → video(idvideo), NO ACTION/NO ACTION | base | :136-137 |
| — | | | | | UNIQUE `uq_observacion` (idespecimen, idvideo) | base | :138 |

### 2.10 segundo (`:144-151`)

| ID | Columna | Tipo | Nulo | Default | Restricción | Desde | Archivo:línea |
|---|---|---|---|---|---|---|---|
| S1 | idobservacion | integer | NN | — | FK → observacion(idobservacion), NO ACTION/NO ACTION; parte de la PK | base | :145-146 |
| S2 | segundo | smallint | NN | — | CHECK (BETWEEN 1 AND 300); parte de la PK | base | :147, :150 |
| S3 | conducta | tipo_conducta (D9) | NULL | — | nula = no se vio al animal o la revisión la marcó como no visible | base | :148 |
| S4 | origen | origen_segundo (D8) | NN | `'maquina'` | | base | :149 |
| — | | | | | PRIMARY KEY (idobservacion, segundo) | base | :150 |

### 2.11 reporte (`:156-164`)

| ID | Columna | Tipo | Nulo | Default | Restricción | Desde | Archivo:línea |
|---|---|---|---|---|---|---|---|
| R1 | idreporte | integer | NN | identidad | PK | base | :157 |
| R2 | idexperimento | integer | NN | — | FK → experimento(idexperimento), NO ACTION/NO ACTION | base | :158-159 |
| R3 | formato | formato_reporte (D3) | NN | — | | base | :160 |
| R4 | ruta | text | NN | — | UNIQUE `uq_reporte_ruta` | base | :161, :163 |
| R5 | fechageneracion | timestamptz | NN | `now()` | | base | :162 |

Nota: la restricción `uq_reporte_formato` ya no existe en el esquema: la quita la migración del 25-oct-2026 (ver §4).

### 2.12 notificacion (`:166-176`)

| ID | Columna | Tipo | Nulo | Default | Restricción | Desde | Archivo:línea |
|---|---|---|---|---|---|---|---|
| F1 | idnotificacion | integer | NN | identidad | PK | base | :167 |
| F2 | idinstitucional | varchar(10) | NN | — | FK → usuario(idinstitucional), NO ACTION/NO ACTION | base | :168-169 |
| F3 | idexperimento | integer | NULL | — | FK → experimento(idexperimento), NO ACTION/NO ACTION | base | :170-171 |
| F4 | tipo | tipo_notificacion (D4) | NN | — | | base | :172 |
| F5 | mensaje | text | NN | — | | base | :173 |
| F6 | leido | boolean | NN | `false` | | base | :174 |
| F7 | fechacreacion | timestamptz | NN | `now()` | | base | :175 |

Conteo de columnas por tabla: usuario 7 · administrador 1 · experimento 5 · grupo 5 · tanda 4 · especimen 3 · video 7 · analisis 7 · observacion 3 · segundo 4 · reporte 5 · notificacion 7 = **58**.

## 3. Índices (`001_esquema.sql:179-186`)

El comentario del archivo marca el bloque como `[PROPUESTA]` (línea 179). Las UNIQUE y las PK crean además sus propios índices con nombre automático (`<tabla>_pkey`, `uq_…`); no se listan aquí porque el archivo no les da nombre.

| ID | Nombre exacto | Tabla (columnas) | Desde | Archivo:línea |
|---|---|---|---|---|
| I1 | `ix_experimento_usuario` | experimento (idinstitucional) | base | 001_esquema.sql:182 |
| I2 | `ix_analisis_estado` | analisis (estado); el worker sondea por aquí | base | :183 |
| I3 | `ix_observacion_video` | observacion (idvideo) | base | :184 |
| I4 | `ix_notificacion_usuario` | notificacion (idinstitucional, leido) | base | :185 |
| I5 | `ix_notificacion_experim` | notificacion (idexperimento) | base | :186 |

## 4. Migraciones (`backend-spring/src/main/resources/db/migraciones.sql`)

El backend corre este archivo en **cada arranque** (`spring.sql.init.mode=always`, según el comentario de `migraciones.sql:1-4`); por eso cada sentencia es idempotente.

| ID | Fecha | Sentencia | Qué hace | Desde | Archivo:línea |
|---|---|---|---|---|---|
| M1 | 20-oct-2026 | `ALTER TABLE video ADD COLUMN IF NOT EXISTS nombreoriginal varchar(255);` | Agrega el nombre original del video (solo para mostrarlo; NULL en videos anteriores). Las bases que ya existían lo reciben por esta vía. | base | migraciones.sql:6-8 |
| M2 | 25-oct-2026 (C-07) | `ALTER TABLE reporte DROP CONSTRAINT IF EXISTS uq_reporte_formato;` | Quita la unicidad por formato: un experimento puede tener varios reportes del mismo formato (uno por alcance); la unicidad queda por `ruta` (`uq_reporte_ruta`). | base | migraciones.sql:10-12 |

Observación: el esquema inicial (`001_esquema.sql`) ya trae la columna `nombreoriginal` (línea 109) y ya no trae `uq_reporte_formato`; las migraciones cubren bases creadas antes de esos cambios. Ambas migraciones existen igual en `15489f4`.

## 5. Datos semilla

| ID | Qué | Dónde | Cuándo corre | Desde | Archivo:línea |
|---|---|---|---|---|---|
| X1 | Sin administrador inicial en SQL: lo crea el backend con `ADMIN_*` del `.env` | 001_esquema.sql:191-193 (comentario) | — | base | 001_esquema.sql:191-193 |
| X2 | Usuario de desarrollo `DEV0000001` (`dev@fst.local`, contraseña no válida a propósito, no administrador) | 002_dev_usuario.sql:13-14 | Solo desarrollo; **no** se monta en `docker-compose.yml` (ver 05, servicio `db`) | base | 002_dev_usuario.sql:13-14 |

## 6. Verificación de consistencia con el código (sin Docker)

- `001_esquema.sql` abre y cierra con `BEGIN;` / `COMMIT;` (líneas 4 y 195).
- Las 12 tablas coinciden con las del conteo de `grep -n "CREATE TABLE"`; los 9 dominios, con `grep -n "CREATE DOMAIN"`.
- No pude confirmar con una consulta real a PostgreSQL que el esquema aplique limpio en PostgreSQL 16.15: eso requiere Docker o una base local, que no se levantó en este encargo.
