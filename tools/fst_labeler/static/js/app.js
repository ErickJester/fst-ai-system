/* Interfaz del visor cuadro a cuadro (Módulo 2).

   Este archivo solo conecta el motor del visor con los controles de la
   página: no decide nada sobre el video ni sobre la conducta del espécimen.
   La lógica de reproducción vive en visor.js y las rutas del servidor en
   api.js. */

import { ClienteFST } from "./api.js";
import { Visor } from "./visor.js";
import { EditorRegiones, MODO, MAX_REGIONES, COLORES } from "./dibujo.js";
import { PanelDeteccion } from "./deteccion.js";
import { PanelModelo } from "./modelo.js";

const el = (id) => document.getElementById(id);

const nodos = {
  version: el("version"),
  selectorVideo: el("selector-video"),
  btnRecargar: el("btn-recargar"),
  aviso: el("aviso"),

  cuadro: el("cuadro"),
  capaDibujo: el("capa-dibujo"),
  capaVacia: el("capa-vacia"),
  capaCargando: el("capa-cargando"),
  pistaModo: el("pista-modo"),

  barra: el("barra"),
  btnInicio: el("btn-inicio"),
  btnRetrocederSalto: el("btn-retroceder-salto"),
  btnAnterior: el("btn-anterior"),
  btnReproducir: el("btn-reproducir"),
  btnSiguiente: el("btn-siguiente"),
  btnAvanzarSalto: el("btn-avanzar-salto"),
  btnFin: el("btn-fin"),
  saltoCuadros: el("salto-cuadros"),
  velocidad: el("velocidad"),
  irCuadro: el("ir-cuadro"),
  btnIr: el("btn-ir"),

  indicadorCuadro: el("indicador-cuadro"),
  indicadorTiempo: el("indicador-tiempo"),
  indicadorEstado: el("indicador-estado"),
  indicadorOmitidos: el("indicador-omitidos"),

  fpsEfectivo: el("fps-efectivo"),
  fpsOrigen: el("fps-origen"),
  tablaMetadata: el("tabla-metadata"),
  btnVerificarTotal: el("btn-verificar-total"),
  maxAncho: el("max-ancho"),
  calidad: el("calidad"),

  btnNuevaRoi: el("btn-nueva-roi"),
  btnLineaAgua: el("btn-linea-agua"),
  btnDeshacer: el("btn-deshacer"),
  listaRegiones: el("lista-regiones"),

  segundosBloque: el("segundos-bloque"),
  umbralBinarizacion: el("umbral-binarizacion"),
  umbralActividad: el("umbral-actividad"),
  cuadroInicio: el("cuadro-inicio-analisis"),
  estabilizar: el("estabilizar"),
  btnEstable: el("btn-inicio-estable"),
  btnAnalizar: el("btn-analizar"),
  estado: el("estado-deteccion"),
  resultados: el("resultados-deteccion"),

  remuestrear: el("remuestrear"),
  fondo: el("fondo-modelo"),
  estabilizarModelo: el("estabilizar-modelo"),
  maxClips: el("max-clips"),
  btnModelo: el("btn-modelo"),
  estadoModelo: el("estado-modelo"),
  resultadosModelo: el("resultados-modelo"),
};

const cliente = new ClienteFST();
const visor = new Visor(cliente, nodos.cuadro, { alCambiar: dibujar });
const editor = new EditorRegiones(nodos.capaDibujo, nodos.cuadro, {
  alCambiar: dibujarRegiones,
});
const irAlCuadro = (cuadro) => {
  visor.pausar();
  visor.irA(cuadro);
};
const modelo = new PanelModelo(nodos, { alPedirCuadro: irAlCuadro });
const deteccion = new PanelDeteccion(nodos, {
  // Hacer clic en un bloque del reporte lleva el visor a ese punto del video.
  alPedirCuadro: (cuadro) => {
    visor.pausar();
    visor.irA(cuadro);
  },
});

// Mensaje propio de la interfaz (errores al listar videos, por ejemplo),
// aparte de los que produce el motor del visor.
let avisoInterfaz = null;
let verificandoTotal = false;

/* ------------------------------------------------------------- arranque */

