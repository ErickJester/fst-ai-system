/* Panel del detector de movimiento por umbral (Módulo 4).

   Envía al servidor las regiones dibujadas en el Módulo 3 y pinta el reporte
   que devuelve: por cada región, un bloque de 5 s a la vez, con su puntuación
   y su clase.

   Las regiones viajan en el cuerpo de la petición y no se guardan en el
   servidor: el navegador sigue siendo su dueño hasta el Módulo 8. */

const CLASES = {
  inmovil: { etiqueta: "inmóvil", color: "#4a9dd8" },
  activo: { etiqueta: "activo", color: "#e0a53c" },
  "sin datos": { etiqueta: "sin datos", color: "#3a424e" },
};

export class PanelDeteccion {
  constructor(nodos, { alPedirCuadro } = {}) {
    this.n = nodos;
    this.alPedirCuadro = alPedirCuadro || (() => {});
    this.videoId = null;
    this.ocupado = false;
    this.informe = null;
    this.mensaje = null;
    this.error = null;
    this._conectar();
  }

  _conectar() {
    this.n.btnEstable.addEventListener("click", () => this.buscarInicioEstable());
    this.n.btnAnalizar.addEventListener("click", () => this.analizar());
  }

  /* Estado que el panel necesita del resto de la página. */
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

  get listo() {
    return Boolean(this.videoId) && this.regiones.length > 0 && !this.ocupado;
  }

