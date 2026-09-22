/* Editor de regiones de interés y línea de agua (Módulo 3).

   ROI = region of interest (región de interés): el contorno de una cámara de
   natación dentro del cuadro.

   Dos decisiones que condicionan todo lo que sigue:

   1. La ROI es un cuadrilátero de cuatro esquinas, no un rectángulo alineado
      a los ejes. La cámara web puede no estar nivelada, y el Módulo 5
      necesita rectificar el contorno a un rectángulo con una transformación
      de perspectiva, que exige las cuatro esquinas por separado.

   2. Todas las coordenadas se guardan en píxeles del ARCHIVO ORIGINAL. El
      cuadro que viaja al navegador puede venir reescalado y además se muestra
      al tamaño que quepa en la ventana; si las coordenadas se guardaran en
      píxeles de pantalla, quedarían atadas al tamaño del navegador y no
      servirían para medir nada.

   Las regiones se numeran de izquierda a derecha, como las ordena el montaje
   del laboratorio (hasta cuatro cámaras en una sola toma lateral). */

const MAX_REGIONES = 4;   // del montaje: hasta cuatro especímenes por toma

// Medidas de la interfaz, en píxeles de pantalla. No son umbrales de
// conducta: solo definen que tan grande se ve un tirador y desde que
// distancia se agarra con el ratón.
const RADIO_TIRADOR = 5;
const RADIO_AGARRE = 12;
const PASOS_HISTORIAL = 60;

const COLORES = ["#4a9dd8", "#e0a53c", "#5fbf7f", "#d1699b"];

export const MODO = {
  NAVEGAR: "navegar",
  DIBUJAR_ROI: "dibujar-roi",
  DIBUJAR_AGUA: "dibujar-agua",
};

export class EditorRegiones {
  constructor(lienzo, imagen, { alCambiar, numeroDe } = {}) {
    this.lienzo = lienzo;
    this.imagen = imagen;
    this.ctx = lienzo.getContext("2d");
    this.alCambiar = alCambiar || (() => {});
    // Número con que se rotula y colorea cada región. En el visor es su
    // posición de izquierda a derecha; en la revisión (Módulo 8) se muestra
    // una sola región y tiene que llevar su número verdadero.
    this.numeroDe = numeroDe || ((indice) => indice + 1);

    // Las regiones viven por video: al volver a un video ya trabajado siguen
    // ahí mientras la página no se recargue.
    this.porVideo = new Map();

    this.videoId = null;
    this.meta = null;
    this.regiones = [];
    this.seleccionada = null;
    this.modo = MODO.NAVEGAR;

    this._enCurso = [];       // esquinas o extremos ya marcados
    this._historial = [];
    this._arrastre = null;
    this._raton = null;       // posición del cursor, para la vista previa

    this._conectar();
  }

  /* --------------------------------------------------------------- video */

  sincronizar(videoId, meta) {
    if (videoId !== this.videoId) {
      this.videoId = videoId;
      this.meta = meta;
      this.regiones = videoId ? this._regionesDe(videoId) : [];
      this.seleccionada = this.regiones[0] || null;
      this.modo = MODO.NAVEGAR;
      this._enCurso = [];
      this._historial = [];
      this._notificar();
    } else if (meta !== this.meta) {
      // La metadata llega despues de abrir el video, y hasta que llega no se
      // conoce la resolucion real: sin ella no se puede dibujar nada.
      this.meta = meta;
      this._notificar();
      return;
    }
    this.redibujar();
  }

  /* Reemplaza las regiones de un video desde fuera (Módulo 8: la geometría
     con que corrieron las capas). Empieza un historial nuevo: deshacer no
     debe devolver las regiones de otro clip. */
  fijarRegiones(videoId, regiones) {
    const copia = regiones.map(clonarRegion);
    this.porVideo.set(videoId, copia);
    if (videoId !== this.videoId) return;   // se aplican al sincronizar
    this.regiones = copia;
    this.seleccionada = copia[0] || null;
    this.modo = MODO.NAVEGAR;
    this._enCurso = [];
    this._historial = [];
    this._arrastre = null;
    this._notificar();
  }

