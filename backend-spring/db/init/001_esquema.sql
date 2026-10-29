-- ============================================================
--  001_esquema.sql  -  Sistema FST  -  PostgreSQL 16
-- ============================================================
BEGIN;

-- ------------------------------------------------------------
--  Dominios cerrados
-- ------------------------------------------------------------
CREATE DOMAIN tipo_grupo AS varchar(24)
  CHECK (VALUE IN ('control', 'referencia', 'tratamiento experimental'));

CREATE DOMAIN sesion_video AS varchar(6)
  CHECK (VALUE IN ('Dia 1', 'Dia 2'));

CREATE DOMAIN formato_reporte AS varchar(4)
  CHECK (VALUE IN ('CSV', 'XLSX', 'PDF'));

CREATE DOMAIN tipo_notificacion AS varchar(32)
  CHECK (VALUE IN ('analisis completado', 'error del pipeline', 'analisis en pausa',
                   'analisis cancelado', 'alerta de disco', 'video borrado'));

CREATE DOMAIN estado_analisis AS varchar(10)
  CHECK (VALUE IN ('en cola', 'procesando', 'en pausa', 'completado', 'error', 'cancelado'));

CREATE DOMAIN etapa_analisis AS varchar(16)
  CHECK (VALUE IN ('preprocesamiento', 'cilindros', 'clasificacion'));

CREATE DOMAIN nivel_clasif_analisis AS varchar(8)
  CHECK (VALUE IN ('preciso', 'agrupado'));

CREATE DOMAIN origen_segundo AS varchar(16)
  CHECK (VALUE IN ('maquina', 'confirmada', 'corregida', 'dudosa'));

CREATE DOMAIN tipo_conducta AS varchar(16)
  CHECK (VALUE IN ('nado activo', 'inmovilidad', 'escalamiento', 'conducta activa'));

-- ------------------------------------------------------------
--  Usuarios
-- ------------------------------------------------------------
CREATE TABLE usuario (
  idinstitucional  varchar(10)  PRIMARY KEY,
  nombre           varchar(100) NOT NULL,
  apellidos        varchar(100) NOT NULL,
  correo           varchar(254) NOT NULL,
  contrasenahash   varchar(255) NOT NULL,   -- bcrypt con sal, nunca texto plano
  activo           boolean      NOT NULL DEFAULT true,   -- dar de baja sin borrar
  cambiorequerido  boolean      NOT NULL DEFAULT false,  -- obliga a cambiar la contrasena temporal
  CONSTRAINT uq_usuario_correo UNIQUE (correo)
);

CREATE TABLE administrador (
  idinstitucional  varchar(10) PRIMARY KEY
    REFERENCES usuario (idinstitucional) ON UPDATE NO ACTION ON DELETE NO ACTION
);

-- ------------------------------------------------------------
--  Experimento, grupo, tanda, espécimen
-- ------------------------------------------------------------
CREATE TABLE experimento (
  idexperimento    integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  idinstitucional  varchar(10)  NOT NULL
    REFERENCES usuario (idinstitucional) ON UPDATE NO ACTION ON DELETE NO ACTION,
  nombre           varchar(150) NOT NULL,
  fecha            date         NOT NULL,
  notas            text,
  CONSTRAINT uq_experimento_nombre UNIQUE (nombre)   -- nombre unico en todo el sistema (RF-11)
);

CREATE TABLE grupo (
  idgrupo        integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  idexperimento  integer      NOT NULL
    REFERENCES experimento (idexperimento) ON UPDATE NO ACTION ON DELETE NO ACTION,
  etiqueta       varchar(100) NOT NULL,
  tipo           tipo_grupo   NOT NULL,
  tratamiento    text         NOT NULL,
  CONSTRAINT uq_grupo_etiqueta UNIQUE (idexperimento, etiqueta)
);

CREATE TABLE tanda (
  idtanda     integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  idgrupo     integer  NOT NULL
    REFERENCES grupo (idgrupo) ON UPDATE NO ACTION ON DELETE NO ACTION,
  ordinal     smallint NOT NULL CHECK (ordinal >= 1),          -- 1 = A, 2 = B, ...
  ncilindros  smallint NOT NULL CHECK (ncilindros BETWEEN 2 AND 4),
  CONSTRAINT uq_tanda_ordinal UNIQUE (idgrupo, ordinal)
);

CREATE TABLE especimen (
  idespecimen     integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  idtanda         integer  NOT NULL
    REFERENCES tanda (idtanda) ON UPDATE NO ACTION ON DELETE NO ACTION,
  numerocilindro  smallint NOT NULL CHECK (numerocilindro BETWEEN 1 AND 4),
  CONSTRAINT uq_especimen_cilindro UNIQUE (idtanda, numerocilindro)
);

