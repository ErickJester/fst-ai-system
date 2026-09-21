/* Panel del modelo 3D preentrenado (Módulo 5, Capa 1).

   Envía las regiones del Módulo 3 y pinta la predicción de las tres clases
   por clip de 3 s, con su confianza.

   A diferencia del Módulo 4, esta capa sí distingue las tres conductas. Pero
   los pesos se entrenaron sobre otro montaje de cámara, así que su exactitud
   aquí está por establecerse: entra al consenso del Módulo 7 como un votante
   más, no como juez. */

/* Compartido con el panel de reglas geométricas: los colores de las clases
   tienen que coincidir entre paneles para poder comparar las dos capas de un
   vistazo. */
export const COLOR_CLASE = {
  inmovilidad: "#4a9dd8",
  nado: "#5fbf7f",
  escalamiento: "#e0a53c",
  "sin datos": "#3a424e",
};

export class PanelModelo {
  constructor(nodos, { alPedirCuadro, alTerminar } = {}) {
    this.n = nodos;
    this.alPedirCuadro = alPedirCuadro || (() => {});
    // El panel del consenso (Modulo 7) necesita saber cuando hay un
    // reporte nuevo que combinar.
    this.alTerminar = alTerminar || (() => {});
    this.videoId = null;
    this.ocupado = false;
    this.informe = null;
    this.mensaje = null;
    this.error = null;
    this.estado = null;   // disponibilidad de pesos y TensorFlow
    this.n.btnModelo.addEventListener("click", () => this.analizar());
    this._consultarEstado();
  }

  async _consultarEstado() {
    try {
      this.estado = await fetch("/api/modelo-3d/estado").then((r) => r.json());
    } catch (e) {
      this.estado = null;
    }
    this.dibujar();
  }

  sincronizar({ videoId, fps, cuadroActual, regiones }) {
    if (videoId !== this.videoId) {
      this.videoId = videoId;
      this.informe = null;
      this.mensaje = null;
      this.error = null;
    }
    this.fps = fps;
    this.cuadroActual = cuadroActual;
    this.regiones = regiones || [];
    this.dibujar();
  }

  get disponible() {
    return Boolean(this.estado && this.estado.pesos_presentes &&
                   this.estado.tensorflow && this.estado.tf_keras);
  }

  get listo() {
    return this.disponible && Boolean(this.videoId) &&
           this.regiones.length > 0 && !this.ocupado;
  }

