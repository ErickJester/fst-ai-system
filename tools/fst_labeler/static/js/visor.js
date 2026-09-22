/* Motor del visor cuadro a cuadro (Módulo 2).

   Responsabilidades:
     - pedir un cuadro concreto y mostrarlo sin que se cuele uno más viejo,
     - pedir por adelantado los cuadros que vienen,
     - reproducir de corrido a la velocidad real del video.

   Nada de lo que hay aquí supone FPS (cuadros por segundo), resolución ni
   total de cuadros: todo sale de la metadata del archivo, y si el archivo no
   lo declara se marca como desconocido y se pide al usuario.

   La reproducción se rige por reloj de pared, no por un contador de cuadros:
   si los cuadros no alcanzan a llegar por la red, se omiten los intermedios
   antes que estirar el tiempo. Así 10 s de reproducción son 10 s del video, y
   los cuadros omitidos se reportan en lugar de disimularse. */

const ORIGEN_ARCHIVO = "leído del archivo";
const ORIGEN_MANUAL = "indicado a mano";
const ORIGEN_DUDOSO = "declarado por el archivo, pero no confiable";
const ORIGEN_AUSENTE = "el archivo no lo declara";

export class Visor {
  constructor(cliente, imagen, { alCambiar } = {}) {
    this.cliente = cliente;
    this.imagen = imagen;
    this.alCambiar = alCambiar || (() => {});

    this.videoId = null;
    this.meta = null;
    this.cuadroActual = null;

    this.fps = null;            // valor efectivo usado para tiempo y velocidad
    this.fpsOrigen = null;
    this.totalCuadros = null;   // declarado o exacto, según la metadata
    this.totalExacto = false;
    // Último cuadro que el servidor entregó de verdad. Sirve cuando el
    // contenedor no declara el total o lo declara de más.
    this.ultimoCuadroValido = null;
    // Se pone en true cuando el servidor ya rechazó un cuadro: a partir de
    // ahí se sabe dónde termina el video de verdad.
    this._agotado = false;

    this.calidad = null;
    this.maxAncho = null;
    this.prefetch = 8;

    this.reproduciendo = false;
    this.velocidad = 1;
    // Tramo que la reproducción repite en bucle: el clip en revisión del
    // Módulo 8. null reproduce el video entero, como en el Módulo 2.
    this.rango = null;
    this.cargando = false;
    this.omitidos = 0;
    this.mensaje = null;

    this._ficha = 0;            // descarta respuestas de peticiones superadas
    this._raf = null;
    this._t0 = 0;
    this._cuadroBase = 0;
    this._enVuelo = false;
    this._pasoObservado = 1;    // cuantos cuadros avanza de verdad cada paso
    this._colaPrefetch = [];
    this._fichaPrefetch = 0;
    this._precargaActiva = null; // referencia viva: sin ella el navegador
                                 // puede descartar la descarga por adelantado
  }

  /* ------------------------------------------------------------- video */

  async abrirVideo(videoId) {
    this.pausar();
    this._ficha += 1;
    this.videoId = videoId;
    this.meta = null;
    this.cuadroActual = null;
    this.ultimoCuadroValido = null;
    this._agotado = false;
    this.omitidos = 0;
    this.mensaje = null;
    this.imagen.removeAttribute("src");
    this._notificar();

    const meta = await this.cliente.metadata(videoId);
    if (this.videoId !== videoId) return null;   // cambiaron de video mientras tanto
    this._aplicarMetadata(meta);
    this._notificar();

    await this.irA(0);
    return this.meta;
  }

  async verificarTotal() {
    if (!this.videoId) return null;
    const videoId = this.videoId;
    const meta = await this.cliente.metadata(videoId, { conteoExacto: true });
    if (this.videoId !== videoId) return null;
    this._aplicarMetadata(meta, { conservarFps: true });
    this.ultimoCuadroValido = null;
    this._agotado = false;
    this.mensaje = null;
    this._notificar();
    return this.meta;
  }

  _aplicarMetadata(meta, { conservarFps = false } = {}) {
    this.meta = meta;
    this.totalCuadros = meta.total_cuadros;
    this.totalExacto = Boolean(meta.total_cuadros_exacto);

    if (!conservarFps || this.fpsOrigen !== ORIGEN_MANUAL) {
      if (meta.fps && meta.fps_confiable) {
        this.fps = meta.fps;
        this.fpsOrigen = ORIGEN_ARCHIVO;
      } else {
        // No se inventa un valor: sin FPS confiable no hay velocidad real.
        this.fps = null;
        this.fpsOrigen = meta.fps ? ORIGEN_DUDOSO + " (" + meta.fps + ")" : ORIGEN_AUSENTE;
      }
    }
  }

