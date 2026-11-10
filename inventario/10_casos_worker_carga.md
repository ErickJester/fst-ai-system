# 10 · Casos comprobados: worker y prueba de carga

**Aviso:** las pruebas de `worker/` **no corren aquí**; corren dentro de la imagen del worker con Docker (`docker exec <worker> python -m unittest <archivo>`). Este documento es conteo estático de `def test_` leyendo los cuerpos de cada prueba. No se usó Docker.

Las áreas siguen `inventario/09_resumen_cap7.md`. El worker cae en las áreas 3 (worker y pipeline), 6 (notificaciones y correo) y 8 (base de datos). Los 8 casos de `test_reportes.py` se cuentan en el área 3 (igual que en 09), así no se suman dos veces.

---

## Parte 1 · Worker

### Área 3 · Worker y pipeline (72 pruebas)

| Caso comprobado | Pruebas | Qué simula |
|---|---|---|
| **Pausa: la racha de tres segundos sin animal** pausa el análisis; dos segundos no bastan | 1 | Cuadros de segundos en memoria, sin video. |
| **Pausa: un segundo sin fotogramas con animal** cuenta como sin animal | 1 | Cuadros en memoria. |
| **Pausa: entre dos cilindros gana la racha más temprana** (aunque el cilindro sea de número mayor); en empate, gana el número menor | 2 | Cuadros en memoria. |
| **Pausa: solo se busca en los últimos 300 s guardados**, y ahí gana la racha más temprana en el tiempo | 1 | Cuadros en memoria. |
| **Pausa: el recuadro sale del bloque del instante**, no de la mediana; si no hay bloque en el instante, sale el más cercano | 2 | Tabla de rasgos en memoria. |
| **Pausa: el ajuste se acumula en `correcciones.json` y borra `pausa.json`**; sin ajuste no se toca nada | 2 | Carpeta temporal. |
| **Pausa: el instante es el centro de la racha de 3 s**, y la corrección empieza un segundo antes (k−1) | 2 | Sin archivos; funciones puras. |
| **Pausa: la foto se lee sin enderezar** y su tamaño sale de la foto (500 × 900) | 1 | Captura de video falsa (`cv2.VideoCapture` simulado) y foto en memoria. |
| **Pausa: un tramo ya aceptado no vuelve a pausar** aunque sea más largo que 3 s; un tramo distinto del mismo cilindro sí pausa; otro cilindro no se afecta | 3 | Racha en memoria y archivo de rachas aceptadas en carpeta temporal. |
| **Pausa: «continuar» acumula la racha en la lista y borra la pausa**; sin «continuar» no cambia nada | 2 | Carpeta temporal con `pausa.json`. |
| **Pausa: los segundos sin animal se anulan como conducta** y el resto no | 1 | Conductas en memoria. |
| **Escritura atómica de JSON**: sin archivos temporales que sobren | 1 | Carpeta temporal. |
| **El avance nunca llega a 1 ni baja**, y sigue avanzando después de lo que se esperaba | 2 | Reloj falso (`Reloj`), sin proceso real. |
| **Lectura del último paso del registro y de los segundos de ffmpeg** (sin registro, con varios pasos) | 2 | Texto en memoria. |
| **El fotograma de referencia se descarta**; un salto que se queda se acepta; sin proceso, no hay lectura | 3 | Lecturas simuladas y archivo temporal de posición. |
| **El avance de cilindros suma pasos** y nunca pasa de 100 | 1 | Registro de texto simulado; video de 1000 bytes. |
| **En clasificación, el avance sigue el tiempo de predicción** y no pasa de 88 | 1 | Registro de texto simulado; ritmo en archivo. |
| **El ritmo de predicción se lee y se guarda** (valor por omisión si no hay archivo) | 1 | Archivo temporal. |
| **El PDF de diagnóstico** pone la imagen de control debajo del texto; sin control no hay imagen | 2 | PDF real en carpeta temporal, con un registro de texto. |
| **Texto largo del diagnóstico**: la imagen pasa a una segunda página si no cabe | 1 | PDF real en carpeta temporal. |
| **El PDF de diagnóstico trae sus páginas e imagen**; cualquier error después del control incluye la imagen y, sin conteo, no dice números | 2 | PDF real en carpeta temporal. |
| **Los rangos de tubos salen del registro** (y vacío si no hay registro) | 1 | Registro de texto en carpeta temporal. |
| **Tiempos: sin modo desarrollador no se escribe `tiempos.json`** | 1 | Variable de entorno falsa, carpeta temporal. |
| **Tiempos: al empezar hay inicio y equipo, y nada cerrado** | 1 | Carpeta temporal. |
| **Tiempos: cada etapa cierra la anterior** (fin y segundos) | 1 | Carpeta temporal. |
| **Tiempos: al cerrar quedan fin, total y subpasos**; con error o cancelado se cierra hasta donde llegó | 2 | Carpeta temporal. |
| **Tiempos: sin archivo no falla**, y reiniciar reescribe desde cero | 2 | Carpeta temporal. |
| **Identidad del modelo**: archivo, huella SHA-256, tamaño y datos del paquete; un modelo que no existe da null; si el paquete no se puede leer, quedan la huella y la versión en null | 3 | Archivo de pesos falso («pesos de prueba») y `_leer_paquete` simulado (no hay torch). |
| **Sin modo desarrollador no se calcula ni se escribe la identidad del modelo** | 1 | Variable de entorno falsa; paquete simulado que no debe llamarse. |
| **La identidad se calcula una vez** y otra si el archivo cambia; lo devuelto no se comparte con la memoria | 2 | Igual que lo anterior; cuenta de llamadas al paquete simulado. |
| **`tiempos.json` trae el modelo** y, sin modelo o sin modo, va null | 3 | Igual; carpeta temporal. |
| **El modelo se conserva al cerrar el análisis** | 1 | Igual. |
| **Texto del modelo**: arma la frase y omite lo que no se sabe | 2 | Diccionarios en memoria. |
| **`modelo_del_analisis` lee el modelo de `tiempos.json`** y es null sin modo, sin archivo o de antes | 2 | Carpeta temporal. |
| **El diagnóstico dice con qué modelo se hizo**; sin modelo queda como antes | 2 | Dibujo de PDF simulado (`ImageDraw.Draw`) y modelo simulado. |
| **`modelo-actual.json`**: con modo escribe la identidad y la hora; sin modo no escribe nada | 2 | Carpeta temporal. |
| **`modelo-actual.json` con el modelo ausente**: archivo null y la ruta esperada | 1 | Carpeta temporal. |
| **`modelo-actual.json` se escribe de forma atómica** (sin temporales que sobren); un fallo al escribir no lanza y devuelve falso | 2 | Carpeta temporal; un archivo estorba la ruta para forzar el fallo. |
| **Sin ruta, usa el modelo de `MODELO_CNN`** | 1 | Variable de entorno falsa. |
| **Borrado de reportes del experimento**: borra la carpeta con sus archivos; sin carpeta no falla; si el borrado falla, solo avisa en el log | 3 | Carpeta temporal; `rmtree` simulado para forzar el fallo. |
| **Borrado de filas del experimento** con el cursor de quien llama | 1 | Cursor simulado (`MagicMock`). |
| **Huérfanos de reportes**: borra solo las carpetas de experimentos que ya no existen; sin carpeta no hace nada | 2 | Carpetas temporales; base simulada (`MagicMock`) con un solo experimento existente. |
| **Orden de candados**: se bloquea el experimento (`FOR UPDATE`) antes de tocar el análisis, en marcar error y en la función que bloquea | 2 | Cursor simulado; **compara el texto del SQL**, no ejecuta contra la base. |