  async analizar() {
    if (!this.listo) return;
    this.ocupado = true;
    this.error = null;
    this.informe = null;
    this.mensaje =
      "Construyendo el fondo y preparando los clips de 3 s. La inferencia es " +
      "rápida, pero preparar los tensores lleva su tiempo.";
    this.dibujar();
    try {
      const cuerpo = {
        regiones: this.regiones.map((r) => ({ esquinas: r.esquinas })),
        cuadro_referencia: this.cuadroActual,
        fps: this.fps || undefined,
        parametros: {
          remuestrear: this.n.remuestrear.checked,
          fondo: this.n.fondo.value,
          estabilizar: this.n.estabilizarModelo.checked,
          max_clips: Number(this.n.maxClips.value) || 0,
        },
      };
      const inicio = Number(this.n.cuadroInicio.value);
      if (Number.isFinite(inicio) && inicio > 0) cuerpo.cuadro_inicio = inicio;

      const resp = await fetch(
        "/api/videos/" + codificar(this.videoId) + "/modelo-3d",
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(cuerpo),
        }
      );
      const datos = await resp.json().catch(() => null);
      if (!resp.ok) throw new Error((datos && datos.error) || "HTTP " + resp.status);
      this.informe = datos;
      this.mensaje = null;
    } catch (e) {
      this.error = e.message;
      this.mensaje = null;
    } finally {
      this.ocupado = false;
      this.dibujar();
      this.alTerminar();
    }
  }

  dibujar() {
    this.n.btnModelo.disabled = !this.listo;
    this.n.btnModelo.textContent = this.ocupado ? "Trabajando…" : "Predecir conducta";
    for (const c of [this.n.remuestrear, this.n.fondo, this.n.estabilizarModelo, this.n.maxClips]) {
      c.disabled = this.ocupado || !this.disponible;
    }

    const e = this.n.estadoModelo;
    e.className = "estado-deteccion" + (this.error ? " error" : "");
    if (this.error) e.textContent = "No se pudo predecir: " + this.error;
    else if (this.mensaje) e.textContent = this.mensaje;
    else if (!this.estado) e.textContent = "No se pudo consultar el estado del modelo.";
    else if (!this.estado.pesos_presentes)
      e.textContent = "Faltan los pesos. Descárgalos de Zenodo (DOI 10.5281/zenodo.14638257, " +
        "CC-BY-4.0) a " + this.estado.ruta_pesos;
    else if (!this.estado.tensorflow || !this.estado.tf_keras)
      e.textContent = "Falta TensorFlow. Instala tensorflow==2.17.1 y tf-keras==2.17.0.";
    else if (!this.videoId) e.textContent = "Selecciona un video.";
    else if (!this.regiones.length) e.textContent = "Dibuja al menos una región de interés (R).";
    else e.textContent = "Listo para predecir sobre " + this.regiones.length +
      (this.regiones.length === 1 ? " región." : " regiones.");

    this.n.resultadosModelo.innerHTML = "";
    if (!this.informe) return;
    this._dibujarResumen();
    this.informe.regiones.forEach((r) => this._dibujarRegion(r));
  }

  _dibujarResumen() {
    const i = this.informe;
    const caja = document.createElement("div");
    caja.className = "resumen-deteccion";
    caja.innerHTML =
      "<p><strong>" + i.clips_por_region + "</strong> clips de " + i.segundos_por_clip +
      " s por región, desde el cuadro " + i.cuadro_inicio + ". Fondo: " +
      escapar(i.parametros.fondo) + "; remuestreo a 25 Hz: " +
      (i.parametros.remuestrear_a_25hz ? "sí" : "no") + ".</p>" +
      '<p class="advertencia">' + escapar(i.advertencia_exactitud) + "</p>";
    this.n.resultadosModelo.appendChild(caja);
  }

  _dibujarRegion(r) {
    const caja = document.createElement("section");
    caja.className = "region-deteccion";
    const p = r.porcentajes;
    const titulo = document.createElement("h3");
    titulo.innerHTML =
      "Región " + r.region + " " +
      Object.keys(p).map((k) =>
        '<span class="pct" style="color:' + COLOR_CLASE[k] + '">' +
        p[k] + " % " + k + "</span>").join(" · ");
    caja.appendChild(titulo);

    const detalle = document.createElement("p");
    detalle.className = "detalle";
    detalle.textContent =
      r.clips_validos + " clips válidos de " + r.clips +
      ", confianza media " + r.confianza_media + ".";
    caja.appendChild(detalle);

    const tira = document.createElement("div");
    tira.className = "tira-bloques";
    for (const c of r.detalle) {
      const b = document.createElement("button");
      b.type = "button";
      b.className = "bloque";
      b.style.background = COLOR_CLASE[c.clase] || COLOR_CLASE["sin datos"];
      // La opacidad lleva la confianza: un clip dudoso se ve apagado.
      b.style.opacity = c.clase === "sin datos" ? 1 : (0.35 + 0.65 * c.confianza).toFixed(2);
      b.title =
        "Clip " + c.clip + " · " + c.segundo_inicio + "–" + c.segundo_fin + " s\n" +
        c.clase + " (" + c.confianza + ")\n" +
        Object.entries(c.probabilidades).map(([k, v]) => k + " " + v).join(" · ") +
        (c.aviso ? "\n" + c.aviso : "");
      b.addEventListener("click", () => this.alPedirCuadro(c.cuadro_inicio));
      tira.appendChild(b);
    }
    caja.appendChild(tira);

    for (const aviso of r.avisos) {
      const el = document.createElement("p");
      el.className = "aviso-deteccion";
      el.textContent = aviso;
      caja.appendChild(el);
    }
    this.n.resultadosModelo.appendChild(caja);
  }
}

function codificar(videoId) {
  return String(videoId).split("/").map(encodeURIComponent).join("/");
}

function escapar(texto) {
  const d = document.createElement("div");
  d.textContent = texto;
  return d.innerHTML;
}
