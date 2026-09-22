/* Revisión humana de la cola de discrepancias (Módulo 8).

   Un clip a la vez: el visor del Módulo 2 limitado al tramo del clip, que se
   repite en bucle, y el editor del Módulo 3 con la región de ese clip tal
   como la usaron las capas. Al lado, lo que dijo cada capa y la decisión.

   Tres cosas que esta página no hace, a propósito:

   - No preselecciona ninguna conducta. El consenso no propone clase para los
     clips en discrepancia, y marcar la de alguna capa convertiría la revisión
     en confirmar al sistema.
   - No decide si la geometría se corrigió. Manda la que hay en pantalla y el
     servidor la compara contra la de la corrida; lo que se ve aquí es solo
     un aviso inmediato para poder deshacer un arrastre accidental.
   - No deja agregar ni borrar regiones: el clip es de un solo espécimen. Se
     pueden mover las esquinas y redibujar la línea de agua. */

import { ClienteFST } from "./api.js";
import { Visor } from "./visor.js";
import { EditorRegiones, MODO } from "./dibujo.js";
import { COLOR_CLASE } from "./modelo.js";

// Orden de la rúbrica del laboratorio; el número es el atajo de teclado.
const ETIQUETAS = [
  { clase: "escalamiento", nombre: "Escalamiento", tecla: "1" },
  { clase: "nado", nombre: "Nado", tecla: "2" },
  { clase: "inmovilidad", nombre: "Inmovilidad", tecla: "3" },
];

// La misma que TOLERANCIA_PX del servidor: solo absorbe el ruido de coma
// flotante. Cualquier arrastre real cuenta como corrección.
const TOLERANCIA_PX = 0.01;

const CLAVE_A_CIEGAS = "fst-revision-a-ciegas";

const el = (id) => document.getElementById(id);

const nodos = {
  version: el("version"),
  progreso: el("progreso"),
  aviso: el("aviso"),
  tituloClip: el("titulo-clip"),

  cuadro: el("cuadro"),
  capaDibujo: el("capa-dibujo"),
  capaVacia: el("capa-vacia"),
  capaCargando: el("capa-cargando"),
  pistaModo: el("pista-modo"),

  barra: el("barra"),
  btnInicioClip: el("btn-inicio-clip"),
  btnRetrocederSalto: el("btn-retroceder-salto"),
  btnAnterior: el("btn-anterior"),
  btnReproducir: el("btn-reproducir"),
  btnSiguiente: el("btn-siguiente"),
  btnAvanzarSalto: el("btn-avanzar-salto"),
  btnFinClip: el("btn-fin-clip"),
  velocidad: el("velocidad"),
  indicadorCuadro: el("indicador-cuadro"),
  indicadorClip: el("indicador-clip"),
  indicadorEstado: el("indicador-estado"),

  filtroVideo: el("filtro-video"),
  filtroMotivo: el("filtro-motivo"),
  filtroPendientes: el("filtro-pendientes"),
  posicionCola: el("posicion-cola"),
  btnClipAnterior: el("btn-clip-anterior"),
  btnClipSiguiente: el("btn-clip-siguiente"),

  opciones: el("opciones"),
  btnDescartar: el("btn-descartar"),
  notas: el("notas"),
  btnGuardar: el("btn-guardar"),
  estadoDecision: el("estado-decision"),

  aCiegas: el("a-ciegas"),
  capasOcultas: el("capas-ocultas"),
  capas: el("capas"),

  estadoGeometria: el("estado-geometria"),
  notaCamara: el("nota-camara"),
  btnLineaAgua: el("btn-linea-agua"),
  btnDeshacer: el("btn-deshacer"),
  btnRestaurar: el("btn-restaurar"),

  datosClip: el("datos-clip"),
};

const estado = {
  cola: null,          // respuesta de /api/revision/cola
  filtrados: [],       // clips que pasan los filtros, en orden estable
  indice: -1,          // posición del clip abierto dentro de `filtrados`
  decision: null,      // { etiqueta, descartado } elegida y aún sin guardar
  vioCapas: false,     // si las predicciones se mostraron con este clip abierto
  guardando: false,
  mensaje: null,
  error: null,
  salto: 10,
};