**Suma del área: 72** (19 pausa + 10 progreso + 6 diagnóstico + 7 tiempos + 22 modelo + 8 reportes).

### Área 6 · Notificaciones y correo (16 pruebas)

| Caso comprobado | Pruebas | Qué simula |
|---|---|---|
| **Un mensaje por pendiente** con asunto, destinatario y texto; se saltan los pendientes sin correo | 2 | Servidor SMTP de prueba en el mismo proceso (no sale correo real). |
| **Con `SMTP_HOST` vacío no se intenta nada**, ni se registra error | 1 | Cliente SMTP simulado (`smtplib`). |
| **Remitente por omisión** cuando `SMTP_FROM` va vacío | 1 | Servidor SMTP de prueba. |
| **Un correo rechazado no corta a los demás**; sin servidor no lanza y registra los dos intentos | 2 | Servidor SMTP de prueba que rechaza a un destinatario. |
| **Usuario y STARTTLS**: si hay usuario, se inicia sesión; con STARTTLS se pide | 1 | Cliente SMTP simulado. |
| **Cada tipo de notificación tiene su asunto**, y los seis asuntos tienen texto | 2 | Diccionario de asuntos en memoria. |
| **`notificar` deja el correo pendiente** con el asunto de su tipo y hace seis consultas de prueba | 1 | Cursor simulado (`Mock`). |
| **Correo de prueba (`probar_correo`)**: dice que salió, o el motivo del rechazo, o el error de conexión | 3 | Servidor SMTP de prueba (rechazo y conexión). |
| **Correo de prueba con `SMTP_HOST` vacío**: avisa que no hay correo configurado | 1 | Variables de entorno falsas. |
| **Códigos de salida de `probar_correo`**: 0 si salió, 1 si no, 2 sin destinatario | 1 | Servidor SMTP de prueba. |
| **El manejador del log se quita al terminar** | 1 | Logger de prueba. |