  _regionesDe(videoId) {
    if (!this.porVideo.has(videoId)) this.porVideo.set(videoId, []);
    return this.porVideo.get(videoId);
  }

  get listo() {
    return Boolean(this.videoId && this.meta && this.meta.ancho > 0);
  }

  /* ---------------------------------------------------------- conversión */

  get _escala() {
    // Píxeles de pantalla por píxel del archivo original.
    const ancho = this.imagen.clientWidth;
    const alto = this.imagen.clientHeight;
    if (!this.meta || !ancho || !alto) return null;
    return { x: ancho / this.meta.ancho, y: alto / this.meta.alto };
  }

  _aPantalla(punto) {
    const e = this._escala;
    return e ? { x: punto.x * e.x, y: punto.y * e.y } : { x: 0, y: 0 };
  }

  _aVideo(punto) {
    const e = this._escala;
    if (!e) return { x: 0, y: 0 };
    // Se acota al cuadro: una esquina fuera de la imagen no significa nada
    // para los detectores que vienen después.
    return {
      x: acotar(punto.x / e.x, 0, this.meta.ancho),
      y: acotar(punto.y / e.y, 0, this.meta.alto),
    };
  }

  _posicionDelEvento(evento) {
    const caja = this.lienzo.getBoundingClientRect();
    return { x: evento.clientX - caja.left, y: evento.clientY - caja.top };
  }

  /* -------------------------------------------------------------- modos */

  nuevaRegion() {
    if (!this.listo || this.regiones.length >= MAX_REGIONES) return;
    this.modo = MODO.DIBUJAR_ROI;
    this._enCurso = [];
    this._notificar();
  }

  nuevaLineaDeAgua() {
    if (!this.listo || !this.seleccionada) return;
    this.modo = MODO.DIBUJAR_AGUA;
    this._enCurso = [];
    this._notificar();
  }

  cancelar() {
    if (this.modo === MODO.NAVEGAR && !this._enCurso.length) return;
    this.modo = MODO.NAVEGAR;
    this._enCurso = [];
    this._notificar();
  }

  seleccionar(region) {
    this.seleccionada = region || null;
    this._notificar();
  }

  borrarSeleccionada() {
    if (!this.seleccionada) return;
    this._guardarHistorial();
    const i = this.regiones.indexOf(this.seleccionada);
    if (i >= 0) this.regiones.splice(i, 1);
    this.seleccionada = this.regiones[0] || null;
    this.modo = MODO.NAVEGAR;
    this._enCurso = [];
    this._notificar();
  }

  borrarLineaDeAgua(region) {
    const destino = region || this.seleccionada;
    if (!destino || !destino.lineaAgua) return;
    this._guardarHistorial();
    destino.lineaAgua = null;
    this._notificar();
  }

  /* ------------------------------------------------------------ historial */

  get puedeDeshacer() {
    return this._historial.length > 0;
  }

  deshacer() {
    const anterior = this._historial.pop();
    if (!anterior) return;
    const indiceSeleccion = anterior.seleccionada;
    this.regiones.length = 0;
    for (const region of anterior.regiones) this.regiones.push(region);
    this.seleccionada = indiceSeleccion === null ? null : this.regiones[indiceSeleccion];
    this.modo = MODO.NAVEGAR;
    this._enCurso = [];
    this._notificar();
  }

  /* Copia profunda del estado antes de cada cambio. Un arrastre completo
     guarda una sola vez, al empezar, para que deshacer revierta el gesto
     entero y no cada paso del ratón. */
  _guardarHistorial() {
    this._historial.push({
      regiones: this.regiones.map(clonarRegion),
      seleccionada: this.seleccionada ? this.regiones.indexOf(this.seleccionada) : null,
    });
    if (this._historial.length > PASOS_HISTORIAL) this._historial.shift();
  }

