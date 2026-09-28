/* Subida de videos nuevos: arrastrar y soltar, o elegir archivo (ampliacion
   del Modulo 1).

   Cada archivo se sube con XMLHttpRequest y no con fetch, a proposito: fetch
   no expone el progreso de SUBIDA sin recurrir a streams manuales, y estos
   son videos de cientos de MB donde ese numero importa. Los archivos de un
   mismo arrastre se suben uno detras de otro, no en paralelo -el servidor ya
   sirve cuadros por HTTP al mismo tiempo, y no conviene competir por el
   disco con varias escrituras grandes a la vez-.

   El nombre duplicado se comprueba dos veces: aqui, contra la lista de
   videos que la pagina ya tiene en memoria, para avisar al instante sin
   gastar la subida completa; y en el servidor, que es la comprobacion que
   de verdad cuenta -la lista del navegador puede estar desactualizada si
   alguien mas toco la carpeta-. */

const EXTENSIONES = [".mp4", ".avi", ".mov", ".mkv", ".mpg", ".mpeg", ".m4v", ".wmv"];

export class PanelSubida {
  constructor(nodos, { alSubir, videosConocidos } = {}) {
    this.n = nodos;
    this.alSubir = alSubir || (() => {});
    // Funcion que devuelve los video_id ya presentes, para el aviso
    // instantaneo de duplicado. Se consulta cada vez, no se copia una vez,
    // porque la lista cambia con cada subida y con "Recargar lista".
    this.videosConocidos = videosConocidos || (() => []);
    this._conectar();
  }

  _conectar() {
    this.n.btnElegirVideo.addEventListener("click", () => this.n.archivoVideo.click());
    this.n.archivoVideo.addEventListener("change", () => {
      this._subirVarios([...this.n.archivoVideo.files]);
      this.n.archivoVideo.value = "";   // permite elegir el mismo archivo otra vez
    });

    const zona = this.n.zonaSubida;
    // dragenter y dragleave se disparan tambien al pasar sobre los hijos del
    // div (el boton, los parrafos); un contador evita que la zona "parpadee"
    // como si el arrastre hubiera salido cuando en realidad sigue dentro.
    let profundidad = 0;
    zona.addEventListener("dragenter", (evento) => {
      evento.preventDefault();
      profundidad += 1;
      zona.classList.add("arrastrando");
    });
    zona.addEventListener("dragover", (evento) => evento.preventDefault());
    zona.addEventListener("dragleave", () => {
      profundidad = Math.max(0, profundidad - 1);
      if (profundidad === 0) zona.classList.remove("arrastrando");
    });
    zona.addEventListener("drop", (evento) => {
      evento.preventDefault();
      profundidad = 0;
      zona.classList.remove("arrastrando");
      this._subirVarios([...evento.dataTransfer.files]);
    });
  }

  async _subirVarios(archivos) {
    // Secuencial a proposito (ver la nota de arriba del archivo).
    for (const archivo of archivos) await this._subirUno(archivo);
  }

  _subirUno(archivo) {
    const fila = document.createElement("li");
    fila.className = "subida-fila";
    const nombre = document.createElement("span");
    nombre.className = "subida-nombre";
    nombre.textContent = archivo.name;
    const estado = document.createElement("span");
    estado.className = "subida-estado";
    fila.append(nombre, estado);
    this.n.listaSubidas.prepend(fila);

    const extension = "." + (archivo.name.split(".").pop() || "").toLowerCase();
    if (!extension || !EXTENSIONES.includes(extension)) {
      estado.textContent = "extensión no reconocida";
      estado.classList.add("error");
      return Promise.resolve();
    }
    if (this.videosConocidos().includes(archivo.name)) {
      estado.textContent = "ya existe un video con ese nombre";
      estado.classList.add("error");
      return Promise.resolve();
    }

    estado.textContent = "0 %";
    return new Promise((resolver) => {
      const xhr = new XMLHttpRequest();
      xhr.open("POST", "/api/videos");
      xhr.upload.addEventListener("progress", (evento) => {
        if (evento.lengthComputable) {
          estado.textContent = Math.round((evento.loaded / evento.total) * 100) + " %";
        }
      });
      xhr.addEventListener("load", () => {
        let datos = null;
        try { datos = JSON.parse(xhr.responseText); } catch (error) { /* no era JSON */ }
        if (xhr.status >= 200 && xhr.status < 300 && datos) {
          estado.textContent = "listo";
          estado.classList.add("bien");
          this.alSubir(datos.video_id);
        } else {
          estado.textContent = (datos && datos.error) || "HTTP " + xhr.status;
          estado.classList.add("error");
        }
        resolver();
      });
      xhr.addEventListener("error", () => {
        estado.textContent = "error de red";
        estado.classList.add("error");
        resolver();
      });
      const cuerpo = new FormData();
      cuerpo.append("video", archivo);
      xhr.send(cuerpo);
    });
  }
}