  fijarFpsManual(valor) {
    const numero = Number(valor);
    if (!Number.isFinite(numero) || numero <= 0) return false;
    this.fps = numero;
    this.fpsOrigen = ORIGEN_MANUAL;
    this._notificar();
    return true;
  }

  fijarTransporte({ calidad, maxAncho } = {}) {
    if (calidad) this.calidad = calidad;
    if (maxAncho) this.maxAncho = maxAncho;
  }

  /* ------------------------------------------------------- navegación */

  /* Último índice accesible según lo que se sabe hoy; null si no se sabe.
     Orden de confianza: conteo verificado > final detectado al fallar una
     lectura > total declarado por el contenedor. `ultimoCuadroValido` por si
     solo no sirve: es el cuadro mas lejano ya visto, no el final del video. */
  get ultimoCuadro() {
    if (this.totalExacto && this.totalCuadros) return this.totalCuadros - 1;
    if (this._agotado && this.ultimoCuadroValido !== null) return this.ultimoCuadroValido;
    if (this.totalCuadros) return this.totalCuadros - 1;
    return null;
  }

  /* Tope duro para acotar lo que pide el usuario. El total declarado por el
     contenedor puede mentir, así que solo sirve de tope cuando está
     verificado; si no, el tope real lo marca el primer cuadro que el
     servidor no pudo entregar. */
  get _topeNavegable() {
    if (this.totalExacto && this.totalCuadros) return this.totalCuadros - 1;
    if (this.ultimoCuadroValido !== null && this._agotado) return this.ultimoCuadroValido;
    return null;
  }

  _acotar(indice) {
    let n = Math.round(Number(indice));
    if (!Number.isFinite(n)) return 0;
    if (n < 0) n = 0;
    const tope = this._topeNavegable;
    if (tope !== null && n > tope) n = tope;
    return n;
  }

  async irA(indice) {
    if (!this.videoId) return;
    const destino = this._acotar(indice);
    const ficha = ++this._ficha;
    const url = this.cliente.urlCuadro(this.videoId, destino, {
      calidad: this.calidad,
      maxAncho: this.maxAncho,
    });

    this.cargando = true;
    this._notificar();
    try {
      await cargarImagen(url);
    } catch (error) {
      if (ficha !== this._ficha) return;
      this.cargando = false;
      // El servidor no pudo entregar ese cuadro: se toma el anterior como
      // último cuadro real del video y se avisa, en lugar de fingir.
      if (destino > 0) {
        this._agotado = true;
        this.ultimoCuadroValido = destino - 1;
        this.mensaje =
          "El cuadro " + destino + " no se pudo leer; se toma " + (destino - 1) +
          " como último cuadro del video. Verifica el total para confirmarlo.";
        this.pausar();
        this._notificar();
        await this.irA(destino - 1);
        return;
      }
      this.mensaje = "No se pudo leer el primer cuadro de este video: " + error.message;
      this.pausar();
      this._notificar();
      return;
    }

    if (ficha !== this._ficha) return;   // llegó tarde: ya se pidió otro cuadro
    this.imagen.src = url;
    this.cuadroActual = destino;
    this.cargando = false;
    if (this.ultimoCuadroValido === null || destino > this.ultimoCuadroValido) {
      this.ultimoCuadroValido = destino;
    }
    this._notificar();
    this._pedirPorAdelantado(destino);
  }

  avanzar(cuadros = 1) {
    if (this.cuadroActual === null) return;
    this.pausar();
    return this.irA(this.cuadroActual + cuadros);
  }

  alInicio() {
    this.pausar();
    return this.irA(0);
  }

  alFinal() {
    this.pausar();
    const tope = this.ultimoCuadro;
    if (tope === null) return;
    return this.irA(tope);
  }

  /* Pide por adelantado los cuadros que siguen.

     De uno en uno y hacia delante, a proposito: el servidor mantiene un solo
     lector por video y lo serializa con un candado, asi que pedir en paralelo
     no lo hace mas rapido y ademas las peticiones pueden llegarle
     desordenadas, obligandolo a retroceder. Retroceder es lo caro en un
     contenedor comprimido; avanzar decodificando es barato.

     El paso es el que la reproduccion esta usando de verdad, no el nominal:
     si se omiten cuadros para sostener la velocidad real, adelantar los
     inmediatos no sirve de nada. */
  _pedirPorAdelantado(desde) {
    if (this.prefetch <= 0) return;
    const paso = this.reproduciendo ? Math.max(1, this._pasoObservado) : 1;
    const tope = this.ultimoCuadro;
    const objetivos = [];
    for (let i = 1; i <= this.prefetch; i += 1) {
      const indice = desde + i * paso;
      if (tope !== null && indice > tope) break;
      objetivos.push(indice);
    }
    this._colaPrefetch = objetivos;
    this._fichaPrefetch += 1;
    this._seguirPrefetch(this._fichaPrefetch);
  }

