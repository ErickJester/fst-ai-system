import { TOKEN_KEY } from '../config'
import * as db from './data'
import { ROL } from '../../lib/contrato'

// Sin backend, la cuenta de la sesión sale del token de ejemplo («mock-token-<idInstitucional>»),
// como el servidor la sacaría del JWT. Con el inicio de sesión ya conectado (VITE_API_REAL) y
// las demás partes con datos de ejemplo, sale de la sesión guardada (fst.user). Sin sesión (o
// sin almacenamiento), null.
export function usuarioDeLaSesion() {
  try {
    const token = localStorage.getItem(TOKEN_KEY) || ''
    if (token.startsWith('mock-token-')) return db.users.find((u) => u.idInstitucional === token.slice(11)) || null
    const u = JSON.parse(localStorage.getItem('fst.user') || 'null')
    if (!token || !u?.id) return null
    return db.users.find((x) => x.idInstitucional === u.id) || { idInstitucional: u.id, nombre: u.nombre, apellidos: u.apellidos, correo: u.correo, rol: ROL.aGuia[u.admin ? 'ADMIN' : 'INVESTIGADOR'], activo: true }
  } catch {
    return null
  }
}

// La cuenta de la sesión es Administrador.
export const esAdmin = (u) => u?.rol === 'Administrador'