const cliente = new ClienteFST();
const visor = new Visor(cliente, nodos.cuadro, { alCambiar: alCambiarVisor });
const editor = new EditorRegiones(nodos.capaDibujo, nodos.cuadro, {
  alCambiar: dibujarGeometria,
});

/* ------------------------------------------------------------- arranque */

async function arrancar() {
  try {
    const indice = await fetch("/api/indice").then((r) => r.json());
    nodos.version.textContent = "v" + indice.version;
  } catch (e) {
    // Informativa: sin ella la página sigue siendo usable.
  }
  try {
    const prefs = await cliente.preferencias();
    visor.fijarTransporte({ calidad: prefs.calidad, maxAncho: prefs.max_ancho });
    visor.prefetch = prefs.prefetch;
    estado.salto = prefs.salto_cuadros;
  } catch (e) {
    avisar("No se pudieron leer las preferencias del servidor: " + e.message);
  }
  try {
    nodos.aCiegas.checked = localStorage.getItem(CLAVE_A_CIEGAS) === "1";
  } catch (e) {
    // Sin almacenamiento local la casilla empieza desmarcada.
  }

  construirOpciones();
  conectarControles();
  await cargarCola();
}

async function cargarCola() {
  try {
    const resp = await fetch("/api/revision/cola");
    const datos = await resp.json().catch(() => null);
    if (!resp.ok) throw new Error((datos && datos.error) || "HTTP " + resp.status);
    estado.cola = datos;
  } catch (e) {
    avisar("No se pudo leer la cola de revisión: " + e.message);
    nodos.capaVacia.textContent = "No se pudo leer la cola.";
    return;
  }
  llenarFiltros();
  await aplicarFiltros();
}

function llenarFiltros() {
  const videos = [...new Set(estado.cola.clips.map((k) => k.video))];
  opcionesDe(nodos.filtroVideo, [["", "todos (" + videos.length + ")"],
    ...videos.map((v) => [v, v])]);
  const conteo = {};
  for (const k of estado.cola.clips) {
    for (const m of k.motivos) conteo[m] = (conteo[m] || 0) + 1;
  }
  opcionesDe(nodos.filtroMotivo, [["", "todos"],
    ...estado.cola.motivos.filter((m) => conteo[m]).map((m) => [m, m + " (" + conteo[m] + ")"])]);
}

function opcionesDe(select, pares) {
  select.innerHTML = "";
  for (const [valor, texto] of pares) {
    const o = document.createElement("option");
    o.value = valor;
    o.textContent = texto;
    select.appendChild(o);
  }
}

/* La lista filtrada solo se recalcula al tocar un filtro, no al guardar: si
   el clip recién decidido desapareciera de la lista, las posiciones se
   correrían bajo los pies de quien revisa. */
async function aplicarFiltros() {
  const video = nodos.filtroVideo.value;
  const motivo = nodos.filtroMotivo.value;
  const pendientes = nodos.filtroPendientes.checked;
  const actual = clipActual();
  estado.filtrados = estado.cola.clips.filter((k) =>
    (!video || k.video === video) &&
    (!motivo || k.motivos.includes(motivo)) &&
    (!pendientes || !k.decision)
  );
  let destino = actual ? estado.filtrados.indexOf(actual) : -1;
  if (destino < 0) destino = estado.filtrados.length ? 0 : -1;
  if (destino < 0) {
    estado.indice = -1;
    visor.pausar();
    nodos.capaVacia.textContent = estado.cola.total
      ? "Ningún clip cumple los filtros."
      : "No hay clips en la cola. Manda un consenso desde el análisis con «Enviar a revisión».";
    dibujarTodo();
    return;
  }
  await abrirClip(destino);
}

/* ------------------------------------------------------------ clip abierto */

function clipActual() {
  return estado.indice >= 0 ? estado.filtrados[estado.indice] : null;
}

function corridaDe(clip) {
  return estado.cola.corridas[clip.corrida_id];
}

function regionOriginal(clip) {
  return corridaDe(clip).regiones[clip.region - 1];
}