async function arrancar() {
  try {
    const indice = await fetch("/api/indice").then((r) => r.json());
    nodos.version.textContent = "v" + indice.version;
  } catch (e) {
    // La versión es informativa: si no llega, la interfaz sigue siendo usable.
  }

  try {
    const prefs = await cliente.preferencias();
    visor.fijarTransporte({ calidad: prefs.calidad, maxAncho: prefs.max_ancho });
    visor.prefetch = prefs.prefetch;
    nodos.calidad.value = prefs.calidad;
    nodos.maxAncho.value = prefs.max_ancho;
    nodos.saltoCuadros.value = prefs.salto_cuadros;
  } catch (e) {
    avisoInterfaz = "No se pudieron leer las preferencias del servidor: " + e.message;
  }

  conectarControles();
  await cargarListaDeVideos();
  dibujar(visor);
  dibujarRegiones(editor);
  sincronizarDeteccion();
}

async function cargarListaDeVideos() {
  nodos.selectorVideo.innerHTML = "";
  try {
    const datos = await cliente.listarVideos();
    if (!datos.total) {
      agregarOpcion("", "— no hay videos en " + datos.videos_dir + " —");
      avisoInterfaz =
        "La carpeta de videos está vacía: " + datos.videos_dir +
        "\nColoca ahí los videos, o genera uno sintético de prueba con:" +
        "\n    python scripts/generar_video_prueba.py --fps 30 --segundos 60";
      dibujar(visor);
      return;
    }
    agregarOpcion("", "— selecciona un video (" + datos.total + " disponibles) —");
    for (const video of datos.videos) {
      agregarOpcion(video.video_id, video.video_id);
    }
    avisoInterfaz = null;
  } catch (e) {
    agregarOpcion("", "— no se pudo leer la lista —");
    avisoInterfaz = "No se pudo leer la lista de videos: " + e.message;
  }
  dibujar(visor);
}

function agregarOpcion(valor, texto) {
  const opcion = document.createElement("option");
  opcion.value = valor;
  opcion.textContent = texto;
  nodos.selectorVideo.appendChild(opcion);
}

/* ------------------------------------------------------------ controles */

function conectarControles() {
  nodos.selectorVideo.addEventListener("change", async () => {
    const videoId = nodos.selectorVideo.value;
    if (!videoId) return;
    avisoInterfaz = null;
    try {
      await visor.abrirVideo(videoId);
    } catch (e) {
      avisoInterfaz = "No se pudo abrir " + videoId + ": " + e.message;
      dibujar(visor);
    }
  });

  nodos.btnRecargar.addEventListener("click", cargarListaDeVideos);

  nodos.btnInicio.addEventListener("click", () => visor.alInicio());
  nodos.btnFin.addEventListener("click", () => visor.alFinal());
  nodos.btnAnterior.addEventListener("click", () => visor.avanzar(-1));
  nodos.btnSiguiente.addEventListener("click", () => visor.avanzar(1));
  nodos.btnRetrocederSalto.addEventListener("click", () => visor.avanzar(-salto()));
  nodos.btnAvanzarSalto.addEventListener("click", () => visor.avanzar(salto()));
  nodos.btnReproducir.addEventListener("click", () => visor.alternarReproduccion());

  // Mover la barra es navegación manual: interrumpe la reproducción.
  nodos.barra.addEventListener("input", () => {
    visor.pausar();
    visor.irA(Number(nodos.barra.value));
  });

  nodos.btnIr.addEventListener("click", irAlCuadroEscrito);
  nodos.irCuadro.addEventListener("keydown", (evento) => {
    if (evento.key === "Enter") irAlCuadroEscrito();
  });

  nodos.velocidad.addEventListener("change", () => {
    visor.fijarVelocidad(nodos.velocidad.value);
  });

  nodos.fpsEfectivo.addEventListener("change", () => {
    if (!visor.fijarFpsManual(nodos.fpsEfectivo.value)) {
      avisoInterfaz = "Los cuadros por segundo deben ser un número mayor que cero.";
      dibujar(visor);
    }
  });

  nodos.calidad.addEventListener("change", aplicarTransporte);
  nodos.maxAncho.addEventListener("change", aplicarTransporte);

  nodos.btnVerificarTotal.addEventListener("click", async () => {
    verificandoTotal = true;
    dibujar(visor);
    try {
      await visor.verificarTotal();
    } catch (e) {
      avisoInterfaz = "No se pudo verificar el total de cuadros: " + e.message;
    } finally {
      verificandoTotal = false;
      dibujar(visor);
    }
  });

  nodos.btnNuevaRoi.addEventListener("click", () => editor.nuevaRegion());
  nodos.btnLineaAgua.addEventListener("click", () => editor.nuevaLineaDeAgua());
  nodos.btnDeshacer.addEventListener("click", () => editor.deshacer());

  // El cuadro cambia de tamano al cargarse y al redimensionar la ventana; el
  // lienzo tiene que seguirlo o las regiones quedarian desplazadas.
  nodos.cuadro.addEventListener("load", () => editor.redibujar());
  if (window.ResizeObserver) {
    new ResizeObserver(() => editor.redibujar()).observe(nodos.cuadro);
  } else {
    window.addEventListener("resize", () => editor.redibujar());
  }

  document.addEventListener("keydown", atajos);
}