  /* ---------------------------------------------------------- interacción */

  _conectar() {
    this.lienzo.addEventListener("pointerdown", (e) => this._alPresionar(e));
    this.lienzo.addEventListener("pointermove", (e) => this._alMover(e));
    this.lienzo.addEventListener("pointerup", (e) => this._alSoltar(e));
    this.lienzo.addEventListener("pointerleave", () => {
      this._raton = null;
      this.redibujar();
    });
    // El menú contextual estorba al dibujar con clics seguidos.
    this.lienzo.addEventListener("contextmenu", (e) => e.preventDefault());
  }

  _alPresionar(evento) {
    if (!this.listo) return;
    const pantalla = this._posicionDelEvento(evento);
    const enVideo = this._aVideo(pantalla);

    if (this.modo === MODO.DIBUJAR_ROI) {
      this._enCurso.push(enVideo);
      if (this._enCurso.length === 4) {
        this._guardarHistorial();
        const region = {
          esquinas: ordenarEsquinas(this._enCurso),
          lineaAgua: null,
        };
        this.regiones.push(region);
        this._ordenarRegiones();
        this.seleccionada = region;
        this.modo = MODO.NAVEGAR;
        this._enCurso = [];
      }
      this._notificar();
      return;
    }

    if (this.modo === MODO.DIBUJAR_AGUA) {
      this._enCurso.push(enVideo);
      if (this._enCurso.length === 2 && this.seleccionada) {
        this._guardarHistorial();
        this.seleccionada.lineaAgua = this._enCurso.slice(0, 2);
        this.modo = MODO.NAVEGAR;
        this._enCurso = [];
      }
      this._notificar();
      return;
    }

    // Modo normal: agarrar un tirador, o la región entera desde dentro.
    const tirador = this._tiradorEn(pantalla);
    if (tirador) {
      this._guardarHistorial();
      this.seleccionada = tirador.region;
      this._arrastre = { ...tirador, origen: enVideo };
      this.lienzo.setPointerCapture(evento.pointerId);
      this._notificar();
      return;
    }

    const region = this._regionEn(enVideo);
    if (region) {
      this._guardarHistorial();
      this.seleccionada = region;
      this._arrastre = { tipo: "region", region, origen: enVideo };
      this.lienzo.setPointerCapture(evento.pointerId);
      this._notificar();
      return;
    }

    this.seleccionada = null;
    this._notificar();
  }

  _alMover(evento) {
    if (!this.listo) return;
    const pantalla = this._posicionDelEvento(evento);
    this._raton = this._aVideo(pantalla);

    if (!this._arrastre) {
      this.redibujar();
      return;
    }

    const destino = this._raton;
    const a = this._arrastre;
    if (a.tipo === "esquina") {
      a.region.esquinas[a.indice] = destino;
    } else if (a.tipo === "agua") {
      a.region.lineaAgua[a.indice] = destino;
    } else if (a.tipo === "region") {
      const dx = destino.x - a.origen.x;
      const dy = destino.y - a.origen.y;
      this._mover(a.region, dx, dy);
      a.origen = destino;
    }
    this.redibujar();
  }

  _alSoltar(evento) {
    if (!this._arrastre) return;
    if (this.lienzo.hasPointerCapture(evento.pointerId)) {
      this.lienzo.releasePointerCapture(evento.pointerId);
    }
    this._arrastre = null;
    // Se renumera al soltar, no durante el gesto: ver los números bailar
    // mientras se arrastra es desconcertante.
    this._ordenarRegiones();
    this._notificar();
  }

  _mover(region, dx, dy) {
    const dentro = (p) => ({
      x: acotar(p.x + dx, 0, this.meta.ancho),
      y: acotar(p.y + dy, 0, this.meta.alto),
    });
    region.esquinas = region.esquinas.map(dentro);
    if (region.lineaAgua) region.lineaAgua = region.lineaAgua.map(dentro);
  }

