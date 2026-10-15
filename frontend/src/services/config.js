// Fuente de datos de la capa de servicios.
// Mientras el backend no tenga los endpoints del plan (fases 1–8), las pantallas usan
// los datos de ejemplo de ./mocks/data.js. Con VITE_USE_MOCKS=false llaman a la API.
export const USE_MOCKS = import.meta.env.VITE_USE_MOCKS !== 'false'

// Tamaño máximo de un video, en MB (MAX_UPLOAD_MB del documento; por defecto 2048). El
// servidor rechaza con 413 lo que lo pase; aquí se avisa antes de subir.
export const MAX_UPLOAD_MB = Number(import.meta.env.VITE_MAX_UPLOAD_MB) || 2048

// Token de la sesión en localStorage: lo guarda AuthContext y lo envía api.js.
export const TOKEN_KEY = 'fst.token'

// Aviso para la pantalla de login cuando el servidor rechaza una sesión vencida (401).
export const AVISO_KEY = 'fst.aviso'
