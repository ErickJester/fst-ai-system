export const mockUsers = [
  { id: 1, name: 'M. Sánchez', email: 'investigador@ipn.mx', role: 'investigador', initials: 'MS', active: true, experiments: 12, lastActive: '2025-03-22' },
  { id: 2, name: 'A. Ramírez', email: 'admin@ipn.mx', role: 'admin', initials: 'AR', active: true, experiments: 0, lastActive: '2025-03-22' },
  { id: 3, name: 'L. García', email: 'lgarcia@ipn.mx', role: 'investigador', initials: 'LG', active: true, experiments: 8, lastActive: '2025-03-21' },
  { id: 4, name: 'P. López', email: 'plopez@ipn.mx', role: 'investigador', initials: 'PL', active: true, experiments: 15, lastActive: '2025-03-20' },
  { id: 5, name: 'R. Martínez', email: 'rmartinez@ipn.mx', role: 'investigador', initials: 'RM', active: true, experiments: 5, lastActive: '2025-03-19' },
  { id: 6, name: 'C. Hernández', email: 'chernandez@ipn.mx', role: 'investigador', initials: 'CH', active: false, experiments: 2, lastActive: '2025-02-10' },
  { id: 7, name: 'J. Flores', email: 'jflores@ipn.mx', role: 'investigador', initials: 'JF', active: true, experiments: 3, lastActive: '2025-03-18' },
  { id: 8, name: 'D. Torres', email: 'dtorres@ipn.mx', role: 'investigador', initials: 'DT', active: true, experiments: 7, lastActive: '2025-03-22' },
]

export const mockExperiments = [
  { id: 1, name: 'Ketamina 30 mg/kg — Grupo A', owner: 'M. Sánchez', status: 'DONE', date: '2025-03-15', videos: 2 },
  { id: 2, name: 'Fluoxetina 10 mg/kg', owner: 'L. García', status: 'DONE', date: '2025-03-14', videos: 2 },
  { id: 3, name: 'Control salino — Batch 3', owner: 'P. López', status: 'RUNNING', date: '2025-03-22', videos: 1 },
  { id: 4, name: 'Imipramina 15 mg/kg', owner: 'M. Sánchez', status: 'QUEUED', date: '2025-03-22', videos: 2 },
  { id: 5, name: 'Ketamina 10 mg/kg — dosis baja', owner: 'R. Martínez', status: 'DONE', date: '2025-03-10', videos: 1 },
  { id: 6, name: 'Desipramina 20 mg/kg', owner: 'J. Flores', status: 'FAILED', date: '2025-03-18', videos: 2 },
]

export const mockDisk = {
  usedPct: 42,
  usedGB: 84,
  totalGB: 200,
  freeGB: 116,
  videosGB: 78,
  resultsGB: 2.1,
}

export const mockQueue = {
  pending: 0,
  processing: 0,
  completedToday: 5,
  errorsToday: 1,
  items: [],
}