async function abrirClip(indice) {
  const clip = estado.filtrados[indice];
  if (!clip) return;
  visor.pausar();
  estado.indice = indice;
  estado.error = null;
  estado.vioCapas = false;

  // Si ya tiene decisión se muestra tal como se guardó, geometría incluida,
  // para poder corregirla. Si no, empieza vacía: nada preseleccionado.
  const d = clip.decision;
  estado.decision = d ? { etiqueta: d.etiqueta_humana, descartado: d.descartado } : null;
  nodos.notas.value = d && d.notas ? d.notas : "";

  const original = regionOriginal(clip);
  const region = {
    esquinas: d && d.roi_corregida ? d.esquinas_nuevas : original.esquinas,
    lineaAgua: d && d.linea_agua_corregida ? d.linea_agua_nueva : original.lineaAgua,
  };
  editor.numeroDe = () => clip.region;
  editor.fijarRegiones(clip.video, [region]);
  visor.rango = { inicio: clip.cuadro_inicio, fin: clip.cuadro_fin };

  dibujarTodo();
  try {
    if (visor.videoId !== clip.video) {
      await visor.abrirVideo(clip.video);
      // Sin FPS confiables en el archivo, se usan los de la corrida: con
      // ellos se trazaron las fronteras del bloque.
      const fps = corridaDe(clip).fps;
      if (!visor.fps && fps) visor.fijarFpsManual(fps);
    }
    await visor.irA(clip.cuadro_inicio);
  } catch (e) {
    avisar("No se pudo abrir " + clip.video + ": " + e.message);
  }
  dibujarTodo();
}

/* ---------------------------------------------------------------- decidir */

function elegir(clase) {
  if (!clipActual()) return;
  estado.decision = { etiqueta: clase, descartado: false };
  estado.mensaje = null;
  dibujarDecision();
}

function descartar() {
  if (!clipActual()) return;
  estado.decision = { etiqueta: null, descartado: true };
  estado.mensaje = null;
  dibujarDecision();
}

async function guardarYSiguiente() {
  const clip = clipActual();
  if (!clip || !estado.decision || estado.guardando) return;
  const region = editor.regiones[0];
  estado.guardando = true;
  estado.error = null;
  dibujarDecision();
  try {
    const resp = await fetch("/api/revision/decisiones", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        clip_id: clip.clip_id,
        corrida_id: clip.corrida_id,
        etiqueta: estado.decision.descartado ? null : estado.decision.etiqueta,
        descartado: estado.decision.descartado,
        esquinas: region.esquinas,
        linea_agua: region.lineaAgua,
        notas: nodos.notas.value,
        a_ciegas: !estado.vioCapas,
      }),
    });
    const datos = await resp.json().catch(() => null);
    if (!resp.ok) throw new Error((datos && datos.error) || "HTTP " + resp.status);
    clip.decision = datos.decision;
    estado.mensaje = "Guardado: " + describirClip(clip) + " → " + textoDecision(datos.decision) +
      (datos.decision.roi_corregida ? ", región corregida" : "") +
      (datos.decision.linea_agua_corregida ? ", línea de agua corregida" : "") + ".";
  } catch (e) {
    estado.error = "No se guardó: " + e.message;
    return;
  } finally {
    estado.guardando = false;
    dibujarTodo();
  }

  const siguiente = siguientePendiente(estado.indice);
  if (siguiente === null) {
    estado.mensaje += " No quedan clips pendientes en esta vista.";
    dibujarTodo();
    return;
  }
  const mensaje = estado.mensaje;
  await abrirClip(siguiente);
  estado.mensaje = mensaje;
  dibujarDecision();
}

/* El siguiente pendiente después de `desde`, dando la vuelta a la lista. */
function siguientePendiente(desde) {
  const n = estado.filtrados.length;
  for (let paso = 1; paso <= n; paso += 1) {
    const i = (desde + paso) % n;
    if (!estado.filtrados[i].decision) return i;
  }
  return null;
}

/* ------------------------------------------------------------ controles */

