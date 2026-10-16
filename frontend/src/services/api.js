import axios from 'axios'
import { TOKEN_KEY, AVISO_KEY } from './config'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || 'http://localhost:8000',
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Toda respuesta JSON llega como { data, error, status } (§6): aquí se desenvuelve, una sola
// vez, para que los servicios lean res.data como el resultado. Los archivos (reportes, PDF
// de diagnóstico, imagen de la pausa) llegan tal cual y no se tocan.
export const esEnvoltorio = (d) => !!d && typeof d === 'object' && !(d instanceof Blob) && 'data' in d && 'status' in d

api.interceptors.response.use(
  (res) => {
    if (esEnvoltorio(res.data)) res.data = res.data.data
    return res
  },
  (err) => {
    // Sesión vencida o inválida: se borra y se vuelve al login con aviso. El 401 del
    // propio login (contraseña incorrecta) lo muestra la pantalla de login.
    const conSesion = !!localStorage.getItem(TOKEN_KEY)
    if (err.response?.status === 401 && conSesion && !err.config?.url?.startsWith('/auth/login')) {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem('fst.user')
      sessionStorage.setItem(AVISO_KEY, 'sesion-caducada')
      window.location.href = '/login'
    }
    // Sin permiso para esa sección (por ejemplo, /admin sin ser administrador).
    if (err.response?.status === 403 && conSesion) {
      sessionStorage.setItem(AVISO_KEY, 'sin-permiso')
      window.location.href = '/experimentos'
    }
    return Promise.reject(err)
  }
)

export default api

// Petición cancelada por quien la hizo (AbortController), como al cancelar una subida.
export const esCancelacion = (e) => e?.code === 'ERR_CANCELED'

// Mensaje de error que manda el servidor (campo «error» del envoltorio, §6), o el de
// respaldo si no hay.
export function mensajeError(e, respaldo) {
  const d = e?.response?.data
  return d && typeof d === 'object' && typeof d.error === 'string' && d.error ? d.error : respaldo
}
