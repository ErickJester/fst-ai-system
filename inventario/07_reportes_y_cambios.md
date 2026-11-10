# 07 · Reportes y cambios desde producción (orquestador_web)

Referencia: producción (Hostinger) = v1.0.0 = commit `15489f4` (21-oct-2026). Hoy: v1.9.2 (`ba903f4`, 8-nov-2026). 14 commits, 91 archivos, 12 612 líneas agregadas. Lo que está en el árbol sin commit (M1B: gráficas en el PDF) no cuenta como parte del sistema publicado.

## 1. Los 14 commits desde producción

| # | Commit | Fecha | Versión | Qué cambió | Visible al usuario |
|---|---|---|---|---|---|
| 1 | 33d405f | 7-nov | 1.1.0 | «Agregar otra tanda» suma un formulario debajo en vez de borrar el actual | Sí |
| 2 | 4ff5ba2 | 7-nov | 1.2.0 | La versión del sistema se ve siempre abajo a la derecha | Sí |
| 3 | b0e9ec8 | 7-nov | 1.2.1 | La franja roja dice «Modo desarrollador» (antes el número de versión) | Solo en modo desarrollador |
| 4 | 6340892 | 7-nov | 1.3.0 | «Agregar otra tanda» hereda grupo, tanda siguiente y su confirmación | Sí |
| 5 | e6c2809 | 7-nov | 1.4.0 | Con varias tandas, un solo botón de guardar las sube en orden | Sí |
| 6 | 4091660 | 7-nov | 1.5.0 | En modo desarrollador, PDF, CSV y XLSX dicen con qué modelo se hizo cada análisis | Solo en modo desarrollador |
| 7 | d2295b0 | 8-nov | — | Diagrama del esquema de la base local (SVG) y ajustes de VS Code | No (documentación del repo) |
| 8 | 5d822c3 | 8-nov | — | `ResultadosPage` dividida en componentes (refactor, sin cambio visible) | No |
| 9 | 14531a7 | 8-nov | 1.6.0 | La revisión segundo a segundo usa el tema claro (se retiró el oscuro) | Sí |
| 10 | f3334c1 | 8-nov | 1.7.0 | Galería de seis gráficas, pestañas reales y endpoint `GET /experiments/{id}/results/charts` | Sí |
| 11 | 324e0f3 | 8-nov | 1.8.0 | Pantalla de resultados del experimento (resumen, «¿Qué pasó?», descargas), vista segundo a segundo, endpoints `results/seconds` y `report/excel` | Sí |
| 12 | 3dbda25 | 8-nov | 1.9.0 | Modo desarrollador: la carga de video muestra el modelo (.pt) que se usará; endpoint `GET /dev/modelo-actual` | Solo en modo desarrollador |
| 13 | b6ade78 | 8-nov | 1.9.1 | La carga de video dice qué falta para poder guardar | Sí |
| 14 | ba903f4 | 8-nov | 1.9.2 | Los textos de resultados vuelven a decir «espécimen» | Sí |

## 2. Endpoints nuevos (backend)

| ID | Método y ruta | Quién | Qué devuelve | Archivo | Desde |
|---|---|---|---|---|---|
| E-N1 | `GET /experiments/{id}/results/charts` | Cualquier sesión | Datos de las seis gráficas: por grupo del Día 2, por conducta, n, media, DE, varianza, coeficiente de variación y valores por espécimen; y por minuto 1 a 5 | `ResultadosController.java`, `GraficasDeResultados.java` | nuevo (1.7.0) |
| E-N2 | `GET /experiments/{id}/results/seconds?grupo=&tanda=` | Cualquier sesión | La conducta de cada segundo de cada espécimen de una tanda, con su origen; 400 si falta la tanda, 404 si no existe o no está completada | `SegundosController.java`, `SegundosDeResultados.java` | nuevo (1.8.0) |
| E-N3 | `GET /experiments/{id}/report/excel` | Cualquier sesión | XLSX del experimento completo con dos hojas: «Resultados» y «Segundo a segundo»; 409 si no hay análisis completados; se genera al momento, no se guarda | `ExcelExperimentoController.java`, `ExcelDelExperimento.java` | nuevo (1.8.0) |
| E-N4 | `GET /dev/modelo-actual` | Cualquier sesión; **404 sin modo desarrollador** | El modelo (.pt) que usa el worker: archivo, versión de la red, SHA-256, tamaño y fecha | `DevModeloController.java`, `ModeloActualDeDesarrollo.java` | nuevo (1.9.0) |

