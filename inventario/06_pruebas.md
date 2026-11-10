# 06 · Pruebas (HEAD ba903f4, v1.9.2)

**Cómo se contó:** conteo estático del código (`@Test` en Java, `def test_` en Python) y, para el frontend, el reporte JSON de `npx vitest run` guardado fuera del repositorio. **No se corrió** `./mvnw -o test` ni las pruebas del worker: Maven escribe en `backend-spring/target/` y el encargo pide escribir solo en `inventario/`. Tampoco se levantó Docker ni PostgreSQL.

**Desde:** las pruebas de backend y worker son el mismo conjunto que en `15489f4` salvo lo indicado en cada bloque (el conteo sale del árbol actual, no de la comparación con 15489f4). Un archivo de prueba `nuevo` en el worker: `worker/test_modelo.py`. En el frontend, los archivos de `components/resultados/graficas/` y `components/resultados/SegundoASegundo.test.jsx` aparecen en la rama de resultados; no se comparó cuáles existían en 15489f4.

**Cifras clave:** backend 206 pruebas en 23 archivos trackeados en HEAD (122 solo con `FST_TEST_DB_URL`, 84 sin base; 12 más sin commit); frontend 331 casos en 27 archivos, **todos pasan** en la última corrida; worker 93 métodos en 8 archivos (`unittest`, sin ejecutar).

## 1. Backend (Spring Boot 4.1.1 / Java 21, `backend-spring/`)

Framework: JUnit 5 (`@Test`). Ninguna prueba usa `@ParameterizedTest` (conteo: 0), así que el número de casos es el de anotaciones `@Test`.

Conteo por archivo con regex sobre `backend-spring/src/test/java`, de los archivos **trackeados en HEAD** (`git ls-tree` + `git show HEAD:ruta`). Hay 12 pruebas más sin commit en el árbol de trabajo (`ReporteAlmacenVersionTest.java`, 3, y `service/graficas/GraficasPdfTest.java`, 9): no están en esta tabla. La columna «Desde» es `base` porque el conteo es del árbol actual; no se comparó archivo por archivo con `15489f4` (para hacerlo: `git cat-file -e 15489f4:<ruta>`).

| ID | Archivo (bajo `backend-spring/src/test/java/com/ipn/mx/fst/service/`) | `@Test` | Sin `FST_TEST_DB_URL` | Desde |
|---|---|---|---|---|
| B1 | AdministracionYRecuperacionTest.java | 30 | se omite | base |
| B2 | ArreglosE50Test.java | 14 | se omite | base |
| B3 | BloquesDeCincoTest.java | 12 | corre | base |
| B4 | BloquesDeResultadosTest.java | 5 | se omite | base |
| B5 | EstadisticosTest.java | 6 | corre | base |
| B6 | EstadoConPausaTest.java | 5 | se omite | base |
| B7 | ExcelDelExperimentoTest.java | 4 | corre | base |
| B8 | GraficasDeResultadosTest.java | 10 | corre | base |
| B9 | GraficasEndpointTest.java | 2 | se omite | base |
| B10 | ListaDeExperimentosTest.java | 14 | se omite | base |
| B11 | ModeloActualDeDesarrolloTest.java | 5 | corre | base |
| B12 | ModeloDelAnalisisTest.java | 5 | corre | base |
| B13 | PausaServiceUmbralTest.java | 4 | corre | base |
| B14 | ReglaAdministradoresTest.java | 5 | corre | base |
| B15 | ReinicioDeAnalisisTest.java | 8 | se omite | base |
| B16 | ReporteAlmacenTest.java | 5 | corre | base |
| B17 | ReporteCsvTest.java | 17 | corre | base |
| B18 | ReporteXlsxTest.java | 7 | corre | base |
| B19 | ReportesCsvXlsxTest.java | 18 | se omite | base |
| B20 | RevisionYVideoTest.java | 18 | se omite | base |
| B21 | SegundosDeResultadosTest.java | 4 | corre | base |
| B22 | SegundosYExcelTest.java | 3 | se omite | base |
| B23 | SinVerYNivelTest.java | 5 | se omite | base |
| | **Total** | **206** | **122 se omiten (11 archivos); 84 corren** | |

**Qué significa «se omite»:** el archivo contiene `@EnabledIfEnvironmentVariable(named = "FST_TEST_DB_URL", matches = ".+")`, así que sin esa variable JUnit lo marca como omitido. Se verificó archivo por archivo con un script que busca `EnabledIfEnvironmentVariable` en el texto.

Comandos (documentados en el propio código; **no ejecutados**):

- Sin base: `cd backend-spring && ./mvnw -o test` (las 122 pruebas gated aparecen como *skipped*).
- Con base: `FST_TEST_DB_URL=jdbc:postgresql://localhost:5544/fst_test ./mvnw test` (AdministracionYRecuperacionTest.java:62). La base debe estar vacía y con el esquema de `backend-spring/db/init/001_esquema.sql` (ArreglosE50Test.java:35).

Cifra que **no pude confirmar**: cuántas pasan con base. Requiere Docker o un PostgreSQL local; no se levantó.

