/* Panel del consenso entre capas (Módulo 7, Capa 5).

   Tiene dos caminos, y la diferencia entre ellos es de minutos:

   «Combinar» toma los reportes que los otros tres paneles ya tienen en
   pantalla y solo los compara, que es instantáneo. Es el camino para mover un
   umbral del consenso y ver el efecto, porque los umbrales del consenso no
   cambian nada de lo que midieron las capas.

   «Correr las tres capas» recorre el video tres veces, una por capa. Es el
   camino cuando todavía no hay reportes o cuando cambió algo que sí afecta a
   las capas: la región, la línea de agua o sus propios umbrales.

   Lo que el panel pinta no es un porcentaje de acierto. Es cuántos clips se
   pueden aceptar sin que nadie los mire y, sobre todo, por qué se encolan los
   demás: ese conteo de motivos es lo que dice qué ajustar. */

import { COLOR_CLASE } from "./modelo.js";

const COLOR_ESTADO = {
  aceptado: "#5fbf7f",
  discrepancia: "#e0a53c",
  sin_datos: "#3a424e",
};

export class PanelConsenso {
  constructor(nodos, { alPedirCuadro, informesDeCapas } = {}) {
    this.n = nodos;
    this.alPedirCuadro = alPedirCuadro || (() => {});
    this.informesDeCapas = informesDeCapas || (() => ({}));
    this.videoId = null;
    this.ocupado = false;
    this.informe = null;
    this.mensaje = null;
    this.error = null;
    this.regiones = [];
    this.n.btnCombinar.addEventListener("click", () => this.combinar());
    this.n.btnConsensoCompleto.addEventListener("click", () => this.correrTodo());
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

  get listo() {
    return Boolean(this.videoId) && this.regiones.length > 0 && !this.ocupado;
  }

  /** Cuántos de los tres reportes de capa están ya calculados. */
  get capasCalculadas() {
    const i = this.informesDeCapas();
    return ["movimiento", "modelo", "reglas"].filter((k) => i[k]).length;
  }

  get parametros() {
    return {
      confianza_minima_modelo: Number(this.n.consensoConfianza.value),
      margen_minimo_reglas: Number(this.n.consensoMargen.value),
      cobertura_minima_modelo: Number(this.n.consensoCobertura.value),
      minimo_votantes: Number(this.n.consensoVotantes.value),
      encolar_empates: this.n.consensoEmpates.checked,
    };
  }

  async combinar() {
    const capas = this.informesDeCapas();
    if (!capas.movimiento && !capas.reglas) {
      this.error =
        "Hace falta al menos la detección de movimiento o las reglas geométricas: " +
        "el modelo preentrenado trabaja en clips de 3 s y no define la rejilla de bloques.";
      this.informe = null;
      this.dibujar();
      return;
    }
    await this._pedir("/api/consenso", {
      video: this.videoId,
      movimiento: capas.movimiento || null,
      modelo: capas.modelo || null,
      reglas: capas.reglas || null,
      parametros: this.parametros,
    }, "Comparando los reportes que ya están en pantalla.");
  }

  async correrTodo() {
    if (!this.listo) return;
    const cuerpo = {
      regiones: this.regiones.map((r) => ({
        esquinas: r.esquinas,
        lineaAgua: r.lineaAgua,
      })),
      cuadro_referencia: this.cuadroActual,
      fps: this.fps || undefined,
      parametros: { consenso: this.parametros },
    };
    const inicio = Number(this.n.cuadroInicio.value);
    if (Number.isFinite(inicio) && inicio > 0) cuerpo.cuadro_inicio = inicio;
    await this._pedir(
      "/api/videos/" + codificar(this.videoId) + "/consenso",
      cuerpo,
      "Corriendo las tres capas sobre el video. Son tres recorridos completos " +
        "del archivo, uno por capa."
    );
  }

  async _pedir(ruta, cuerpo, mensaje) {
    this.ocupado = true;
    this.error = null;
    this.informe = null;
    this.mensaje = mensaje;
    this.dibujar();
    try {
      const resp = await fetch(ruta, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(cuerpo),
      });
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
    }
  }