**Suma del área: 16.**

### Área 8 · Base de datos (5 pruebas)

| Caso comprobado | Pruebas | Qué simula |
|---|---|---|
| **El respaldo diario crea `fst-AAAAMMDD.dump`** y no deja temporales | 1 | `pg_dump` falso (escribe el archivo que se le pide); carpeta temporal. |
| **Se conservan los 7 respaldos más nuevos** y se quitan los temporales viejos | 1 | `pg_dump` falso; carpeta con respaldos viejos. |
| **Si ya hay un respaldo de hoy, se reemplaza** | 1 | Igual. |
| **Si `pg_dump` falla, no queda respaldo válido** y el log no trae la clave | 1 | `pg_dump` que falla a medias. |
| **Sin `pg_dump`, solo registra el error** | 1 | `FileNotFoundError` simulado. |

**Suma del área: 5.** Ninguna prueba del worker corre contra PostgreSQL real.

**Total de pruebas del worker: 72 + 16 + 5 = 93**. Cuenta por archivo: `test_pausa.py` 19, `test_vigilancia.py` 10, `test_tiempos.py` 7, `test_modelo.py` 22, `test_diagnostico.py` 6, `test_reportes.py` 8, `test_correos.py` 16, `test_respaldo.py` 5. Suma: 19 + 10 + 7 + 22 + 6 + 8 + 16 + 5 = **93**. Cuadra con el conteo de `def test_` de la carpeta `worker/`.

---

## Parte 2 · Prueba de carga con cuatro sesiones

Fuente: `backend-spring/CHECKLIST.md` líneas 240 y 242. El script es `tools/carga_4_sesiones.py` (solo biblioteca estándar). Los números se copian tal como están en el checklist; lo que no aparece ahí se marca **no registrado**. Un dato con «n = 1» trae un solo valor, que aparece en la columna de mediana.

### Corrida A · línea 240 (28-oct, encargo 47)

Qué se simuló: 4 clientes con su propia sesión; 25 rondas sincronizadas; base desechable con 1 experimento de 48 especímenes y 14 400 segundos; rutas: login, lista, detalle, `/status`, `/results`, `/byminute`, `/blocks`, `/groups`, `/report/csv` y `/report/xlsx`. Todas las respuestas fueron 200. **Sin nginx** ni red de por medio. **Sin** `/report/pdf` ni subida de videos. La versión del código no está registrada en la línea.

| Ruta | Peticiones | Mediana (s) | p95 (s) | Máximo (s) |
|---|---|---|---|---|
| login | no registrado | no registrado | no registrado | no registrado |
| lista de experimentos | no registrado | no registrado | no registrado | no registrado |
| detalle | no registrado | no registrado | no registrado | no registrado |
| `/status` | no registrado | no registrado | no registrado | no registrado |
| `/results` | no registrado | no registrado | no registrado | no registrado |
| `/byminute` | no registrado | no registrado | no registrado | no registrado |
| `/blocks` | no registrado | no registrado | 0.086 (la p95 más alta de la corrida) | no registrado |
| `/groups` | no registrado | no registrado | no registrado | no registrado |
| `/report/csv` en frío | no registrado | no registrado | no registrado | 0.155 |
| `/report/xlsx` en frío | no registrado | no registrado | no registrado | 0.229 |
| Toda la sesión | no registrado | no registrado | no registrado | 0.888 (la primera ronda, con la JVM sin calentar) |

