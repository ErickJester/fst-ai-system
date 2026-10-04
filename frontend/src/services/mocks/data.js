// Datos de ejemplo de los mockups v2 con la forma que tendrá la API.
// Las pantallas no los importan: los piden a la capa de servicios (../*.js),
// que los entrega mientras VITE_USE_MOCKS no sea 'false'.
//
// estado: EN_ANALISIS | CARGA_INCOMPLETA | CONCLUIDO
// retencion_dias: días que le quedan al video crudo (null si ya se borró).
// con_detalle: solo el experimento de ejemplo tiene detalle en los mockups;
//   con la API real todos lo tendrán.

export const experiments = [
  { clave: 'EXP-2026-02', titulo: 'Compuesto CSR-14 · curva de dosis', tratamientos: 'Compuesto CSR-14 Fluoxetina Placebo', fecha_inicio: '2026-02-18', n_grupos: 4, n_especimenes: 32, videos_dia2_listos: 4, videos_dia2_cargados: 8, videos_dia2_total: 8, estado: 'EN_ANALISIS', responsable: 'M. Rivera', retencion_dias: 30, videos_borrados: false, con_detalle: true },
  { clave: 'EXP-2026-01', titulo: 'Extracto de Salvia · dosis única', tratamientos: 'Salvia', fecha_inicio: '2026-01-09', n_grupos: 4, n_especimenes: 32, videos_dia2_listos: 8, videos_dia2_cargados: 8, videos_dia2_total: 8, estado: 'CONCLUIDO', responsable: 'J. Domínguez', retencion_dias: 12, videos_borrados: false },
  { clave: 'EXP-2025-11', titulo: 'Extracto de Valeriana officinalis', tratamientos: 'Valeriana officinalis', fecha_inicio: '2025-11-14', n_grupos: 5, n_especimenes: 38, videos_dia2_listos: 10, videos_dia2_cargados: 10, videos_dia2_total: 10, estado: 'CONCLUIDO', responsable: 'M. Rivera', retencion_dias: 4, videos_borrados: false },
  { clave: 'EXP-2025-10', titulo: 'Réplica interanalista · control metodológico', tratamientos: '', fecha_inicio: '2025-10-02', n_grupos: 4, n_especimenes: 24, videos_dia2_listos: 3, videos_dia2_cargados: 3, videos_dia2_total: 6, estado: 'CARGA_INCOMPLETA', responsable: 'C. Reyes', retencion_dias: null, videos_borrados: true },
  { clave: 'EXP-2025-08', titulo: 'Fluoxetina · calibración del clasificador', tratamientos: 'Fluoxetina', fecha_inicio: '2025-08-19', n_grupos: 4, n_especimenes: 32, videos_dia2_listos: 8, videos_dia2_cargados: 8, videos_dia2_total: 8, estado: 'CONCLUIDO', responsable: 'C. Reyes', retencion_dias: null, videos_borrados: true },
]
