import axios from 'axios'
import { TOKEN_KEY, AVISO_KEY } from './config'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || 'http://localhost:8000',
})

api.interceptors.request.use((config) => {
  const token = sessionStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (res) => res,
  (err) => {
    // Sesión vencida o inválida: se borra y se vuelve al login con aviso. El 401 del
    // propio login (contraseña incorrecta) lo muestra la pantalla de login.
    const conSesion = !!sessionStorage.getItem(TOKEN_KEY)
    if (err.response?.status === 401 && conSesion && !err.config?.url?.startsWith('/auth/login')) {
      sessionStorage.removeItem(TOKEN_KEY)
      sessionStorage.removeItem('fst.user')
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

// Mensaje de error que manda el servidor, o el de respaldo si no hay.
export const mensajeError = (e, respaldo) => e.response?.data?.error || respaldo
