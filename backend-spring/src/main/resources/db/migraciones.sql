-- Migraciones para bases que ya existen. initdb (db/init/001_esquema.sql) solo corre cuando el volumen de la base
-- está vacío; este archivo lo corre el backend en CADA arranque (spring.sql.init.mode=always), antes de que
-- Hibernate valide las entidades contra las tablas. Por eso cada sentencia debe ser IDEMPOTENTE: no hace nada si
-- el cambio ya está hecho.

-- 20-oct-2026: nombre original del video, como lo subió el usuario (solo para mostrarlo). NULL en los videos
-- anteriores.
ALTER TABLE video ADD COLUMN IF NOT EXISTS nombreoriginal varchar(255);

-- 25-oct-2026 (C-07): un experimento puede tener varios reportes del mismo formato (uno por alcance: el experimento,
-- cada grupo, cada tanda). La unicidad es por ruta (uq_reporte_ruta), que ya distingue el alcance.
ALTER TABLE reporte DROP CONSTRAINT IF EXISTS uq_reporte_formato;