function salto() {
  const valor = Math.round(Number(nodos.saltoCuadros.value));
  return Number.isFinite(valor) && valor >= 1 ? valor : 1;
}

function irAlCuadroEscrito() {
  const valor = Number(nodos.irCuadro.value);
  if (!Number.isFinite(valor)) return;
  visor.pausar();
  visor.irA(valor);
}

function aplicarTransporte() {
  const calidad = Math.round(Number(nodos.calidad.value));
  const maxAncho = Math.round(Number(nodos.maxAncho.value));
  if (!(calidad >= 1 && calidad <= 100) || !(maxAncho >= 16)) {
    avisoInterfaz = "Calidad entre 1 y 100; ancho máximo de al menos 16 px.";
    dibujar(visor);
    return;
  }
  avisoInterfaz = null;
  visor.fijarTransporte({ calidad, maxAncho });
  if (visor.cuadroActual !== null) visor.irA(visor.cuadroActual);
}

function atajos(evento) {
  // Si se está escribiendo en un campo, las teclas son del campo.
  const destino = evento.target;
  if (destino && /^(INPUT|SELECT|TEXTAREA)$/.test(destino.tagName)) return;
  if (evento.ctrlKey || evento.altKey || evento.metaKey) return;
  if (!visor.videoId) return;

  switch (evento.key) {
    case "ArrowLeft":
      visor.avanzar(evento.shiftKey ? -salto() : -1);
      break;
    case "ArrowRight":
      visor.avanzar(evento.shiftKey ? salto() : 1);
      break;
    case " ":
      visor.alternarReproduccion();
      break;
    case "Home":
      visor.alInicio();
      break;
    case "End":
      visor.alFinal();
      break;
    case "r":
    case "R":
      editor.nuevaRegion();
      break;
    case "w":
    case "W":
      editor.nuevaLineaDeAgua();
      break;
    case "z":
    case "Z":
      editor.deshacer();
      break;
    case "Delete":
      editor.borrarSeleccionada();
      break;
    case "Escape":
      editor.cancelar();
      break;
    default:
      return;
  }
  evento.preventDefault();
}

/* --------------------------------------------------------------- dibujo */

