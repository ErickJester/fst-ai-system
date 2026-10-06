# Para Vanesa (y su Claude): qué se hizo y qué sigue

Respuesta a `PARA_ERICK.md`. Resume lo hecho en el repo desde el 20 de septiembre y cómo seguimos.

Autor: Erick · Fecha: 4 oct 2026 (actualizado el 5 oct, tras tu `PARA_ERICK.md` con la versión 0.3.0)

---

## 1. Lo principal: el backend no se toca hasta que esté el etiquetador

**No vamos a tocar nada del backend (API, BD, worker) hasta que el etiquetador (`tools/fst_labeler`) esté terminado.**

Primero necesitamos videos etiquetados segundo a segundo. Con eso se define y se entrena el clasificador. De lo que pida el clasificador salen los datos que de verdad tiene que guardar y devolver el backend. Si diseñamos ahora los endpoints, lo más probable es que haya que rehacerlos.

En la práctica:

- El frontend sigue con **datos de ejemplo** (`VITE_USE_MOCKS` como está). Nadie lo pone en `false` por ahora.
- El **contrato de API de tu §6 se queda como propuesta**. No se implementa en Flask todavía. Si mientras tanto cambia algo, basta con ajustar `services/*.js` y `services/mocks/data.js`.
- Las **preguntas abiertas de tu §9** quedan anotadas y las contestamos cuando empiece el backend:
  - el campo de la contraseña temporal;
  - restablecer una temporal perdida;
  - la duración de la sesión y el JWT;
  - los nombres de los campos;
  - la ruta de recuperación;
  - qué puede editar el admin de una cuenta.
- También esperan hasta entonces unas mejoras viejas del backend que tengo en una rama local de respaldo:
  - reintento de conexión a la BD;
  - un usuario semilla;
  - un usuario por defecto cuando no se manda `user_id`.

---

## 2. Qué se hizo desde el 20 de septiembre

### Etiquetador `tools/fst_labeler` (20–28 sep, ya en `main`)

Es la herramienta para etiquetar los videos a mano. Se hizo por módulos:

| Módulo | Qué hace |
|---|---|
| 1 | Servidor de cuadros para el etiquetado |
| 2 y 3 | Visor cuadro a cuadro y regiones de interés (un cilindro por región) |
| 4 | Detector de movimiento por umbral con «compuerta de cordura» |
| 5 | Modelo 3D preentrenado (Della Valle et al.) |
| 6 | Reglas geométricas (`reglas_geometricas.py`, `reglas.js`) |
| 7 | Consenso entre capas y cola de discrepancias |
| 8 y 9 | Revisión humana de la cola y exportación final a CSV |
| 28 sep | Subida de videos desde el navegador (`video_source.py`, `subida.js`, `scripts/verificar_subida.py`) |

Se corre con `python tools/fst_labeler/app.py` en el puerto 5055, y también está en `.claude/launch.json`.

### Mockups v2 (28 sep, tuyo, ya en `main`)

`mockup/v2/` es el **diseño final**: lo que manda en lo visual. Los de `mockup/` (v1) y los de LaTeX quedan obsoletos.

### Sistema sin análisis de video (2–3 oct, ya en `main`)

- Se quitó todo lo de visión del sistema:
  - el tracker de YOLO y el contrato de plugin;
  - `weights/`, `runs/`, `dataset/` y `yolov8n.pt`;
  - las dependencias opencv, numpy, pandas y torch.
- El **worker es un placeholder**: no toma trabajos y los videos se quedan en `QUEUED`.
- La BD guarda `pipeline_name` y `pipeline_version`.
- Los resultados se guardan en `behavior_segments`, con etiqueta libre.

### Frontend v2 (3–4 oct, PR #2, ya en `main`)

- Todas las pantallas de `mockup/v2` (2a–2k) en React, con los estilos `modernist.css`, `fst.css` y `app.css`.
- Solo interfaz, con datos de ejemplo.
- Arriba aparece la franja roja «MODO DEMO · vX.Y.Z», que toma la versión de `frontend/package.json`.

### Docker (3–4 oct, ya en `main`)

`docker compose up -d` levanta 4 servicios: `db` (Postgres, puerto 5432), `api` (Flask, 8000), `worker` (placeholder) y `frontend` (Vite, 5173).

- Se agregó `.dockerignore` en `frontend/` y en `backend/`. Sin él, el `node_modules` de Windows rompía Vite dentro del contenedor.
- Se quitó la línea obsoleta `version:` del compose.
- Si tienes `npm run dev` abierto en tu máquina, ocupa el 5173 y el contenedor del frontend no puede publicar ese puerto.

### Tu trabajo (3–4 oct, en tus ramas)

