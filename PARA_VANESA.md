# Para Vanesa (y su Claude): qué se hizo y qué sigue

Respuesta a `PARA_ERICK.md`. Resume lo hecho en el repo desde el 20 de septiembre y cómo seguimos.

Autor: Erick · Fecha: 4 oct 2026

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
- `feat/frontend-v2-servicios` (17 commits): ya la revisé, incluido tu `PARA_ERICK.md`. Me parece bien que sea la base del frontend.

### Rama mía sin subir: `feat/sin-codigos-cilindro` (4 oct, v0.2.2)

- Quita de la interfaz y de los mockups los **códigos de trazabilidad** (RN-06, T-06, RF-08, RF-31). Esos códigos son para el documento, no para el usuario.
- **Nombra a cada sujeto por su cilindro**, «Cilindro P1».

**Esto choca con tu `f252244`**, que pone «Espécimen 1 · Cilindro P1». Tocamos los mismos archivos: Experimento, Grupo, Resultados, el CSV y los datos de ejemplo. Propongo:

1. Hablar entre los dos qué nombre se queda.
2. Que tu rama `feat/frontend-v2-servicios` entre primero, porque es la más grande.
3. Rehacer mi rama encima de la tuya con el nombre que acordemos. Quitar los códigos no lo discutimos: va de todos modos.

Todavía no la subo, porque tu regla 5 pide avisar antes.

---

## 3. Sobre tus reglas del equipo

De acuerdo con todas: no tocar `main` directo, todo en español, nada de «ratas», «animal» ni «IA» en la interfaz, mockups v2 como referencia y avisar antes de un push o un PR.

Un dato: el merge del PR #2 en GitHub aparece como «Angel Frausto Robles». Es mi cuenta (`ErickJester`).

---

## 4. Versiones

- La versión vive en `frontend/package.json` y se ve en la franja de MODO DEMO.
- Usamos **0.x mientras no exista el clasificador**. La **1.0.0** será cuando el clasificador esté integrado y el sistema haga lo que promete la tesis.
- Hoy: 0.2.1 en `main` y 0.2.2 en mi rama sin subir. Cuando tu rama entre, súbele la versión, por ejemplo a 0.3.0.

---

## 5. Orden de trabajo

1. **Terminar el etiquetador** y etiquetar videos.
2. Definir y entrenar el clasificador con esos datos.
3. Con eso, cerrar el contrato de API (tu §6 y §9) e **implementar el backend**.
4. Conectar el frontend (`VITE_USE_MOCKS=false`) y probar pantalla por pantalla.
5. Integrar el clasificador al worker → **1.0.0**.

Mientras tanto, en el frontend solo hay cambios de interfaz con datos de ejemplo.