  _ordenarRegiones() {
    // De izquierda a derecha, como las numera el montaje del laboratorio.
    this.regiones.sort((a, b) => centroide(a.esquinas).x - centroide(b.esquinas).x);
  }

  _tiradorEn(pantalla) {
    // Se recorre al revés para que gane la región dibujada encima.
    for (let i = this.regiones.length - 1; i >= 0; i -= 1) {
      const region = this.regiones[i];
      if (region.lineaAgua) {
        for (let j = 0; j < region.lineaAgua.length; j += 1) {
          if (this._cerca(pantalla, region.lineaAgua[j])) {
            return { tipo: "agua", region, indice: j };
          }
        }
      }
      for (let j = 0; j < region.esquinas.length; j += 1) {
        if (this._cerca(pantalla, region.esquinas[j])) {
          return { tipo: "esquina", region, indice: j };
        }
      }
    }
    return null;
  }

  _cerca(pantalla, puntoVideo) {
    const p = this._aPantalla(puntoVideo);
    return Math.hypot(pantalla.x - p.x, pantalla.y - p.y) <= RADIO_AGARRE;
  }

  _regionEn(enVideo) {
    for (let i = this.regiones.length - 1; i >= 0; i -= 1) {
      if (dentroDelPoligono(enVideo, this.regiones[i].esquinas)) return this.regiones[i];
    }
    return null;
  }

  /* ------------------------------------------------------------- dibujo */

  redibujar() {
    const ctx = this.ctx;
    const ancho = this.imagen.clientWidth;
    const alto = this.imagen.clientHeight;
    if (!ancho || !alto) return;

    // El lienzo se dibuja a la resolución real de la pantalla para que las
    // líneas finas no salgan borrosas en pantallas de alta densidad.
    const densidad = window.devicePixelRatio || 1;
    if (this.lienzo.width !== Math.round(ancho * densidad) ||
        this.lienzo.height !== Math.round(alto * densidad)) {
      this.lienzo.width = Math.round(ancho * densidad);
      this.lienzo.height = Math.round(alto * densidad);
    }
    this.lienzo.style.width = ancho + "px";
    this.lienzo.style.height = alto + "px";
    ctx.setTransform(densidad, 0, 0, densidad, 0, 0);
    ctx.clearRect(0, 0, ancho, alto);
    if (!this.listo) return;

    this.regiones.forEach((region, indice) => {
      this._dibujarRegion(region, indice, region === this.seleccionada);
    });
    this._dibujarEnCurso();
  }

  _dibujarRegion(region, indice, activa) {
    const ctx = this.ctx;
    const color = this._color(indice);
    const puntos = region.esquinas.map((p) => this._aPantalla(p));

    ctx.save();
    ctx.beginPath();
    ctx.moveTo(puntos[0].x, puntos[0].y);
    for (let i = 1; i < puntos.length; i += 1) ctx.lineTo(puntos[i].x, puntos[i].y);
    ctx.closePath();
    if (activa) {
      ctx.fillStyle = color + "22";
      ctx.fill();
    }
    ctx.strokeStyle = color;
    ctx.lineWidth = activa ? 2.5 : 1.5;
    ctx.stroke();
    ctx.restore();

    if (region.lineaAgua) this._dibujarLineaDeAgua(region, color, activa);

    // Esquinas. La primera va rellena: marca el inicio del recorrido, que es
    // lo que el Módulo 5 necesita para rectificar el contorno siempre igual.
    puntos.forEach((p, i) => {
      this._tirador(p, color, i === 0, activa);
    });

    this._etiqueta(puntos[0], String(this.numeroDe(indice)), color);
  }

  _color(indice) {
    return COLORES[(this.numeroDe(indice) - 1) % COLORES.length];
  }

