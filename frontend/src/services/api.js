import axios from 'axios'
import { TOKEN_KEY } from './config'

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
    if (err.response?.status === 401) {
      sessionStorage.removeItem(TOKEN_KEY)
      sessionStorage.removeItem('fst.user')
      window.location.href = '/login'
    }
    return Promise.reject(err)
  }
)

export default api

// Mensaje de error que manda el servidor, o el de respaldo si no hay.
export const mensajeError = (e, respaldo) => e.response?.data?.error || respaldo
