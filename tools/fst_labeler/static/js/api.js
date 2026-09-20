/* Cliente de la interfaz HTTP del servidor de cuadros (Módulo 1).
   Es la única parte de la interfaz que conoce las rutas del servidor. */

async function pedirJson(url) {
  const respuesta = await fetch(url, { headers: { Accept: "application/json" } });
  let datos = null;
  try {
    datos = await respuesta.json();
  } catch (e) {
    datos = null;
  }
  if (!respuesta.ok) {
    const detalle = datos && datos.error ? datos.error : `HTTP ${respuesta.status}`;
    const error = new Error(detalle);
    error.tipo = datos && datos.tipo ? datos.tipo : "http";
    error.estado = respuesta.status;
    throw error;
  }
  return datos;
}

export class ClienteFST {
  /* Preferencias por defecto del visor, definidas del lado del servidor. */
  preferencias() {
    return pedirJson("/api/preferencias");
  }

  /* Videos disponibles en la carpeta configurada. No los abre ni decodifica. */
  listarVideos() {
    return pedirJson("/api/videos");
  }

  /* Metadata leída del archivo real. Con conteoExacto el servidor recorre el
     archivo completo contando cuadros decodificables (tarda, pero es exacto). */
  metadata(videoId, { conteoExacto = false } = {}) {
    const ruta = `/api/videos/${codificarId(videoId)}/metadata`;
    return pedirJson(conteoExacto ? `${ruta}?conteo_exacto=1` : ruta);
  }

  /* URL del cuadro `indice` (base 0) como JPEG. La imagen se carga con un
     elemento Image para que el navegador aproveche su propio caché. */
  urlCuadro(videoId, indice, { calidad, maxAncho } = {}) {
    const parametros = new URLSearchParams();
    if (calidad) parametros.set("calidad", String(calidad));
    if (maxAncho) parametros.set("max_ancho", String(maxAncho));
    const consulta = parametros.toString();
    const base = `/api/videos/${codificarId(videoId)}/frame/${indice}`;
    return consulta ? `${base}?${consulta}` : base;
  }
}

/* El identificador de video es una ruta relativa con barras (por ejemplo
   "sesion_03/especimen_07.mp4"); las barras se conservan y el resto se
   codifica para que nombres con espacios o acentos no rompan la URL. */
function codificarId(videoId) {
  return String(videoId).split("/").map(encodeURIComponent).join("/");
}