## 2. Frontend (React 18.3.1 / Vite 8 / Vitest 5, `frontend/`)

Comando: `npm test` desde `frontend/` (que ejecuta `vitest run`, `frontend/package.json:8`). Entorno jsdom declarado por archivo (`// @vitest-environment jsdom`) y en `frontend/vite.config.js` (`testTimeout: 15000`, `VITE_USE_MOCKS=true`).

**Última corrida:** 27 archivos, **331 casos, 331 pasan, 0 fallan, 0 omitidos** (reporte JSON en la carpeta de trabajo de la sesión, no en el repositorio).

| ID | Archivo | Casos | Desde |
|---|---|---|---|
| F1 | pantallas.test.jsx | 75 | base |
| F2 | services/servicios.test.js | 60 | base |
| F3 | lib/fst.test.js | 23 | base |
| F4 | components/resultados/graficas/GaleriaGraficas.test.jsx (incluye G1 y el selector) | 22 | nuevo |
| F5 | accesibilidad.test.jsx (axe-core por pantalla) | 12 | base |
| F6 | components/resultados/graficas/G5Variabilidad.test.jsx | 12 | nuevo |
| F7 | components/resultados/graficas/G6MapaPorMinuto.test.jsx | 10 | nuevo |
| F8 | components/resultados/SegundoASegundo.test.jsx | 10 | nuevo |
| F9 | eliminarTanda.test.jsx | 10 | base |
| F10 | modo.test.jsx | 15 | base |
| F11 | components/resultados/graficas/G3BarrasPorMinuto.test.jsx | 8 | nuevo |
| F12 | lib/svgAPng.test.js | 8 | nuevo |
| F13 | components/resultados/graficas/G4ApiladaPorGrupo.test.jsx | 9 | nuevo |
| F14 | components/resultados/graficas/G2CurvaPorMinuto.test.jsx | 7 | nuevo |
| F15 | services/graficas.test.js | 7 | nuevo |
| F16 | services/contrato.test.js | 7 | base |
| F17 | campanita.test.jsx | 5 | base |
| F18 | control.test.jsx | 4 | base |
| F19 | lib/porcentajes.test.js | 4 | base |
| F20 | services/tiempos.test.js | 4 | base |
| F21 | components/resultados/graficas/BotonDescargarImagen.test.jsx | 4 | nuevo |
| F22 | borradores.test.jsx | 2 | base |
| F23 | hooks/useAsync.test.jsx | 2 | base |
| F24 | hooks/usePolling.test.jsx | 2 | base |
| F25 | lib/contrato.test.js | 3 | base |
| F26 | diagnostico.test.jsx | 3 | base |
| F27 | services/segundos.test.js | 3 | nuevo |
| | **Total** | **331** | |

Las marcas «base»/«nuevo» de cada archivo se comprobaron con `git cat-file -e 15489f4:<ruta>`: 16 archivos existían en `15489f4` y 11 son nuevos.

Sobre las cifras anteriores: en este inventario no se reusó 202 ni 232 (conteos de corridas previas); el número vigente es 331.

## 3. Worker (Python 3.12, `worker/`)

Framework: `unittest` (no pytest). Cada archivo documenta cómo correrlo en su docstring; por ejemplo `docker exec <worker> python -m unittest test_diagnostico` (`worker/test_diagnostico.py:5`) y `python -m unittest test_correos -v` (`worker/test_correos.py:3`).

| ID | Archivo | Métodos `test_` | Desde |
|---|---|---|---|
| W1 | test_correos.py | 16 | base |
| W2 | test_diagnostico.py | 6 | base |
| W3 | test_modelo.py | 22 | **nuevo** |
| W4 | test_pausa.py | 19 | base |
| W5 | test_reportes.py | 8 | base |
| W6 | test_respaldo.py | 5 | base |
| W7 | test_tiempos.py | 7 | base |
| W8 | test_vigilancia.py | 10 | base |
| | **Total** | **93** | |

Conteo estático (`grep -c "def test_"`). **No se ejecutaron**: dependen de torch, ffmpeg y de la base o del contenedor; el encargo prohíbe usar Docker. Además, `worker/Dockerfile` no copia los `test_*.py` a la imagen (`worker/Dockerfile:28-30`), así que las pruebas corren en el repositorio o en un contenedor de prueba.

## 4. Archivos con prueba que depende de la base

Son los 11 archivos gated de la tabla de §1 (122 pruebas): los que llevan `@EnabledIfEnvironmentVariable(named = "FST_TEST_DB_URL", ...)`. Los 12 restantes (84 pruebas) corren sin base.

## 5. Lo que no pude confirmar

- Que las 206 pruebas de backend (HEAD) pasen (sin base: 84 se ejecutarían y 122 se omitirían; con base: no se corrió).
- Que las 93 pruebas del worker pasen: no se ejecutaron.
- El número de pruebas que `./mvnw -o test` reporta como *skipped* (el conteo estático dice 122; no se corrió Maven, así que no lo confirmo).
- Que `npm test` falle en una máquina limpia: se corrió aquí y pasó, pero no en otra máquina.
