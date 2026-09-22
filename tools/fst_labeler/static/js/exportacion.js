/* Suavizado temporal y exportación final a CSV (Módulo 9).

   Esta página no mide nada ni corre ninguna capa: lee lo que ya existe en la
   base del Módulo 8 (bloques aceptados por el consenso y clips decididos a
   mano), aplica el voto mayoritario y ofrece el CSV resultante.

   Todo lo que se ve aquí sale de peticiones GET sin efecto, así que cerrar
   la pestaña y volver a abrirla —o correr `python app.py` de nuevo mañana—
   muestra exactamente el mismo estado: lo que haya en la base sqlite en ese
   momento, ni un decisión de más ni de menos. */

import { COLOR_CLASE } from "./modelo.js";

const el = (id) => document.getElementById(id);

const nodos = {
  version: el("version"),
  aviso: el("aviso"),
  filasVideos: el("filas-videos"),
  resumenGlobal: el("resumen-global"),
  ventana: el("ventana"),
  selectorVideo: el("selector-video-export"),
  btnVistaPrevia: el("btn-vista-previa"),
  enlaceCsv: el("enlace-csv"),
  estadoExportacion: el("estado-exportacion"),
  resumenSuavizado: el("resumen-suavizado"),
  tablaCambios: el("tabla-cambios"),
  filasCambios: el("filas-cambios"),
  sinCambios: el("sin-cambios"),
};

let ultimoResumen = null;
let ocupado = false;

async function arrancar() {
  try {
    const indice = await fetch("/api/indice").then((r) => r.json());
    nodos.version.textContent = "v" + indice.version;
  } catch (e) {
    // Informativa: sin ella la pagina sigue siendo usable.
  }
  conectarControles();
  await cargarResumen();
  actualizarEnlaceCsv();
}

function conectarControles() {
  nodos.ventana.addEventListener("change", () => {
    if (!ventanaValida()) return;
    cargarResumen();
    actualizarEnlaceCsv();
  });
  nodos.selectorVideo.addEventListener("change", actualizarEnlaceCsv);
  nodos.btnVistaPrevia.addEventListener("click", vistaPrevia);
}

function ventanaValida() {
  const v = Number(nodos.ventana.value);
  return Number.isInteger(v) && v >= 1 && v % 2 === 1;
}

function avisar(texto) {
  nodos.aviso.classList.remove("oculto");
  nodos.aviso.classList.add("error");
  nodos.aviso.textContent = texto;
}

/* ------------------------------------------------------------- resumen */

async function cargarResumen() {
  if (!ventanaValida()) {
    avisar("La ventana de suavizado debe ser un número impar de al menos 1.");
    return;
  }
  try {
    const resp = await fetch("/api/exportacion/resumen?ventana=" + nodos.ventana.value);
    const datos = await resp.json().catch(() => null);
    if (!resp.ok) throw new Error((datos && datos.error) || "HTTP " + resp.status);
    ultimoResumen = datos;
  } catch (e) {
    avisar("No se pudo leer el resumen de exportación: " + e.message);
    return;
  }
  dibujarResumen();
  llenarSelectorVideos();
}

function dibujarResumen() {
  const r = ultimoResumen;
  const videos = Object.keys(r.por_video).sort();
  nodos.filasVideos.innerHTML = "";
  if (!videos.length) {
    nodos.filasVideos.innerHTML =
      '<tr><td colspan="7">Ningún video tiene todavía una corrida enviada a revisión.</td></tr>';
  }
  for (const nombre of videos) {
    const v = r.por_video[nombre];
    const fila = document.createElement("tr");
    fila.innerHTML =
      "<td>" + nombre + "</td>" +
      "<td>" + v.bloques + "</td>" +
      "<td>" + v.exportados + "</td>" +
      "<td>" + v.pendientes + "</td>" +
      "<td>" + v.descartados + "</td>" +
      "<td>" + v.sin_datos + "</td>" +
      '<td><span class="sello ' + (v.listo ? "bueno" : "malo") + '">' +
      (v.listo ? "listo" : v.pendientes + " pendientes") + "</span></td>";
    nodos.filasVideos.appendChild(fila);
  }
  const totalVideos = videos.length;
  const listos = videos.filter((n) => r.por_video[n].listo).length;
  nodos.resumenGlobal.textContent =
    r.exportados + " bloques exportables en total, " + r.pendientes +
    " pendientes de revisión, " + r.descartados + " descartados, " +
    r.sin_datos + " sin datos suficientes. " + listos + " de " + totalVideos +
    " videos están completamente listos.";
}