  _dibujarLineaDeAgua(region, color, activa) {
    const ctx = this.ctx;
    const [a, b] = region.lineaAgua.map((p) => this._aPantalla(p));

    ctx.save();
    // Prolongación tenue hasta los bordes del cuadrilátero: ayuda a juzgar si
    // la línea sigue de verdad la superficie del agua de lado a lado.
    const caja = envolvente(region.esquinas.map((p) => this._aPantalla(p)));
    const extendida = extenderSegmento(a, b, caja);
    if (extendida) {
      ctx.setLineDash([4, 4]);
      ctx.strokeStyle = color + "88";
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.moveTo(extendida[0].x, extendida[0].y);
      ctx.lineTo(extendida[1].x, extendida[1].y);
      ctx.stroke();
      ctx.setLineDash([]);
    }

    ctx.strokeStyle = color;
    ctx.lineWidth = activa ? 2.5 : 1.5;
    ctx.beginPath();
    ctx.moveTo(a.x, a.y);
    ctx.lineTo(b.x, b.y);
    ctx.stroke();
    ctx.restore();

    this._tirador(a, color, false, activa, true);
    this._tirador(b, color, false, activa, true);
  }

  _tirador(p, color, relleno, activa, redondo = false) {
    const ctx = this.ctx;
    const r = RADIO_TIRADOR + (activa ? 1 : 0);
    ctx.save();
    ctx.beginPath();
    if (redondo) ctx.arc(p.x, p.y, r, 0, Math.PI * 2);
    else ctx.rect(p.x - r, p.y - r, r * 2, r * 2);
    ctx.fillStyle = relleno ? color : "#101317";
    ctx.fill();
    ctx.strokeStyle = color;
    ctx.lineWidth = 1.5;
    ctx.stroke();
    ctx.restore();
  }

  _etiqueta(p, texto, color) {
    const ctx = this.ctx;
    ctx.save();
    ctx.font = "600 13px 'Segoe UI', system-ui, sans-serif";
    const ancho = ctx.measureText(texto).width + 10;
    ctx.fillStyle = color;
    ctx.fillRect(p.x + 8, p.y - 22, ancho, 18);
    ctx.fillStyle = "#08121a";
    ctx.textBaseline = "middle";
    ctx.fillText(texto, p.x + 13, p.y - 12);
    ctx.restore();
  }

  _dibujarEnCurso() {
    if (!this._enCurso.length) return;
    const ctx = this.ctx;
    const color = this.modo === MODO.DIBUJAR_AGUA && this.seleccionada
      ? this._color(this.regiones.indexOf(this.seleccionada))
      : COLORES[this.regiones.length % COLORES.length];
    const puntos = this._enCurso.map((p) => this._aPantalla(p));
    if (this._raton) puntos.push(this._aPantalla(this._raton));

    ctx.save();
    ctx.setLineDash([5, 4]);
    ctx.strokeStyle = color;
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.moveTo(puntos[0].x, puntos[0].y);
    for (let i = 1; i < puntos.length; i += 1) ctx.lineTo(puntos[i].x, puntos[i].y);
    if (this.modo === MODO.DIBUJAR_ROI && this._enCurso.length === 3) ctx.closePath();
    ctx.stroke();
    ctx.setLineDash([]);
    ctx.restore();

    this._enCurso.forEach((p) => this._tirador(this._aPantalla(p), color, true, true));
  }

  /* --------------------------------------------------------- notificación */

  get faltan() {
    if (this.modo === MODO.DIBUJAR_ROI) return 4 - this._enCurso.length;
    if (this.modo === MODO.DIBUJAR_AGUA) return 2 - this._enCurso.length;
    return 0;
  }

  _notificar() {
    this.redibujar();
    this.alCambiar(this);
  }
}

/* ------------------------------------------------------------ geometría */

function acotar(valor, minimo, maximo) {
  return Math.min(maximo, Math.max(minimo, valor));
}