  dibujar() {
    const calculadas = this.capasCalculadas;
    this.n.btnCombinar.disabled = this.ocupado || calculadas === 0;
    this.n.btnConsensoCompleto.disabled = !this.listo;
    this.n.btnCombinar.textContent = this.ocupado
      ? "Trabajando…"
      : "Combinar (" + calculadas + " de 3)";
    for (const c of [
      this.n.consensoConfianza, this.n.consensoMargen, this.n.consensoCobertura,
      this.n.consensoVotantes, this.n.consensoEmpates,
    ]) {
      c.disabled = this.ocupado;
    }

    const e = this.n.estadoConsenso;
    e.className = "estado-deteccion" + (this.error ? " error" : "");
    if (this.error) e.textContent = "No se pudo combinar: " + this.error;
    else if (this.mensaje) e.textContent = this.mensaje;
    else if (!this.videoId) e.textContent = "Selecciona un video.";
    else if (!this.regiones.length)
      e.textContent = "Dibuja al menos una región de interés (R).";
    else if (!calculadas)
      e.textContent =
        "Ninguna capa calculada todavía. Corre los paneles de arriba y usa " +
        "«Combinar», o deja que «Correr las tres capas» lo haga todo.";
    else
      e.textContent =
        calculadas + " de 3 capas calculadas. Los umbrales de este panel no " +
        "cambian lo que midieron: «Combinar» aplica el nuevo corte al instante.";

    this.n.resultadosConsenso.innerHTML = "";
    if (!this.informe) return;
    this._dibujarResumen();
    this.informe.regiones.forEach((r) => this._dibujarRegion(r));
  }

  _dibujarResumen() {
    const i = this.informe;
    const caja = document.createElement("div");
    caja.className = "resumen-deteccion";

    const t = i.totales;
    const cabecera = document.createElement("p");
    cabecera.innerHTML =
      "<strong>" + t.aceptados + "</strong> de " + t.clips +
      " clips aceptados sin revisión (" + t.porcentaje_automatico + " %), " +
      "<strong>" + t.en_cola + "</strong> a la cola de revisión" +
      (t.sin_datos ? ", " + t.sin_datos + " sin datos" : "") +
      ". Rejilla de " + i.rejilla.segundos_por_bloque + " s tomada de " +
      i.rejilla.origen + ".";
    caja.appendChild(cabecera);

    const ausentes = Object.keys(i.capas_presentes).filter((k) => !i.capas_presentes[k]);
    if (ausentes.length) {
      const p = document.createElement("p");
      p.className = "detalle";
      p.textContent = "Capas ausentes en esta corrida: " + ausentes.join(", ") + ".";
      caja.appendChild(p);
    }

    caja.appendChild(this._tablaMotivos(i.motivos));
    caja.appendChild(this._tablaAcuerdo(i.acuerdo));

    const compuerta = document.createElement("p");
    compuerta.className = i.compuerta_cordura.sospechoso ? "aviso-deteccion" : "detalle";
    compuerta.textContent = i.compuerta_cordura.mensaje;
    caja.appendChild(compuerta);

    for (const aviso of i.avisos || []) {
      const el = document.createElement("p");
      el.className = "aviso-deteccion";
      el.textContent = aviso;
      caja.appendChild(el);
    }
    this.n.resultadosConsenso.appendChild(caja);
  }

  _tablaMotivos(motivos) {
    const caja = document.createElement("div");
    if (!motivos.length) {
      const p = document.createElement("p");
      p.className = "detalle";
      p.textContent = "Ningún clip se encoló.";
      caja.appendChild(p);
      return caja;
    }
    const titulo = document.createElement("p");
    titulo.className = "detalle";
    titulo.textContent = "Por qué se encola lo que se encola:";
    caja.appendChild(titulo);
    const lista = document.createElement("dl");
    lista.className = "metadata";
    for (const m of motivos) {
      const fila = document.createElement("div");
      const dt = document.createElement("dt");
      dt.textContent = m.clips + " clips";
      const dd = document.createElement("dd");
      dd.textContent = m.explicacion;
      fila.append(dt, dd);
      lista.appendChild(fila);
    }
    caja.appendChild(lista);
    return caja;
  }