function construirOpciones() {
  for (const e of ETIQUETAS) {
    const b = document.createElement("button");
    b.type = "button";
    b.className = "opcion";
    b.dataset.clase = e.clase;
    b.style.setProperty("--color-opcion", COLOR_CLASE[e.clase]);
    b.innerHTML = e.nombre + ' <span class="tecla">' + e.tecla + "</span>";
    b.addEventListener("click", () => {
      b.blur();
      elegir(e.clase);
    });
    nodos.opciones.appendChild(b);
  }
}

function conectarControles() {
  const sinFoco = (fn) => (evento) => {
    evento.currentTarget.blur();   // para que Enter y espacio sigan siendo atajos
    fn();
  };
  nodos.btnInicioClip.addEventListener("click", sinFoco(() => irAlCuadro(clipActual().cuadro_inicio)));
  nodos.btnFinClip.addEventListener("click", sinFoco(() => irAlCuadro(clipActual().cuadro_fin)));
  nodos.btnAnterior.addEventListener("click", sinFoco(() => visor.avanzar(-1)));
  nodos.btnSiguiente.addEventListener("click", sinFoco(() => visor.avanzar(1)));
  nodos.btnRetrocederSalto.addEventListener("click", sinFoco(() => visor.avanzar(-estado.salto)));
  nodos.btnAvanzarSalto.addEventListener("click", sinFoco(() => visor.avanzar(estado.salto)));
  nodos.btnReproducir.addEventListener("click", sinFoco(() => visor.alternarReproduccion()));
  nodos.barra.addEventListener("input", () => irAlCuadro(Number(nodos.barra.value)));
  nodos.velocidad.addEventListener("change", () => {
    visor.fijarVelocidad(nodos.velocidad.value);
    nodos.velocidad.blur();
  });

  for (const filtro of [nodos.filtroVideo, nodos.filtroMotivo, nodos.filtroPendientes]) {
    filtro.addEventListener("change", () => {
      filtro.blur();
      aplicarFiltros();
    });
  }
  nodos.btnClipAnterior.addEventListener("click", sinFoco(() => abrirClip(estado.indice - 1)));
  nodos.btnClipSiguiente.addEventListener("click", sinFoco(() => abrirClip(estado.indice + 1)));

  nodos.btnDescartar.addEventListener("click", sinFoco(descartar));
  nodos.btnGuardar.addEventListener("click", sinFoco(guardarYSiguiente));

  nodos.aCiegas.addEventListener("change", () => {
    nodos.aCiegas.blur();
    try {
      localStorage.setItem(CLAVE_A_CIEGAS, nodos.aCiegas.checked ? "1" : "0");
    } catch (e) {
      // Sin almacenamiento local la preferencia no se recuerda; no importa.
    }
    dibujarCapas();
  });

  nodos.btnLineaAgua.addEventListener("click", sinFoco(() => editor.nuevaLineaDeAgua()));
  nodos.btnDeshacer.addEventListener("click", sinFoco(() => editor.deshacer()));
  nodos.btnRestaurar.addEventListener("click", sinFoco(() => {
    const clip = clipActual();
    if (clip) editor.fijarRegiones(clip.video, [regionOriginal(clip)]);
  }));

  nodos.cuadro.addEventListener("load", () => editor.redibujar());
  if (window.ResizeObserver) {
    new ResizeObserver(() => editor.redibujar()).observe(nodos.cuadro);
  } else {
    window.addEventListener("resize", () => editor.redibujar());
  }

  document.addEventListener("keydown", atajos);
}

function irAlCuadro(cuadro) {
  visor.pausar();
  visor.irA(cuadro);
}