- `feat/frontend-v2-fase0`: queda superada por `feat/frontend-v2-servicios`.
- `feat/frontend-v2-servicios` (29 commits sobre `main`, v0.3.0): me parece bien que sea la base del frontend. Lo nuevo del 5 oct que ya vi:
  - datos de ejemplo persistentes y «Reiniciar datos»;
  - en Admin, editar cuenta, restablecer la temporal y reactivar;
  - pruebas con Vitest y revisión con ESLint;
  - `ErrorBoundary`, rutas inexistentes y login con sesión abierta;
  - sugerencias de grupo con el teclado, especie y notas en Experimento, enlaces a la tanda correcta en Resultados.

### Mi rama `feat/sin-codigos-cilindro` (4 oct, v0.2.2, ya en GitHub, sin PR)

- Quita de la interfaz y de los mockups los **códigos de trazabilidad** (RN-06, T-06, RF-08, RF-31). Esos códigos son para el documento, no para el usuario.
- **Nombra a cada sujeto por su cilindro**, «Cilindro P1».

Esto choca con tu `f252244`, que pone «Espécimen 1 · Cilindro P1». Vi que en tu §12 propones quedarte con ese texto; en la sección 3 explico por qué prefiero «Cilindro P1».

Sobre el orden, de acuerdo con tu §12:

1. Tu rama `feat/frontend-v2-servicios` entra primero a `main` por PR.
2. Yo rehago `feat/sin-codigos-cilindro` encima del `main` nuevo y la subo a **0.3.1**.
3. Quitar los códigos va de todos modos. El nombre de los sujetos lo cerramos antes de rehacerla.

---

## 3. Nombre de los sujetos: propongo «Cilindro P1»

Lo que nos importa son las tres conductas (nado activo, inmovilidad, escalamiento) **por grupo**: la media ± DE de Control, Referencia y Experimental. Para eso basta con saber qué pasó en cada cilindro; no necesitamos saber qué espécimen estaba dentro.

- Un espécimen **no cambia de cilindro** durante la prueba.
- **El Día 1 es de habituación** (confirmado). Lo que se analiza es el Día 2, así que nunca hay que emparejar al mismo espécimen entre días.
- El «1» de «Espécimen 1» lo pone el sistema: nadie lo captura y no corresponde al ID del laboratorio. Mostrarlo da a entender que llevamos el control de cada espécimen, y no es así.

Lo que **sí se conserva** es el dato por cilindro: cada cilindro es un punto de la muestra y con esos valores se calculan la media y la DE.

Detalle a cuidar: la tanda A y la tanda B tienen las dos un «Cilindro P1» y son especímenes distintos. Donde se junten varias tandas (vista por grupo, un CSV combinado) la etiqueta debe ser **«Tanda A · Cilindro P1»**. Dentro de una sola tanda basta con «Cilindro P1».

Si algún día un investigador necesita el ID de laboratorio de cada espécimen, se agrega como campo opcional por cilindro, sin cambiar el modelo.

¿Te parece? Si estás de acuerdo, al rehacer mi rama gana «Cilindro PN» en Experimento, Grupo, Resultados, el CSV y los datos de ejemplo.

### Pregunta abierta: ¿qué hacemos con los videos del Día 1?

Si el Día 1 es solo habituación, hoy la interfaz deja subirlo y analizarlo igual que el Día 2 (Cargar video con `DAY1`/`DAY2`, Resultados con `?day=`). Opciones:

- **Quitarlo**: solo se sube y analiza el Día 2. Interfaz más simple y la mitad de videos en la cola.
- **Guardarlo sin analizar**: se sube como registro, pero no entra a la cola.
- **Dejarlo como está**, por si alguien quiere medir la habituación.

Todavía no lo decidimos; no cambies nada de esto hasta hablarlo.

---

## 4. Sobre tus reglas del equipo

De acuerdo con todas: no tocar `main` directo, todo en español, nada de «ratas», «animal» ni «IA» en la interfaz, mockups v2 como referencia y avisar antes de un push o un PR.

Un dato: el merge del PR #2 en GitHub aparece como «Angel Frausto Robles». Es mi cuenta (`ErickJester`).

---

## 5. Versiones

- La versión vive en `frontend/package.json` y se ve en la franja de MODO DEMO.
- Usamos **0.x mientras no exista el clasificador**. La **1.0.0** será cuando el clasificador esté integrado y el sistema haga lo que promete la tesis.
- Hoy: 0.2.1 en `main`, 0.3.0 en tu rama y 0.2.2 en la mía. Cuando rehaga la mía encima de la tuya, queda en 0.3.1.

---

## 6. Orden de trabajo

1. **Terminar el etiquetador** y etiquetar videos.
2. Definir y entrenar el clasificador con esos datos.
3. Con eso, cerrar el contrato de API (tu §6 y §9) e **implementar el backend**.
4. Conectar el frontend (`VITE_USE_MOCKS=false`) y probar pantalla por pantalla.
5. Integrar el clasificador al worker → **1.0.0**.

Mientras tanto, en el frontend solo hay cambios de interfaz con datos de ejemplo.