### Corrida B · línea 242 (28-oct, encargo 46)

Qué se simuló: 4 cuentas distintas (`carga46-N`), 120 s en bucle; **con nginx** (compose de producción, proyecto `fst-enc46`, puerto 8146); imágenes `enc46` construidas con el código de la **v0.91.1**; 34 experimentos sembrados y uno completo con 2 especímenes de 5 min (`db/pruebas/datos_prueba_pdf.sql`); el worker quieto. Todo por `http://localhost:8146/api/...`.

Totales de la corrida: 1474 peticiones, sin errores, p95 de todas 0.041 s, máximo 0.962 s (la primera generación del XLSX).

| Ruta | Peticiones | Mediana (s) | p95 (s) | Máximo (s) |
|---|---|---|---|---|
| `GET /experiments` | 228 | 0.026 | 0.044 | 0.054 |
| `GET /experiments?estado=` | 110 | 0.026 | 0.042 | 0.046 |
| `GET /experiments?q=` | 95 | 0.026 | 0.045 | 0.050 |
| `GET /experiments/{id}` | 150 | 0.025 | 0.038 | 0.042 |
| `/status` | 216 | 0.024 | 0.035 | 0.041 |
| `/results` | 182 | 0.025 | 0.039 | 0.045 |
| `/results/byminute` | 110 | 0.024 | 0.040 | 0.042 |
| `/results/blocks` | 108 | 0.025 | 0.043 | 0.064 |
| `/notifications` | 158 | 0.022 | 0.036 | 0.047 |
| CSV, primera generación | 1 | 0.063 | no registrado | no registrado |
| CSV, guardado | 39 | 0.029 | 0.051 | 0.054 |
| XLSX, primera generación | 1 | 0.962 | no registrado | no registrado |
| XLSX, guardado | 43 | 0.034 | 0.054 | 0.388 |
| PDF, guardado | 32 | 0.030 | 0.048 | 0.050 |
| PDF, primera generación | no registrado (en esta corrida) | no registrado | no registrado | no registrado |
| Login | no registrado | no registrado | no registrado | no registrado |
| `/groups`, `/report/...` que no están en la lista | no registrado | no registrado | no registrado | no registrado |

Nota del checklist: la primera generación del PDF fue de 0.59 s en una corrida anterior (esa corrida ya encontró el PDF guardado). No está en la tabla porque no es de esta corrida. Con `localhost` en Windows, `urllib` tardaba unos 21 s por petición por probar IPv6 primero; el script usa IPv4.

**Medición de frontend (no es una de las dos corridas):** `frontend/CHECKLIST.md` línea 214. Medida el 20-oct en local: 80 peticiones de 4 sesiones a la vez con un análisis procesando; p95 0.03 s, máximo 0.07 s. Ninguna ruta ni mediana registrada.

---

### Tercera tabla · Lo que la carga NO cubre

| Qué no cubre | Por qué |
|---|---|
| El worker procesando un video real | No se hizo en la corrida B: la PC tiene 15.7 GB y el análisis pide hasta 3.5 GB; el checklist dice que se repetiría el cierre por memoria del encargo 42. En la corrida A no se hizo con worker. |
| La subida de videos | Se excluyó en las dos corridas (A: «sin `/report/pdf` ni la subida de videos»; B: no aparece en las rutas). |
| La primera generación del PDF | No está en la corrida B (solo el PDF guardado); en A se excluyó. La cifra de 0.59 s es de otra corrida. |
| Pruebas sin nginx (A) frente a con nginx (B) | A se hizo sin nginx ni red de por medio; B con nginx. No son comparables punto por punto. |
| Un análisis procesando en la carga de frontend | La línea 214 dice «con un análisis procesando» pero no dice si era un video real ni qué ruta se midió. |

---

## Totales

- **Worker:** 93 pruebas (72 área 3 + 16 área 6 + 5 área 8), conteo estático, sin ejecutar aquí. Cuadra con el conteo de `def test_` por archivo.
- **Carga:** dos corridas en backend (A línea 240, B línea 242) y una medición de frontend (línea 214, fuera de las dos tablas).
- **Lo que no cuadró:** nada en el conteo del worker. En la carga, la corrida A no trae número de peticiones, mediana, p95 ni máximo por ruta; esas celdas van como «no registrado» por instrucción, y la versión del código de A tampoco aparece.
