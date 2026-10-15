-- ============================================================
--  datos_prueba_pdf.sql  -  SOLO PARA PROBAR EL REPORTE PDF
-- ============================================================
-- Sin worker no hay resultados, y sin resultados el PDF solo dice "Sin resultados". Este script
-- inventa lo que escribiria el worker al terminar un analisis: un experimento con un grupo, una
-- tanda de 2 especimenes, su video del Dia 2 con su analisis completado y, por especimen, 300 filas
-- en SEGUNDO (5 minutos). Los totales por minuto no se guardan: el PDF los cuenta desde SEGUNDO.
-- Los numeros son inventados.
--
-- No se ejecuta solo (no esta en db/init). Se corre a mano:
--   docker compose exec -T db psql -U fst -d fst < db/pruebas/datos_prueba_pdf.sql
-- y luego se abre http://localhost:8080/experiments/{id}/report/pdf con el id que imprime.
--
-- Ese experimento tiene un grupo, asi que DELETE /experiments/{id} responde 409 (es lo esperado).

DO $$
DECLARE
  v_experimento integer;
  v_grupo       integer;
  v_tanda       integer;
  v_video       integer;
  v_especimen   integer;
  v_observacion integer;
BEGIN
  INSERT INTO experimento (idinstitucional, nombre, fecha, notas)
  VALUES ('DEV0000001', 'Prueba del reporte PDF', DATE '2026-10-01', 'Datos inventados por datos_prueba_pdf.sql')
  RETURNING idexperimento INTO v_experimento;

  INSERT INTO grupo (idexperimento, etiqueta, tipo, tratamiento)
  VALUES (v_experimento, 'Control', 'control', 'Salina')
  RETURNING idgrupo INTO v_grupo;

  INSERT INTO tanda (idgrupo, ordinal, ncilindros)
  VALUES (v_grupo, 1, 2)
  RETURNING idtanda INTO v_tanda;

  INSERT INTO video (idtanda, sesion, archivo, duracion)
  VALUES (v_tanda, 'Dia 2', NULL, 300)
  RETURNING idvideo INTO v_video;

  INSERT INTO analisis (idvideo, estado, etapa, nivelclasif, fechaanalisis)
  VALUES (v_video, 'completado', 'clasificacion', 'agrupado', now());

  FOR c IN 1..2 LOOP
    INSERT INTO especimen (idtanda, numerocilindro)
    VALUES (v_tanda, c)
    RETURNING idespecimen INTO v_especimen;

    INSERT INTO observacion (idespecimen, idvideo)
    VALUES (v_especimen, v_video)
    RETURNING idobservacion INTO v_observacion;

    -- Dentro de cada minuto, los primeros "inm" segundos son inmovilidad, luego 5 de escalamiento, 2 de
    -- conducta activa y el resto de nado activo. La inmovilidad crece con los minutos, como suele pasar en
    -- la prueba. El ultimo segundo de cada minuto queda sin conducta (nula) para ver que el reporte no
    -- exige sumar 60.
    INSERT INTO segundo (idobservacion, segundo, conducta, origen)
    SELECT v_observacion,
           x.s,
           CASE WHEN x.k < x.inm          THEN 'inmovilidad'
                WHEN x.k < x.inm + 5      THEN 'escalamiento'
                WHEN x.k < x.inm + 5 + 2  THEN 'conducta activa'
                WHEN x.k < 59             THEN 'nado activo'
           END,
           'maquina'
    FROM (
      SELECT s::smallint AS s,
             (s - 1) % 60 AS k,
             8 * ((s - 1) / 60 + 1) + 5 * (c - 1) AS inm
      FROM generate_series(1, 300) AS s
    ) AS x;
  END LOOP;

  RAISE NOTICE 'Experimento de prueba creado con id %', v_experimento;
END $$;
