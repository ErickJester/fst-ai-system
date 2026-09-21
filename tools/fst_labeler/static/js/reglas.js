/* Panel de las reglas geométricas (Módulo 6, Capa 3 simplificada).

   Envía las regiones del Módulo 3 —con su línea de agua, que aquí sí se usa—
   y pinta la clase por bloque junto con los umbrales que el servidor resolvió.

   Los umbrales se muestran siempre, con su origen y con cuánto separa cada
   corte: en automático los calcula el método de Otsu sobre la distribución del
   propio video, y Otsu devuelve un corte incluso cuando no hay dos grupos que
   separar. Sin ese dato a la vista, un porcentaje de esta capa no se puede
   interpretar. */

import { COLOR_CLASE } from "./modelo.js";

export class PanelReglas {
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
    this.regiones = [];
    this.n.btnReglas.addEventListener("click", () => this.analizar());
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

  get sinLineaDeAgua() {
    return this.regiones.filter((r) => !r.lineaAgua).length;
  }

  async analizar() {
    if (!this.listo) return;
    this.ocupado = true;
    this.error = null;
    this.informe = null;
    this.mensaje =
      "Construyendo el fondo y midiendo la forma y la posición del cuerpo en " +
      "cada cuadro. Recorre el video completo una vez.";
    this.dibujar();
    try {
      const cuerpo = {
        regiones: this.regiones.map((r) => ({
          esquinas: r.esquinas,
          lineaAgua: r.lineaAgua,
        })),
        cuadro_referencia: this.cuadroActual,
        fps: this.fps || undefined,
        parametros: {
          segundos_por_bloque: Number(this.n.reglasSegundosBloque.value) || 5,
          estabilizar: this.n.estabilizarReglas.checked,
          umbrales: {
            desplazamiento: this.n.umbralDesplazamiento.value.trim(),
            proximidad_pared: this.n.umbralPared.value.trim(),
            verticalidad: this.n.umbralVerticalidad.value.trim(),
            sobre_agua: this.n.umbralSobreAgua.value.trim(),
          },
        },
      };
      const inicio = Number(this.n.cuadroInicio.value);
      if (Number.isFinite(inicio) && inicio > 0) cuerpo.cuadro_inicio = inicio;

      const resp = await fetch(
        "/api/videos/" + codificar(this.videoId) + "/reglas-geometricas",
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
    this.n.btnReglas.disabled = !this.listo;
    this.n.btnReglas.textContent = this.ocupado ? "Trabajando…" : "Aplicar reglas";
    const campos = [
      this.n.reglasSegundosBloque,
      this.n.estabilizarReglas,
      this.n.umbralDesplazamiento,
      this.n.umbralPared,
      this.n.umbralVerticalidad,
      this.n.umbralSobreAgua,
    ];
    for (const c of campos) c.disabled = this.ocupado;

    const e = this.n.estadoReglas;
    e.className = "estado-deteccion" + (this.error ? " error" : "");
    if (this.error) e.textContent = "No se pudieron aplicar las reglas: " + this.error;
    else if (this.mensaje) e.textContent = this.mensaje;
    else if (!this.videoId) e.textContent = "Selecciona un video.";
    else if (!this.regiones.length)
      e.textContent = "Dibuja al menos una región de interés (R).";
    else {
      const faltan = this.sinLineaDeAgua;
      e.textContent =
        "Listo para " + this.regiones.length +
        (this.regiones.length === 1 ? " región" : " regiones") +
        (faltan
          ? ". " + faltan + (faltan === 1 ? " región" : " regiones") +
            " sin línea de agua (W): ahí la regla de escalamiento corre con dos " +
            "señales de tres."
          : ". Todas con línea de agua.");
    }

    this.n.resultadosReglas.innerHTML = "";
    if (!this.informe) return;
    this._dibujarUmbrales();
    this.informe.regiones.forEach((r) => this._dibujarRegion(r));
  }

  _dibujarUmbrales() {
    const i = this.informe;
    const caja = document.createElement("div");
    caja.className = "resumen-deteccion";

    const cabecera = document.createElement("p");
    cabecera.innerHTML =
      "Bloques de <strong>" + i.segundos_por_bloque + " s</strong> (" +
      i.cuadros_por_bloque + " cuadros), desde el cuadro " + i.cuadro_inicio +
      ". Conducta predominante por bloque, por voto mayoritario de los cuadros.";
    caja.appendChild(cabecera);

    const lista = document.createElement("dl");
    lista.className = "metadata";
    for (const clave of Object.keys(i.umbrales)) {
      const u = i.umbrales[clave];
      const fila = document.createElement("div");
      const nombre = document.createElement("dt");
      nombre.textContent = u.nombre;
      const valor = document.createElement("dd");
      valor.textContent =
        u.valor + " — " + u.origen +
        (u.separacion === null ? "" : " (separación " + u.separacion + ")") +
        ". Unidad: " + (i.unidades[clave] || "—") + ".";
      fila.appendChild(nombre);
      fila.appendChild(valor);
      lista.appendChild(fila);
    }
    caja.appendChild(lista);

    for (const clave of Object.keys(i.umbrales)) {
      const aviso = i.umbrales[clave].aviso;
      if (!aviso) continue;
      const el = document.createElement("p");
      el.className = "aviso-deteccion";
      el.textContent = aviso;
      caja.appendChild(el);
    }
    this.n.resultadosReglas.appendChild(caja);
  }

  _dibujarRegion(r) {
    const caja = document.createElement("section");
    caja.className = "region-deteccion";
    const p = r.porcentajes;

    const titulo = document.createElement("h3");
    titulo.innerHTML =
      "Región " + r.region + " " +
      Object.keys(p)
        .map(
          (k) =>
            '<span class="pct" style="color:' + COLOR_CLASE[k] + '">' +
            p[k] + " % " + k + "</span>"
        )
        .join(" · ");
    caja.appendChild(titulo);

    const detalle = document.createElement("p");
    detalle.className = "detalle";
    detalle.textContent =
      r.bloques_medidos + " bloques medidos de " + r.bloques_totales +
      ", margen de voto medio " + r.margen_voto_medio +
      (r.bloques_empatados ? ", " + r.bloques_empatados + " empatados" : "") +
      (r.tiene_linea_agua ? "." : ", sin línea de agua.");
    caja.appendChild(detalle);

    const tira = document.createElement("div");
    tira.className = "tira-bloques";
    for (const b of r.bloques) {
      const boton = document.createElement("button");
      boton.type = "button";
      boton.className = "bloque";
      boton.style.background = COLOR_CLASE[b.clase] || COLOR_CLASE["sin datos"];
      // La opacidad lleva el margen del voto: un bloque repartido se ve apagado.
      boton.style.opacity =
        b.clase === "sin datos" ? 1 : (0.35 + 0.65 * b.margen_voto).toFixed(2);
      const medianas = Object.entries(b.medianas)
        .map(([k, v]) => k + " " + v)
        .join(" · ");
      boton.title =
        "Bloque " + b.bloque + " · " + b.segundo_inicio + "–" + b.segundo_fin + " s\n" +
        b.clase + (b.empate ? " (empate)" : "") +
        " — voto " + Object.entries(b.votos).map(([k, v]) => k + " " + v).join(", ") +
        "\n" + b.cuadros_medidos + " cuadros medidos" +
        (b.cuadros_sin_cuerpo ? ", " + b.cuadros_sin_cuerpo + " sin cuerpo" : "") +
        (medianas ? "\n" + medianas : "");
      boton.addEventListener("click", () => this.alPedirCuadro(b.cuadro_inicio));
      tira.appendChild(boton);
    }
    caja.appendChild(tira);

    const compuerta = document.createElement("p");
    compuerta.className = r.compuerta_cordura.sospechoso
      ? "aviso-deteccion"
      : "detalle";
    compuerta.textContent = r.compuerta_cordura.mensaje;
    caja.appendChild(compuerta);

    for (const aviso of r.avisos) {
      const el = document.createElement("p");
      el.className = "aviso-deteccion";
      el.textContent = aviso;
      caja.appendChild(el);
    }
    this.n.resultadosReglas.appendChild(caja);
  }
}

function codificar(videoId) {
  return String(videoId).split("/").map(encodeURIComponent).join("/");
}