function clonarRegion(region) {
  return {
    esquinas: region.esquinas.map((p) => ({ x: p.x, y: p.y })),
    lineaAgua: region.lineaAgua ? region.lineaAgua.map((p) => ({ x: p.x, y: p.y })) : null,
  };
}

function centroide(puntos) {
  const n = puntos.length;
  return {
    x: puntos.reduce((s, p) => s + p.x, 0) / n,
    y: puntos.reduce((s, p) => s + p.y, 0) / n,
  };
}

/* Deja las cuatro esquinas en un recorrido fijo: en el sentido de las
   manecillas del reloj, empezando por la más cercana a la esquina superior
   izquierda del cuadrilátero.

   Dos razones. Una, ordenarlas por ángulo alrededor del centro evita que
   cuatro clics en mal orden produzcan un cuadrilátero cruzado en forma de
   moño. Dos, el Módulo 5 tiene que rectificar el contorno a un rectángulo
   con una transformación de perspectiva, y eso exige saber qué esquina es
   cuál; si el orden cambiara de una región a otra, cada recorte saldría
   rotado de forma distinta. */
function ordenarEsquinas(puntos) {
  const centro = centroide(puntos);
  const porAngulo = puntos
    .map((p) => ({ p, angulo: Math.atan2(p.y - centro.y, p.x - centro.x) }))
    .sort((a, b) => a.angulo - b.angulo)
    .map((e) => e.p);

  let inicio = 0;
  let mejor = Infinity;
  porAngulo.forEach((p, i) => {
    const distancia = p.x + p.y;   // proximidad a la esquina superior izquierda
    if (distancia < mejor) {
      mejor = distancia;
      inicio = i;
    }
  });
  return porAngulo.slice(inicio).concat(porAngulo.slice(0, inicio));
}

function dentroDelPoligono(punto, poligono) {
  let dentro = false;
  for (let i = 0, j = poligono.length - 1; i < poligono.length; j = i, i += 1) {
    const a = poligono[i];
    const b = poligono[j];
    const cruza = (a.y > punto.y) !== (b.y > punto.y);
    if (cruza && punto.x < ((b.x - a.x) * (punto.y - a.y)) / (b.y - a.y) + a.x) {
      dentro = !dentro;
    }
  }
  return dentro;
}

function envolvente(puntos) {
  return {
    x0: Math.min(...puntos.map((p) => p.x)),
    y0: Math.min(...puntos.map((p) => p.y)),
    x1: Math.max(...puntos.map((p) => p.x)),
    y1: Math.max(...puntos.map((p) => p.y)),
  };
}

/* Prolonga el segmento a-b hasta los lados de la caja envolvente. */
function extenderSegmento(a, b, caja) {
  const dx = b.x - a.x;
  const dy = b.y - a.y;
  if (dx === 0 && dy === 0) return null;
  const cortes = [];
  if (dx !== 0) {
    for (const x of [caja.x0, caja.x1]) {
      const t = (x - a.x) / dx;
      cortes.push({ x, y: a.y + t * dy });
    }
  }
  if (dy !== 0) {
    for (const y of [caja.y0, caja.y1]) {
      const t = (y - a.y) / dy;
      cortes.push({ x: a.x + t * dx, y });
    }
  }
  const margen = 0.5;
  const validos = cortes.filter(
    (p) => p.x >= caja.x0 - margen && p.x <= caja.x1 + margen &&
           p.y >= caja.y0 - margen && p.y <= caja.y1 + margen
  );
  if (validos.length < 2) return null;
  // Los dos cortes más separados son los extremos de la prolongación.
  let mejor = [validos[0], validos[1]];
  let maxima = -1;
  for (let i = 0; i < validos.length; i += 1) {
    for (let j = i + 1; j < validos.length; j += 1) {
      const d = Math.hypot(validos[i].x - validos[j].x, validos[i].y - validos[j].y);
      if (d > maxima) {
        maxima = d;
        mejor = [validos[i], validos[j]];
      }
    }
  }
  return mejor;
}

export { MAX_REGIONES, COLORES };