  async _pedir(ruta, cuerpo) {
    const respuesta = await fetch(ruta, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(cuerpo),
    });
    const datos = await respuesta.json().catch(() => null);
    if (!respuesta.ok) {
      throw new Error((datos && datos.error) || "HTTP " + respuesta.status);
    }
    return datos;
  }

  _cuerpoBase() {
    return {
      regiones: this.regiones.map((r) => ({ esquinas: r.esquinas })),
      cuadro_referencia: this.cuadroActual,
      fps: this.fps || undefined,
    };
  }

  async buscarInicioEstable() {
    if (!this.listo) return;
    this.ocupado = true;
    this.error = null;
    this.mensaje = "Recorriendo el video para ver cuándo deja de moverse la cámara…";
    this.dibujar();
    try {
      const d = await this._pedir(
        "/api/videos/" + codificar(this.videoId) + "/estabilidad-camara",
        { regiones: this._cuerpoBase().regiones }
      );
      if (d.detectado) {
        this.n.cuadroInicio.value = d.cuadro_estable;
        const seg = this.fps ? (d.cuadro_estable / this.fps).toFixed(1) + " s" : "—";
        this.mensaje =
          "La cámara se acomoda hasta el cuadro " + d.cuadro_estable + " (" + seg +
          "). Deriva posterior: " + d.deriva_mediana_px + " px de mediana, " +
          d.deriva_maxima_px + " px de máxima.";
      } else {
        this.mensaje = d.motivo || "No se pudo determinar cuándo se estabiliza la cámara.";
      }
    } catch (e) {
      this.error = e.message;
    } finally {
      this.ocupado = false;
      this.dibujar();
    }
  }

  async analizar() {
    if (!this.listo) return;
    this.ocupado = true;
    this.error = null;
    this.informe = null;
    this.mensaje =
      "Analizando. Se construye el modelo de fondo y luego se recorre el video " +
      "cuadro por cuadro; en un video de 5 min tarda alrededor de un minuto.";
    this.dibujar();
    try {
      const cuerpo = this._cuerpoBase();
      const inicio = Number(this.n.cuadroInicio.value);
      if (Number.isFinite(inicio) && inicio > 0) cuerpo.cuadro_inicio = inicio;
      cuerpo.parametros = {
        segundos_por_bloque: Number(this.n.segundosBloque.value) || 5,
        umbral_binarizacion: Number(this.n.umbralBinarizacion.value) || 40,
        umbral_actividad: this.n.umbralActividad.value.trim() || "auto",
        estabilizar: this.n.estabilizar.checked,
      };
      this.informe = await this._pedir(
        "/api/videos/" + codificar(this.videoId) + "/deteccion-movimiento",
        cuerpo
      );
      this.mensaje = null;
    } catch (e) {
      this.error = e.message;
      this.mensaje = null;
    } finally {
      this.ocupado = false;
      this.dibujar();
    }
  }

  /* ------------------------------------------------------------- dibujo */

  dibujar() {
    const listo = this.listo;
    this.n.btnAnalizar.disabled = !listo;
    this.n.btnEstable.disabled = !listo;
    this.n.btnAnalizar.textContent = this.ocupado ? "Trabajando…" : "Analizar movimiento";

    for (const campo of [this.n.segundosBloque, this.n.umbralBinarizacion,
                         this.n.umbralActividad, this.n.cuadroInicio, this.n.estabilizar]) {
      campo.disabled = this.ocupado;
    }

    const estado = this.n.estado;
    estado.className = "estado-deteccion" + (this.error ? " error" : "");
    if (this.error) estado.textContent = "No se pudo analizar: " + this.error;
    else if (this.mensaje) estado.textContent = this.mensaje;
    else if (!this.videoId) estado.textContent = "Selecciona un video.";
    else if (!this.regiones.length) estado.textContent = "Dibuja al menos una región de interés (R).";
    else estado.textContent = "Listo para analizar " + this.regiones.length +
      (this.regiones.length === 1 ? " región." : " regiones.");

    this.n.resultados.innerHTML = "";
    if (!this.informe) return;
    this._dibujarResumen();
    this.informe.regiones.forEach((r) => this._dibujarRegion(r));
  }

  _dibujarResumen() {
    const i = this.informe;
    const caja = document.createElement("div");
    caja.className = "resumen-deteccion";
    const umbral = i.regiones.length ? i.regiones[0] : null;
    caja.innerHTML =
      "<p>Bloques de <strong>" + i.parametros.segundos_por_bloque + " s</strong> " +
      "(" + i.cuadros_por_bloque + " cuadros a " + i.fps + " cuadros por segundo), " +
      "desde el cuadro " + i.cuadro_inicio + " hasta el " + i.cuadro_fin + ".</p>" +
      (umbral
        ? "<p>Umbral de actividad <strong>" + umbral.umbral_usado + "</strong> — " +
          escapar(umbral.origen_umbral) + ".</p>"
        : "");
    this.n.resultados.appendChild(caja);
  }

  _dibujarRegion(r) {
    const caja = document.createElement("section");
    caja.className = "region-deteccion";

    const cordura = r.compuerta_cordura;
    const titulo = document.createElement("h3");
    titulo.innerHTML =
      "Región " + r.region +
      ' <span class="pct">' + r.porcentaje_inmovilidad + " % inmovilidad</span>" +
      ' <span class="sello ' + (cordura.sospechoso ? "malo" : "bueno") + '">' +
      (cordura.sospechoso ? "sospechoso" : "compatible con el prior") + "</span>";
    caja.appendChild(titulo);

    const detalle = document.createElement("p");
    detalle.className = "detalle";
    detalle.textContent =
      r.bloques_inmovil + " bloques inmóviles y " + r.bloques_activo +
      " activos de " + r.bloques_medidos + " medidos. " + cordura.mensaje;
    caja.appendChild(detalle);

    // Tira de bloques: un recuadro por bloque, en orden. Hacer clic lleva el
    // visor al primer cuadro de ese bloque.
    const tira = document.createElement("div");
    tira.className = "tira-bloques";
    for (const b of r.bloques) {
      const cuadro = document.createElement("button");
      cuadro.type = "button";
      cuadro.className = "bloque bloque-" + b.clase.replace(" ", "-");
      cuadro.title =
        "Bloque " + b.bloque + " · " + b.segundo_inicio + "–" + b.segundo_fin + " s\n" +
        (CLASES[b.clase] || CLASES["sin datos"]).etiqueta +
        "\ncambio " + b.cambio_medio + " · área " + b.area_especimen_media +
        "\ncámara " + b.camara_desplazamiento_px + " px";
      cuadro.addEventListener("click", () => this.alPedirCuadro(b.cuadro_inicio));
      tira.appendChild(cuadro);
    }
    caja.appendChild(tira);

    for (const aviso of r.avisos) {
      const p = document.createElement("p");
      p.className = "aviso-deteccion";
      p.textContent = aviso;
      caja.appendChild(p);
    }
    this.n.resultados.appendChild(caja);
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