function atajos(evento) {
  const destino = evento.target;
  if (destino && /^(INPUT|SELECT|TEXTAREA)$/.test(destino.tagName)) {
    // Esc sale del campo de notas y devuelve el teclado a los atajos.
    if (evento.key === "Escape") destino.blur();
    return;
  }
  if (evento.ctrlKey || evento.altKey || evento.metaKey) return;
  const clip = clipActual();
  if (!clip) return;

  switch (evento.key) {
    case "1":
    case "2":
    case "3":
      elegir(ETIQUETAS[Number(evento.key) - 1].clase);
      break;
    case "d":
    case "D":
      descartar();
      break;
    case "Enter":
      guardarYSiguiente();
      break;
    case "ArrowLeft":
      visor.avanzar(evento.shiftKey ? -estado.salto : -1);
      break;
    case "ArrowRight":
      visor.avanzar(evento.shiftKey ? estado.salto : 1);
      break;
    case " ":
      visor.alternarReproduccion();
      break;
    case "Home":
      irAlCuadro(clip.cuadro_inicio);
      break;
    case "End":
      irAlCuadro(clip.cuadro_fin);
      break;
    case "w":
    case "W":
      editor.nuevaLineaDeAgua();
      break;
    case "z":
    case "Z":
      editor.deshacer();
      break;
    case "Escape":
      editor.cancelar();
      break;
    default:
      return;
  }
  evento.preventDefault();
}

/* ---------------------------------------------------------------- dibujo */

function alCambiarVisor(v) {
  editor.sincronizar(v.videoId, v.meta);
  dibujarTransporte();
}

function dibujarTodo() {
  dibujarProgreso();
  dibujarCola();
  dibujarDecision();
  dibujarCapas();
  dibujarGeometria();
  dibujarDatosClip();
  dibujarTransporte();
}

function dibujarProgreso() {
  const c = estado.cola;
  if (!c) return;
  const decididos = c.clips.filter((k) => k.decision).length;
  nodos.progreso.textContent = decididos + " de " + c.clips.length +
    " clips de la cola decididos";
}

function dibujarCola() {
  const clip = clipActual();
  const n = estado.filtrados.length;
  nodos.btnClipAnterior.disabled = !clip || estado.indice <= 0;
  nodos.btnClipSiguiente.disabled = !clip || estado.indice >= n - 1;
  nodos.posicionCola.textContent = clip
    ? "Clip " + (estado.indice + 1) + " de " + n + " en esta vista" +
      (clip.decision ? " · ya decidido" : " · pendiente")
    : n ? "—" : "Ningún clip en esta vista.";
  nodos.tituloClip.textContent = clip ? describirClip(clip) : "—";
}

function describirClip(clip) {
  return "Región " + clip.region + " · bloque " + clip.bloque + " · " +
    clip.segundo_inicio.toFixed(1) + "–" + clip.segundo_fin.toFixed(1) + " s · " +
    clip.video;
}

function textoDecision(d) {
  return d.descartado ? "descartado" : d.etiqueta_humana;
}

function dibujarDecision() {
  const clip = clipActual();
  const elegida = estado.decision;
  for (const b of nodos.opciones.children) {
    b.disabled = !clip || estado.guardando;
    b.classList.toggle("elegida",
      Boolean(elegida && !elegida.descartado && elegida.etiqueta === b.dataset.clase));
  }
  nodos.btnDescartar.disabled = !clip || estado.guardando;
  nodos.btnDescartar.classList.toggle("elegida", Boolean(elegida && elegida.descartado));
  nodos.notas.disabled = !clip;
  // Guardar exige una decisión explícita: no hay opción por omisión.
  nodos.btnGuardar.disabled = !clip || !elegida || estado.guardando;
  nodos.btnGuardar.firstChild.textContent = estado.guardando ? "Guardando… " : "Guardar y siguiente ";

  const e = nodos.estadoDecision;
  e.className = "estado-deteccion" + (estado.error ? " error" : "");
  if (estado.error) e.textContent = estado.error;
  else if (estado.mensaje) e.textContent = estado.mensaje;
  else if (!clip) e.textContent = "—";
  else if (!elegida) e.textContent = "Elige una conducta (1, 2, 3) o descarta el clip (D).";
  else e.textContent = "Elegido: " + (elegida.descartado ? "descartar" : elegida.etiqueta) +
    ". Enter guarda y pasa al siguiente pendiente.";
}