-- ------------------------------------------------------------
--  Video y análisis
--  Solo los videos del Dia 2 tienen analisis (D-71). La base no puede
--  forzarlo: el backend simplemente no crea ANALISIS para el Dia 1.
-- ------------------------------------------------------------
CREATE TABLE video (
  idvideo     integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  idtanda     integer      NOT NULL
    REFERENCES tanda (idtanda) ON UPDATE NO ACTION ON DELETE NO ACTION,
  sesion      sesion_video NOT NULL,
  archivo     text,                         -- ruta dentro del volumen; nula cuando el video ya se borro
  -- nombre del archivo como lo subio el usuario, solo para mostrarlo (nunca arma rutas); NULL en videos
  -- anteriores al 20-oct-2026. Las bases que ya existian lo reciben con db/migraciones.sql (la corre el backend).
  nombreoriginal varchar(255),
  duracion    numeric(8,2) NOT NULL CHECK (duracion > 0),    -- segundos
  fechacarga  timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT uq_video_sesion UNIQUE (idtanda, sesion)
);

CREATE TABLE analisis (
  idanalisis       integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  idvideo          integer NOT NULL
    REFERENCES video (idvideo) ON UPDATE NO ACTION ON DELETE NO ACTION,
  estado           estado_analisis       NOT NULL DEFAULT 'en cola',
  etapa            etapa_analisis        NOT NULL DEFAULT 'preprocesamiento',  -- valor de arranque (D-79); el worker la actualiza
  nivelclasif      nivel_clasif_analisis NOT NULL DEFAULT 'preciso',           -- valor de arranque (D-79); el worker lo fija
  fechaanalisis    timestamptz           NOT NULL DEFAULT now(),               -- fecha de encolado; el worker la reemplaza al terminar
  rutadiagnostico  text,
  CONSTRAINT uq_analisis_video UNIQUE (idvideo),       -- un video, un analisis vigente
  CONSTRAINT ck_analisis_diagnostico
    CHECK (rutadiagnostico IS NULL OR estado = 'error')
);

-- ------------------------------------------------------------
--  Resultados
-- ------------------------------------------------------------
CREATE TABLE observacion (
  idobservacion  integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  idespecimen    integer NOT NULL
    REFERENCES especimen (idespecimen) ON UPDATE NO ACTION ON DELETE NO ACTION,
  idvideo        integer NOT NULL
    REFERENCES video (idvideo) ON UPDATE NO ACTION ON DELETE NO ACTION,
  CONSTRAINT uq_observacion UNIQUE (idespecimen, idvideo)
);

-- Una fila por espécimen y por segundo. Es lo que se revisa y corrige en la pantalla
-- de revisión; los totales por minuto no se guardan: se calculan desde esta tabla (D-52).
-- La conducta es el dominio cerrado tipo_conducta (D-102), no una tabla.
CREATE TABLE segundo (
  idobservacion  integer        NOT NULL
    REFERENCES observacion (idobservacion) ON UPDATE NO ACTION ON DELETE NO ACTION,
  segundo        smallint       NOT NULL CHECK (segundo BETWEEN 1 AND 300),
  conducta       tipo_conducta,                           -- nula = no se vio al animal (maquina) o la revision la marco como no visible
  origen         origen_segundo NOT NULL DEFAULT 'maquina',
  PRIMARY KEY (idobservacion, segundo)
);

-- ------------------------------------------------------------
--  Reportes y notificaciones
-- ------------------------------------------------------------
CREATE TABLE reporte (
  idreporte        integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  idexperimento    integer         NOT NULL
    REFERENCES experimento (idexperimento) ON UPDATE NO ACTION ON DELETE NO ACTION,
  formato          formato_reporte NOT NULL,
  ruta             text            NOT NULL,
  fechageneracion  timestamptz     NOT NULL DEFAULT now(),
  CONSTRAINT uq_reporte_ruta UNIQUE (ruta)
);

CREATE TABLE notificacion (
  idnotificacion   integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  idinstitucional  varchar(10) NOT NULL
    REFERENCES usuario (idinstitucional) ON UPDATE NO ACTION ON DELETE NO ACTION,
  idexperimento    integer
    REFERENCES experimento (idexperimento) ON UPDATE NO ACTION ON DELETE NO ACTION,
  tipo             tipo_notificacion NOT NULL,
  mensaje          text              NOT NULL,
  leido            boolean           NOT NULL DEFAULT false,
  fechacreacion    timestamptz       NOT NULL DEFAULT now()
);

-- ------------------------------------------------------------
--  Índices [PROPUESTA]. PostgreSQL no indexa solo las llaves foráneas;
--  las UNIQUE de arriba ya cubren varias.
-- ------------------------------------------------------------
CREATE INDEX ix_experimento_usuario   ON experimento (idinstitucional);
CREATE INDEX ix_analisis_estado       ON analisis (estado);       -- el worker hace polling por aqui
CREATE INDEX ix_observacion_video     ON observacion (idvideo);
CREATE INDEX ix_notificacion_usuario  ON notificacion (idinstitucional, leido);
CREATE INDEX ix_notificacion_experim  ON notificacion (idexperimento);

-- ------------------------------------------------------------
--  Datos semilla
-- ------------------------------------------------------------
-- El administrador inicial NO se inserta aqui: no hay contrasena que poner en un SQL.
-- Lo creara el backend con los valores ADMIN_* del .env (seccion 11, punto 7) cuando haya
-- autenticacion. Mientras tanto, 002_dev_usuario.sql crea un usuario solo para desarrollo.

COMMIT;