  _seguirPrefetch(ficha) {
    if (ficha !== this._fichaPrefetch) return;   // hubo una navegacion nueva
    const indice = this._colaPrefetch.shift();
    if (indice === undefined) return;
    const imagen = new Image();
    this._precargaActiva = imagen;
    const seguir = () => this._seguirPrefetch(ficha);
    imagen.onload = seguir;
    imagen.onerror = seguir;
    imagen.src = this.cliente.urlCuadro(this.videoId, indice, {
      calidad: this.calidad,
      maxAncho: this.maxAncho,
    });
  }

  /* ------------------------------------------------------ reproducción */

  puedeReproducir() {
    return Boolean(this.videoId) && Boolean(this.fps) && this.cuadroActual !== null;
  }

  alternarReproduccion() {
    if (this.reproduciendo) this.pausar();
    else this.reproducir();
  }

  reproducir() {
    if (this.reproduciendo || !this.puedeReproducir()) return;
    // Punto de arranque: el inicio del tramo si se está fuera de él, o el
    // principio del video si ya se estaba al final. El reloj se ancla en ese
    // cuadro y no en el actual, o el primer paso saltaría de vuelta al final.
    let base = this.cuadroActual;
    const tope = this.ultimoCuadro;
    if (this.rango && (base < this.rango.inicio || base >= this.rango.fin)) {
      base = this.rango.inicio;
    } else if (!this.rango && tope !== null && base >= tope) {
      base = 0;
    }
    this.reproduciendo = true;
    this.omitidos = 0;
    this.mensaje = null;
    this._cuadroBase = base;
    this._t0 = performance.now();
    this._enVuelo = false;
    this._pasoObservado = 1;
    if (base !== this.cuadroActual) {
      this._enVuelo = true;
      this.irA(base).finally(() => { this._enVuelo = false; });
    }
    this._notificar();
    this._raf = requestAnimationFrame(() => this._paso());
  }

  pausar() {
    if (this._raf !== null) {
      cancelAnimationFrame(this._raf);
      this._raf = null;
    }
    if (!this.reproduciendo) return;
    this.reproduciendo = false;
    this._notificar();
  }

  fijarVelocidad(valor) {
    const numero = Number(valor);
    if (!Number.isFinite(numero) || numero <= 0) return;
    this.velocidad = numero;
    if (this.reproduciendo) {
      // Se reancla el reloj en el cuadro actual para que el cambio de
      // velocidad no provoque un salto de posición.
      this._cuadroBase = this.cuadroActual;
      this._t0 = performance.now();
    }
    this._notificar();
  }

  _paso() {
    if (!this.reproduciendo) return;

    const transcurrido = (performance.now() - this._t0) / 1000;
    const objetivo = this._cuadroBase + Math.floor(transcurrido * this.fps * this.velocidad);
    const tope = this.ultimoCuadro;

    if (this.rango && objetivo > this.rango.fin) {
      // Fin del tramo: vuelve a empezar. Quien revisa suele necesitar ver el
      // clip más de una vez antes de decidir.
      if (!this._enVuelo) {
        this._cuadroBase = this.rango.inicio;
        this._t0 = performance.now();
        this._enVuelo = true;
        this.irA(this.rango.inicio).finally(() => { this._enVuelo = false; });
      }
      this._raf = requestAnimationFrame(() => this._paso());
      return;
    }

    if (tope !== null && objetivo >= tope) {
      this._enVuelo = true;
      this.irA(tope).finally(() => {
        this._enVuelo = false;
        this.pausar();
        this.mensaje = "Fin del video.";
        this._notificar();
      });
      return;
    }

    if (!this._enVuelo && objetivo > this.cuadroActual) {
      // Los cuadros entre el actual y el objetivo no alcanzaron a mostrarse:
      // se cuentan y se reportan, no se disimulan.
      this.omitidos += objetivo - this.cuadroActual - 1;
      this._pasoObservado = objetivo - this.cuadroActual;
      this._enVuelo = true;
      this.irA(objetivo).finally(() => { this._enVuelo = false; });
    }

    this._raf = requestAnimationFrame(() => this._paso());
  }

  /* -------------------------------------------------------- utilidades */

  segundosDe(indice) {
    if (!this.fps || indice === null || indice === undefined) return null;
    return indice / this.fps;
  }

  _notificar() {
    this.alCambiar(this);
  }
}

/* Carga la imagen en un elemento aparte y solo entonces la pasa al visor.
   Como el servidor marca los cuadros como cacheables, asignar después la
   misma URL al elemento visible sale del caché y no parpadea. */
function cargarImagen(url) {
  return new Promise((resolver, rechazar) => {
    const imagen = new Image();
    imagen.onload = () => resolver(imagen);
    imagen.onerror = () => rechazar(new Error("el servidor no entregó el cuadro"));
    imagen.src = url;
  });
}