function dibujarDatosClip() {
  const clip = clipActual();
  nodos.datosClip.innerHTML = "";
  if (!clip) return;
  const corrida = corridaDe(clip);
  const filas = [
    ["Video", clip.video],
    ["Región", String(clip.region)],
    ["Bloque", String(clip.bloque)],
    ["Tramo", clip.segundo_inicio.toFixed(2) + "–" + clip.segundo_fin.toFixed(2) + " s"],
    ["Cuadros", clip.cuadro_inicio + "–" + clip.cuadro_fin],
    ["Corrida", corrida.id + " · " + corrida.creada.replace("T", " ")],
    ["Identificador", clip.clip_id],
  ];
  if (clip.decision) {
    filas.push(["Decisión guardada", textoDecision(clip.decision) +
      (clip.decision.a_ciegas ? " (a ciegas)" : "")]);
    filas.push(["Guardada", clip.decision.timestamp.replace("T", " ")]);
  }
  for (const [clave, valor] of filas) {
    const fila = document.createElement("div");
    const dt = document.createElement("dt");
    dt.textContent = clave;
    const dd = document.createElement("dd");
    dd.textContent = valor;
    fila.append(dt, dd);
    nodos.datosClip.appendChild(fila);
  }
}

function dibujarTransporte() {
  const clip = clipActual();
  const hayCuadro = visor.cuadroActual !== null;
  nodos.capaVacia.classList.toggle("oculto", Boolean(clip) && hayCuadro);
  nodos.capaCargando.classList.toggle("oculto", !visor.cargando);

  const navegable = Boolean(clip) && hayCuadro;
  for (const b of [nodos.btnInicioClip, nodos.btnFinClip, nodos.btnAnterior,
    nodos.btnSiguiente, nodos.btnRetrocederSalto, nodos.btnAvanzarSalto]) {
    b.disabled = !navegable;
  }
  nodos.btnReproducir.disabled = !navegable || !visor.puedeReproducir();
  nodos.btnReproducir.textContent = visor.reproduciendo ? "Pausar" : "Reproducir";
  nodos.velocidad.disabled = !navegable;

  if (!navegable) {
    nodos.barra.disabled = true;
    nodos.indicadorCuadro.textContent = "cuadro —";
    nodos.indicadorClip.textContent = "—";
    return;
  }
  // La barra recorre solo el clip. Las flechas sí pueden salir de él para
  // ver el contexto, y el indicador lo dice.
  const actual = visor.cuadroActual;
  nodos.barra.disabled = false;
  nodos.barra.min = String(clip.cuadro_inicio);
  nodos.barra.max = String(clip.cuadro_fin);
  nodos.barra.value = String(Math.min(clip.cuadro_fin, Math.max(clip.cuadro_inicio, actual)));

  nodos.indicadorCuadro.textContent = "cuadro " + actual;
  const dentro = actual >= clip.cuadro_inicio && actual <= clip.cuadro_fin;
  const largo = clip.cuadro_fin - clip.cuadro_inicio + 1;
  nodos.indicadorClip.textContent = dentro
    ? (actual - clip.cuadro_inicio + 1) + " de " + largo + " del clip" +
      (visor.fps ? " · " + ((actual - clip.cuadro_inicio) / visor.fps).toFixed(2) + " s" : "")
    : "fuera del clip (" + (actual < clip.cuadro_inicio ? "antes" : "después") + ")";
  nodos.indicadorClip.classList.toggle("fuera", !dentro);
  nodos.indicadorEstado.textContent = visor.reproduciendo
    ? "reproduciendo el clip en bucle a " + visor.velocidad + "×"
    : "en pausa";
  nodos.indicadorEstado.classList.toggle("activo", visor.reproduciendo);
}