  _tablaAcuerdo(acuerdo) {
    const caja = document.createElement("div");
    const lista = document.createElement("dl");
    lista.className = "metadata";
    const nombres = {
      modelo_vs_reglas: "Modelo y reglas (tres clases)",
      movimiento_vs_modelo: "Movimiento y modelo (compatibilidad)",
      movimiento_vs_reglas: "Movimiento y reglas (compatibilidad)",
    };
    for (const clave of Object.keys(nombres)) {
      const a = acuerdo[clave];
      if (!a || !a.comparables) continue;
      const fila = document.createElement("div");
      const dt = document.createElement("dt");
      dt.textContent = nombres[clave];
      const dd = document.createElement("dd");
      dd.textContent = a.porcentaje + " % (" + a.coinciden + " de " + a.comparables + ")";
      fila.append(dt, dd);
      lista.appendChild(fila);
    }
    if (lista.children.length) caja.appendChild(lista);

    const nota = document.createElement("p");
    nota.className = "detalle";
    nota.textContent = acuerdo.nota;
    caja.appendChild(nota);
    return caja;
  }

  _dibujarRegion(r) {
    const caja = document.createElement("section");
    caja.className = "region-deteccion";

    const titulo = document.createElement("h3");
    const t = r.totales;
    titulo.innerHTML =
      "Región " + r.region + " — " +
      '<span class="pct" style="color:' + COLOR_ESTADO.aceptado + '">' +
      t.aceptados + " aceptados</span> · " +
      '<span class="pct" style="color:' + COLOR_ESTADO.discrepancia + '">' +
      t.en_cola + " en cola</span>";
    caja.appendChild(titulo);

    const tira = document.createElement("div");
    tira.className = "tira-bloques";
    for (const c of r.clips) {
      const boton = document.createElement("button");
      boton.type = "button";
      boton.className = "bloque";
      // El color lleva el estado; los aceptados llevan además el tono de su
      // conducta, para ver de un vistazo qué clase se acepta sola.
      boton.style.background =
        c.estado === "aceptado"
          ? COLOR_CLASE[c.clase_consenso] || COLOR_ESTADO.aceptado
          : COLOR_ESTADO[c.estado];
      boton.style.opacity = c.estado === "aceptado" ? 1 : 0.7;
      boton.title = this._textoDelClip(c);
      boton.addEventListener("click", () => this.alPedirCuadro(c.cuadro_inicio));
      tira.appendChild(boton);
    }
    caja.appendChild(tira);

    const detalle = document.createElement("p");
    detalle.className = "detalle";
    detalle.textContent =
      "Aceptados por conducta: " +
      Object.entries(t.conteo_aceptados).map(([k, v]) => k + " " + v).join(", ") +
      ". Pasa el cursor sobre un bloque para ver qué dijo cada capa; haz clic " +
      "para llevar el visor a su primer cuadro.";
    caja.appendChild(detalle);

    const compuerta = document.createElement("p");
    compuerta.className = r.compuerta_cordura.sospechoso ? "aviso-deteccion" : "detalle";
    compuerta.textContent = r.compuerta_cordura.mensaje;
    caja.appendChild(compuerta);

    this.n.resultadosConsenso.appendChild(caja);
  }

  _textoDelClip(c) {
    const lineas = [
      "Bloque " + c.bloque + " · " + c.segundo_inicio + "–" + c.segundo_fin + " s",
      c.estado === "aceptado"
        ? "ACEPTADO como " + c.clase_consenso
        : c.estado.toUpperCase(),
    ];
    const m = c.capas.modelo;
    lineas.push(
      "modelo: " + (m.clase || "se abstiene") +
        (m.certidumbre === null ? "" : " (" + m.certidumbre + ")") +
        (m.cobertura === undefined ? "" : ", cobertura " + m.cobertura)
    );
    const mov = c.capas.movimiento;
    lineas.push(
      "movimiento: " + (mov.salida || "sin datos") +
        (mov.cambio_medio == null ? "" : " (cambio " + mov.cambio_medio + ")")
    );
    const g = c.capas.reglas;
    lineas.push(
      "reglas: " + (g.clase || "se abstiene") +
        (g.certidumbre === null ? "" : " (margen " + g.certidumbre + ")") +
        (g.empate ? " EMPATE" : "")
    );
    if (c.explicacion.length) lineas.push("", ...c.explicacion);
    return lineas.join("\n");
  }
}

function codificar(videoId) {
  return String(videoId).split("/").map(encodeURIComponent).join("/");
}