function llenarSelectorVideos() {
  const anterior = nodos.selectorVideo.value;
  const videos = Object.keys(ultimoResumen.por_video).sort();
  nodos.selectorVideo.innerHTML =
    '<option value="">— todos los videos listos —</option>';
  for (const nombre of videos) {
    const o = document.createElement("option");
    o.value = nombre;
    const v = ultimoResumen.por_video[nombre];
    o.textContent = nombre + (v.listo ? "" : " (incompleto: " + v.pendientes + " pendientes)");
    nodos.selectorVideo.appendChild(o);
  }
  if (videos.includes(anterior)) nodos.selectorVideo.value = anterior;
}

function actualizarEnlaceCsv() {
  const video = nodos.selectorVideo.value;
  const ventana = ventanaValida() ? nodos.ventana.value : 3;
  const parametros = new URLSearchParams();
  if (video) parametros.set("video", video);
  parametros.set("ventana", ventana);
  nodos.enlaceCsv.href = "/api/exportacion/csv?" + parametros.toString();
}

/* ------------------------------------------------------- vista previa */

async function vistaPrevia() {
  if (!ventanaValida()) {
    avisar("La ventana de suavizado debe ser un número impar de al menos 1.");
    return;
  }
  ocupado = true;
  nodos.btnVistaPrevia.disabled = true;
  nodos.estadoExportacion.className = "estado-deteccion";
  nodos.estadoExportacion.textContent = "Calculando el suavizado…";
  actualizarEnlaceCsv();

  const video = nodos.selectorVideo.value;
  const parametros = new URLSearchParams({ ventana: nodos.ventana.value });
  if (video) parametros.set("video", video);

  try {
    const resp = await fetch("/api/exportacion/vista-previa?" + parametros.toString());
    const datos = await resp.json().catch(() => null);
    if (!resp.ok) throw new Error((datos && datos.error) || "HTTP " + resp.status);
    dibujarVistaPrevia(datos);
    nodos.estadoExportacion.textContent =
      datos.filas.length + " filas listas para exportar" +
      (video ? " de " + video : " en total") + ".";
  } catch (e) {
    nodos.estadoExportacion.className = "estado-deteccion error";
    nodos.estadoExportacion.textContent = "No se pudo calcular la vista previa: " + e.message;
  } finally {
    ocupado = false;
    nodos.btnVistaPrevia.disabled = false;
  }
}

function dibujarVistaPrevia({ filas, resumen }) {
  const r = resumen;
  nodos.resumenSuavizado.innerHTML = "";
  const caja = document.createElement("div");
  caja.className = "resumen-deteccion";
  const p = document.createElement("p");
  p.innerHTML =
    "<strong>" + r.cambios_por_suavizado + "</strong> bloques cambiaron de clase por el " +
    "suavizado, sobre " + r.exportados + " exportables. " +
    (r.empates_suavizado
      ? r.empates_suavizado + " quedaron empatados en su ventana y no se tocaron."
      : "Ningún empate en esta corrida.");
  caja.appendChild(p);
  nodos.resumenSuavizado.appendChild(caja);

  const cambiadas = filas.filter((f) => f.suavizado);
  nodos.tablaCambios.classList.toggle("oculto", cambiadas.length === 0);
  nodos.sinCambios.classList.toggle("oculto", cambiadas.length > 0);
  nodos.filasCambios.innerHTML = "";
  for (const f of cambiadas) {
    const fila = document.createElement("tr");
    fila.innerHTML =
      "<td>" + f.video + "</td>" +
      "<td>" + f.region + "</td>" +
      "<td>" + f.bloque + "</td>" +
      "<td>" + f.segundo_inicio.toFixed(1) + "–" + f.segundo_fin.toFixed(1) + " s</td>" +
      '<td><span style="color:' + (COLOR_CLASE[f.clase_antes_de_suavizar] || "") + '">' +
      f.clase_antes_de_suavizar + "</span></td>" +
      '<td><span style="color:' + (COLOR_CLASE[f.clase] || "") + '">' + f.clase + "</span></td>";
    nodos.filasCambios.appendChild(fila);
  }
}

arrancar();