function dibujarGeometria() {
  const clip = clipActual();
  const listo = Boolean(clip) && editor.listo;
  nodos.btnLineaAgua.disabled = !listo;
  nodos.btnDeshacer.disabled = !editor.puedeDeshacer;
  nodos.capaDibujo.dataset.modo = editor.modo;
  nodos.pistaModo.classList.toggle("dibujando", editor.modo !== MODO.NAVEGAR);
  nodos.pistaModo.textContent = !listo
    ? "—"
    : editor.modo === MODO.DIBUJAR_AGUA
      ? "Marca los dos extremos de la línea de agua: faltan " + editor.faltan + ". Esc cancela."
      : "Espacio reproduce el clip en bucle. 1, 2, 3 eligen la conducta; D descarta; Enter guarda.";

  const e = nodos.estadoGeometria;
  const region = editor.regiones[0];
  if (!clip || !region) {
    e.textContent = "—";
    nodos.btnRestaurar.disabled = true;
    nodos.notaCamara.textContent = "";
    return;
  }
  const original = regionOriginal(clip);
  const roi = !mismosPuntos(region.esquinas, original.esquinas);
  const agua = !mismosPuntos(region.lineaAgua, original.lineaAgua);
  nodos.btnRestaurar.disabled = !roi && !agua;
  e.className = "estado-deteccion" + (roi || agua ? " cambiado" : "");
  e.textContent = roi && agua
    ? "Región y línea de agua corregidas. Se guardarán con la decisión."
    : roi || agua
      ? (roi ? "Región corregida" : "Línea de agua corregida") + ". Se guardará con la decisión."
    : "Sin cambios respecto a la corrida" +
      (original.lineaAgua ? "." : "; la corrida no tenía línea de agua.");

  // La región se dibujó sobre el cuadro de referencia. Si la cámara se movió,
  // en este bloque se verá desplazada aunque las capas la hayan seguido.
  const mov = clip.capas.movimiento || {};
  const px = mov.camara_desplazamiento_px;
  const referencia = corridaDe(clip).cuadro_referencia;
  nodos.notaCamara.textContent = typeof px === "number"
    ? "En este bloque la cámara estaba desplazada en promedio " + px + " px respecto " +
      "al cuadro de referencia" + (referencia == null ? "" : " (" + referencia + ")") +
      ", y las capas ya compensaron ese movimiento. Un desfase de ese tamaño entre " +
      "la región y el cilindro es la cámara, no un error de la región."
    : "";
}

function mismosPuntos(a, b) {
  if (!a || !b) return !a && !b;
  if (a.length !== b.length) return false;
  return a.every((p, i) =>
    Math.abs(p.x - b[i].x) <= TOLERANCIA_PX && Math.abs(p.y - b[i].y) <= TOLERANCIA_PX);
}

/* ------------------------------------------------- lo que dijo cada capa */

function dibujarCapas() {
  const clip = clipActual();
  const ciegas = nodos.aCiegas.checked;
  nodos.capasOcultas.classList.toggle("oculto", !ciegas);
  nodos.capas.innerHTML = "";
  if (!clip || ciegas) return;
  // Mostrarlas una sola vez basta para que la decisión ya no sea a ciegas.
  estado.vioCapas = true;

  const corrida = corridaDe(clip);
  const motivos = document.createElement("div");
  motivos.className = "resumen-deteccion";
  for (const texto of clip.explicacion) {
    const p = document.createElement("p");
    p.textContent = texto;
    motivos.appendChild(p);
  }
  nodos.capas.append(
    motivos,
    tarjetaModelo(clip.capas.modelo || {}, corrida.parametros),
    tarjetaReglas(clip.capas.reglas || {}, corrida.parametros),
    tarjetaMovimiento(clip.capas.movimiento || {}),
  );
}

/* `sello` sustituye al nombre de la clase en el título: la detección de
   movimiento no nombra conducta, dice inmóvil o activo. */
function tarjeta(titulo, clase, detalle, sello = null) {
  const caja = document.createElement("section");
  caja.className = "region-deteccion tarjeta-capa";
  const h = document.createElement("h3");
  h.textContent = titulo + " ";
  const marca = document.createElement("span");
  marca.className = "pct";
  marca.textContent = sello || clase || "se abstiene";
  if (clase && COLOR_CLASE[clase]) marca.style.color = COLOR_CLASE[clase];
  h.appendChild(marca);
  caja.appendChild(h);
  for (const linea of detalle.filter(Boolean)) {
    const p = document.createElement("p");
    p.className = "detalle";
    p.textContent = linea;
    caja.appendChild(p);
  }
  return caja;
}