function dibujar(v) {
  const hayVideo = Boolean(v.videoId);
  const hayCuadro = v.cuadroActual !== null;
  const tope = v.ultimoCuadro;

  nodos.capaVacia.classList.toggle("oculto", hayCuadro);
  nodos.capaCargando.classList.toggle("oculto", !v.cargando);

  // Controles de navegación.
  const navegable = hayVideo && hayCuadro;
  for (const boton of [nodos.btnInicio, nodos.btnAnterior, nodos.btnRetrocederSalto]) {
    boton.disabled = !navegable || v.cuadroActual === 0;
  }
  const alFinal = tope !== null && hayCuadro && v.cuadroActual >= tope;
  for (const boton of [nodos.btnSiguiente, nodos.btnAvanzarSalto]) {
    boton.disabled = !navegable || alFinal;
  }
  nodos.btnFin.disabled = !navegable || tope === null || alFinal;
  nodos.saltoCuadros.disabled = !navegable;
  nodos.irCuadro.disabled = !navegable;
  nodos.btnIr.disabled = !navegable;
  nodos.velocidad.disabled = !v.puedeReproducir();
  nodos.btnReproducir.disabled = !v.puedeReproducir();
  nodos.btnReproducir.textContent = v.reproduciendo ? "Pausar" : "Reproducir";

  // Barra de posición: solo tiene sentido si se sabe dónde termina el video.
  if (navegable && tope !== null && tope > 0) {
    nodos.barra.disabled = false;
    nodos.barra.max = String(tope);
    nodos.barra.value = String(v.cuadroActual);
    nodos.irCuadro.max = String(tope);
  } else {
    nodos.barra.disabled = true;
    nodos.barra.value = "0";
  }

  // Indicadores.
  const total = v.totalCuadros
    ? v.totalCuadros + (v.totalExacto ? "" : " (declarado)")
    : "total desconocido";
  nodos.indicadorCuadro.textContent = hayCuadro
    ? "cuadro " + v.cuadroActual + " de " + total
    : "cuadro — de " + (hayVideo ? total : "—");
  nodos.indicadorTiempo.textContent = textoDeTiempo(v);
  nodos.indicadorEstado.textContent = v.reproduciendo
    ? "reproduciendo a " + v.velocidad + "×"
    : "en pausa";
  nodos.indicadorEstado.classList.toggle("activo", v.reproduciendo);
  nodos.indicadorOmitidos.textContent = v.omitidos
    ? v.omitidos + " cuadros omitidos para sostener la velocidad real"
    : "";

  // Cuadros por segundo.
  nodos.fpsEfectivo.disabled = !hayVideo;
  if (document.activeElement !== nodos.fpsEfectivo) {
    nodos.fpsEfectivo.value = v.fps === null ? "" : v.fps;
  }
  nodos.fpsOrigen.textContent = hayVideo
    ? "Origen: " + (v.fpsOrigen || "—")
    : "Origen: —";

  nodos.calidad.disabled = !hayVideo;
  nodos.maxAncho.disabled = !hayVideo;
  nodos.btnVerificarTotal.disabled = !hayVideo || verificandoTotal || v.totalExacto;
  nodos.btnVerificarTotal.textContent = verificandoTotal
    ? "Contando cuadros…"
    : v.totalExacto
      ? "Total ya verificado"
      : "Verificar total de cuadros";

  dibujarMetadata(v);
  dibujarAviso(v);

  // Las regiones pertenecen al video, no al cuadro: al cambiar de cuadro el
  // editor solo se vuelve a pintar; al cambiar de video, se cambian.
  editor.sincronizar(v.videoId, v.meta);
  sincronizarDeteccion();
}

function sincronizarDeteccion() {
  const estado = {
    videoId: visor.videoId,
    fps: visor.fps,
    cuadroActual: visor.cuadroActual === null ? 0 : visor.cuadroActual,
    regiones: editor.regiones,
  };
  modelo.sincronizar(estado);
  deteccion.sincronizar({
    videoId: visor.videoId,
    fps: visor.fps,
    cuadroActual: visor.cuadroActual === null ? 0 : visor.cuadroActual,
    regiones: editor.regiones,
  });
}

function textoDeTiempo(v) {
  if (v.cuadroActual === null) return "—";
  const segundos = v.segundosDe(v.cuadroActual);
  if (segundos === null) return "tiempo no calculable sin cuadros por segundo";
  const tope = v.ultimoCuadro;
  const duracion = tope === null ? null : v.segundosDe(tope);
  return duracion === null
    ? reloj(segundos)
    : reloj(segundos) + " de " + reloj(duracion);
}

function reloj(segundos) {
  const minutos = Math.floor(segundos / 60);
  const resto = segundos - minutos * 60;
  return String(minutos).padStart(2, "0") + ":" + resto.toFixed(3).padStart(6, "0");
}

function dibujarMetadata(v) {
  const meta = v.meta;
  if (!meta) {
    nodos.tablaMetadata.innerHTML = "";
    agregarFila(nodos.tablaMetadata, "Archivo", "—");
    return;
  }
  const filas = [
    ["Archivo", meta.nombre],
    ["Resolución", meta.ancho && meta.alto ? meta.ancho + " × " + meta.alto + " px" : "no reportada"],
    ["Cuadros por segundo", meta.fps === null ? "no declarados" : String(meta.fps)],
    ["Total de cuadros", meta.total_cuadros === null
      ? "no declarado"
      : meta.total_cuadros + (meta.total_cuadros_exacto ? " (verificado)" : " (declarado)")],
    ["Duración", meta.duracion_s === null ? "no calculable" : meta.duracion_s + " s"],
    ["Códec", meta.codec],
    ["Tamaño", (meta.tamano_bytes / (1024 * 1024)).toFixed(1) + " MB"],
  ];
  nodos.tablaMetadata.innerHTML = "";
  for (const [clave, valor] of filas) {
    const sospechoso =
      (clave === "Cuadros por segundo" && !meta.fps_confiable) ||
      (clave === "Total de cuadros" && !meta.total_cuadros_exacto);
    agregarFila(nodos.tablaMetadata, clave, valor, sospechoso);
  }
}

