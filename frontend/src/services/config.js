// Fuente de datos de la capa de servicios.
// Mientras el backend no tenga los endpoints del plan (fases 1–8), las pantallas usan
// los datos de ejemplo de ./mocks/data.js. Con VITE_USE_MOCKS=false llaman a la API.
export const USE_MOCKS = import.meta.env.VITE_USE_MOCKS !== 'false'

// Interruptor por parte (PARA_VANESA_API.md §5, paso 9): con datos de ejemplo, las partes que
// lista VITE_API_REAL ya llaman al backend, para conectar una a la vez a medida que existen.
// Partes, en el orden de conexión de §9: sesion, experimentos, videos, progreso, resultados,
// revision, admin y notificaciones. Ejemplo: VITE_API_REAL=sesion,experimentos
export const PARTES_REALES = (import.meta.env.VITE_API_REAL || '').split(',').map((p) => p.trim()).filter(Boolean)
export const usaEjemplo = (parte) => USE_MOCKS && !PARTES_REALES.includes(parte)

// Tamaño máximo de un video, en MB (MAX_UPLOAD_MB del documento; por defecto 2048). El
// servidor rechaza con 413 lo que lo pase; aquí se avisa antes de subir.
export const MAX_UPLOAD_MB = Number(import.meta.env.VITE_MAX_UPLOAD_MB) || 2048

// Token de la sesión en localStorage: lo guarda AuthContext y lo envía api.js.
export const TOKEN_KEY = 'fst.token'

// Aviso para la pantalla de login cuando el servidor rechaza una sesión vencida (401).
export const AVISO_KEY = 'fst.aviso'