function tarjetaModelo(m, parametros) {
  if (!m.disponible && !m.probabilidades) {
    return tarjeta("Modelo preentrenado", null, ["No corrió en esta corrida."]);
  }
  const minimo = parametros.confianza_minima_modelo;
  const caja = tarjeta("Modelo preentrenado", m.clase, [
    m.certidumbre == null ? null
      : "Confianza " + m.certidumbre.toFixed(2) +
        (minimo == null ? "" : m.certidumbre >= minimo
          ? " (sobre el mínimo " + minimo + ")"
          : " — por debajo del mínimo " + minimo),
    "Cobertura del bloque por sus clips de 3 s: " + m.cobertura,
    m.motivo_abstencion === "cobertura_baja"
      ? "Se abstiene: sus clips cubren muy poco del bloque." : null,
  ]);
  if (m.probabilidades) caja.appendChild(barras(m.probabilidades));
  return caja;
}

function tarjetaReglas(g, parametros) {
  if (!g.disponible) return tarjeta("Reglas geométricas", null, ["No corrieron en esta corrida."]);
  const minimo = parametros.margen_minimo_reglas;
  const med = g.medianas || {};
  const medidas = [
    ["desplazamiento", "desplazamiento"], ["proximidad_pared", "proximidad a la pared"],
    ["verticalidad", "verticalidad"], ["sobre_agua", "masa sobre el agua"],
  ].filter(([k]) => med[k] != null).map(([k, nombre]) => nombre + " " + med[k]);
  const caja = tarjeta("Reglas geométricas", g.clase, [
    g.certidumbre == null ? null
      : "Margen del voto " + g.certidumbre.toFixed(2) +
        (minimo == null ? "" : g.certidumbre >= minimo
          ? " (sobre el mínimo " + minimo + ")"
          : " — por debajo del mínimo " + minimo) +
        (g.empate ? " · EMPATE" : ""),
    g.cuadros_sin_cuerpo ? g.cuadros_sin_cuerpo + " cuadros sin cuerpo detectado" : null,
    medidas.length ? "Medianas del bloque: " + medidas.join(", ") : null,
  ]);
  if (g.votos) {
    const total = Object.values(g.votos).reduce((s, v) => s + v, 0) || 1;
    const fracciones = {};
    for (const [k, v] of Object.entries(g.votos)) fracciones[k] = v / total;
    caja.appendChild(barras(fracciones, g.votos));
  }
  return caja;
}

function tarjetaMovimiento(mov) {
  if (!mov.disponible) {
    return tarjeta("Detección de movimiento", null, ["No corrió en esta corrida."]);
  }
  // Es una compuerta, no un voto de tres clases: dice con qué es compatible.
  const salida = mov.salida === "inmovil" ? "inmóvil" : mov.salida;
  return tarjeta("Detección de movimiento", null, [
    mov.cambio_medio == null ? null : "Cambio medio entre cuadros: " + mov.cambio_medio,
    mov.compatible_con && mov.compatible_con.length
      ? "Es una compuerta, no un voto: compatible con " +
        mov.compatible_con.join(" o ") + "." : null,
  ], salida || "sin datos");
}

/* Una barra por conducta. `cuentas` pone el número crudo a la derecha en
   lugar de la fracción (los votos de las reglas). */
function barras(fracciones, cuentas = null) {
  const caja = document.createElement("div");
  caja.className = "barras";
  for (const e of ETIQUETAS) {
    const valor = fracciones[e.clase] || 0;
    const fila = document.createElement("div");
    fila.className = "barra-fila";
    const nombre = document.createElement("span");
    nombre.textContent = e.clase;
    const pista = document.createElement("span");
    pista.className = "barra-pista";
    const relleno = document.createElement("span");
    relleno.className = "barra-relleno";
    relleno.style.width = (100 * valor).toFixed(1) + "%";
    relleno.style.background = COLOR_CLASE[e.clase];
    pista.appendChild(relleno);
    const cifra = document.createElement("span");
    cifra.className = "barra-cifra";
    cifra.textContent = cuentas ? String(cuentas[e.clase] || 0) : valor.toFixed(2);
    fila.append(nombre, pista, cifra);
    caja.appendChild(fila);
  }
  return caja;
}

/* --------------------------------------------------------------- avisos */

function avisar(texto) {
  nodos.aviso.classList.remove("oculto");
  nodos.aviso.classList.add("error");
  nodos.aviso.textContent = texto;
}

arrancar();