function agregarFila(tabla, clave, valor, sospechoso = false) {
  const fila = document.createElement("div");
  const dt = document.createElement("dt");
  dt.textContent = clave;
  const dd = document.createElement("dd");
  dd.textContent = valor;
  if (sospechoso) dd.className = "sospechoso";
  fila.append(dt, dd);
  tabla.appendChild(fila);
}

/* ----------------------------------------------------- panel de regiones */

function dibujarRegiones(ed) {
  // El detector del Modulo 4 trabaja sobre estas regiones: cualquier cambio
  // en ellas cambia lo que se analizaria.
  sincronizarDeteccion();
  const listo = ed.listo;
  nodos.btnNuevaRoi.disabled = !listo || ed.regiones.length >= MAX_REGIONES;
  nodos.btnLineaAgua.disabled = !listo || !ed.seleccionada;
  nodos.btnDeshacer.disabled = !ed.puedeDeshacer;
  nodos.capaDibujo.dataset.modo = ed.modo;

  nodos.pistaModo.textContent = textoDeModo(ed);
  nodos.pistaModo.classList.toggle("dibujando", ed.modo !== MODO.NAVEGAR);

  nodos.listaRegiones.innerHTML = "";
  if (!listo) return;
  if (!ed.regiones.length) {
    const vacio = document.createElement("li");
    vacio.className = "region-vacia";
    vacio.textContent = "Ninguna región dibujada todavía.";
    nodos.listaRegiones.appendChild(vacio);
    return;
  }

  ed.regiones.forEach((region, indice) => {
    const fila = document.createElement("li");
    fila.className = "region" + (region === ed.seleccionada ? " activa" : "");
    fila.style.setProperty("--color-region", COLORES[indice % COLORES.length]);

    const nombre = document.createElement("button");
    nombre.type = "button";
    nombre.className = "region-nombre";
    nombre.innerHTML =
      '<span class="region-numero">' + (indice + 1) + "</span>" +
      '<span class="region-estado">' +
      (region.lineaAgua ? "con línea de agua" : "sin línea de agua") +
      "</span>";
    nombre.addEventListener("click", () => ed.seleccionar(region));

    const borrar = document.createElement("button");
    borrar.type = "button";
    borrar.className = "region-borrar";
    borrar.title = "Borrar esta región";
    borrar.textContent = "×";
    borrar.addEventListener("click", () => {
      ed.seleccionar(region);
      ed.borrarSeleccionada();
    });

    fila.append(nombre, borrar);
    nodos.listaRegiones.appendChild(fila);
  });
}

function textoDeModo(ed) {
  if (!ed.listo) return "Selecciona un video para dibujar las regiones de interés.";
  if (ed.modo === MODO.DIBUJAR_ROI) {
    return "Marca las cuatro esquinas de la cámara de natación: faltan " +
      ed.faltan + ". Esc cancela.";
  }
  if (ed.modo === MODO.DIBUJAR_AGUA) {
    return "Marca los dos extremos de la línea de agua: faltan " +
      ed.faltan + ". Esc cancela.";
  }
  if (!ed.regiones.length) {
    return "R dibuja una región de interés (hasta " + MAX_REGIONES + ").";
  }
  return "Arrastra las esquinas para ajustar, o el interior para mover la " +
    "región entera. W pone su línea de agua, Z deshace, Supr la borra.";
}

function dibujarAviso(v) {
  const partes = [];
  if (avisoInterfaz) partes.push(avisoInterfaz);
  if (v.mensaje) partes.push(v.mensaje);
  if (v.videoId && !v.fps) {
    partes.push(
      "Este video no declara cuadros por segundo confiables, así que la " +
      "reproducción continua está desactivada. Escribe el valor real en el " +
      "panel lateral para habilitarla; mientras tanto puedes navegar cuadro a cuadro."
    );
  }
  if (v.meta && v.meta.aviso && !v.meta.total_cuadros_exacto) {
    partes.push(v.meta.aviso);
  }

  if (!partes.length) {
    nodos.aviso.classList.add("oculto");
    nodos.aviso.textContent = "";
    return;
  }
  nodos.aviso.classList.remove("oculto");
  nodos.aviso.classList.toggle("error", Boolean(avisoInterfaz));
  nodos.aviso.textContent = partes.join("\n");
}

arrancar();