## 3. Reportes: estado actual

| ID | Reporte | Contenido | Archivo | Desde |
|---|---|---|---|---|
| R-1 | PDF del experimento (`report/pdf`) | Resultados por espécimen, estadísticos, avisos; con el modelo en modo desarrollador | `ExperimentoServiceImpl.generarReporteDeResultados` | base; el modelo es nuevo (1.5.0) |
| R-2 | CSV de grupo o tanda (`report/csv`) | Tabla con una columna por conducta en segundos, media, DE, varianza, revisión; con el modelo en modo desarrollador | `ReporteCsv.java`, `TablaDeReporte.java` | base; el modelo es nuevo (1.5.0) |
| R-3 | XLSX de grupo o tanda (`report/xlsx`) | Lo mismo en la hoja «Resultados» | `ReporteXlsx.java` | base; el modelo es nuevo (1.5.0) |
| R-4 | **Excel del experimento** (`report/excel`) | Hoja «Resultados» (una tabla por grupo) y hoja «Segundo a segundo»: Grupo, Tanda, Espécimen, Tiempo (s), Conducta, una columna 1 o 0 por conducta (cinco) y Origen | `ExcelDelExperimento.java` | **nuevo** (1.8.0) |

Nota: la hoja «Segundo a segundo» responde a lo que pidió el Dr. Sandino (P10): tiempo en segundos y una columna por conducta.

## 4. Lo que cambió en la pantalla de resultados

| ID | Cambio | Desde |
|---|---|---|
| P-1 | «Ver resultados» abre un **resumen del experimento** (no una tanda): «Listos X de Y grupos», «¿Qué pasó? (comparado con el control)» con selector de conducta, galería de gráficas, «Descargar» (PDF y Excel) y «Ver el detalle» | nuevo (1.8.0) |
| P-2 | El detalle por tanda tiene pestañas reales (`?vista=`): Por espécimen, Segundo a segundo, Minuto a minuto, Cada 5 segundos, Comparación entre grupos; con selector de grupo y de tanda | nuevo (1.7.0 y 1.8.0) |
| P-3 | Seis gráficas, todas del Día 2 y de todos los grupos, con tabla de números y «Descargar imagen» (PNG): G1 barras por grupo, G2 curva por minuto, G3 barras por minuto, G4 reparto de la duración, G5 variabilidad entre especímenes, G6 mapa por minuto | nuevo (1.7.0) |
| P-4 | La comparación entre grupos dejó la columna derecha y es una pestaña | cambió (1.7.0) |
| P-5 | Vista «Segundo a segundo»: franja de colores y patrones por espécimen y tabla con tiempo, conducta y origen (Sistema, Confirmado en revisión, Corregido en revisión, Marcado como no se ve) | nuevo (1.8.0) |
| P-6 | La página ya no se desborda a lo ancho a 390 px | cambió (1.7.0) |

## 5. Otros cambios

| ID | Cambio | Desde |
|---|---|---|
| O-1 | Cargar video: varias tandas en la misma página, un solo botón que las sube en orden | nuevo (1.1.0 a 1.4.0) |
| O-2 | Cargar video: bajo el botón de guardar se dice qué falta | nuevo (1.9.1) |
| O-3 | Versión del sistema siempre visible abajo a la derecha | nuevo (1.2.0) |
| O-4 | Modo desarrollador: franja roja, modelo en reportes y modelo en la carga de video | nuevo (1.2.1, 1.5.0, 1.9.0) |
| O-5 | Revisión segundo a segundo con el tema claro (el oscuro se retiró) | cambió (1.6.0) |
| O-6 | Versionado automático con `tools/version.py` (lo calcula desde los commits) | ya existía en la base |

## 6. Pendiente (no publicado)

- **M1B:** gráficas G1 y G2 dibujadas en el PDF, índice lateral de marcadores y borrado de PDF guardados al actualizar. Sin commit y en pausa; no cuenta como parte del sistema publicado.
