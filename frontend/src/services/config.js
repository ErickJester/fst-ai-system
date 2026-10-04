// Fuente de datos de la capa de servicios.
// Mientras el backend no tenga los endpoints del plan (fases 1–8), las pantallas usan
// los datos de ejemplo de ./mocks/data.js. Con VITE_USE_MOCKS=false llaman a la API.
export const USE_MOCKS = import.meta.env.VITE_USE_MOCKS !== 'false'
