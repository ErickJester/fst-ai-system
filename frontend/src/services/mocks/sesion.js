import { TOKEN_KEY } from '../config'
import * as db from './data'

// Sin backend, la cuenta de la sesión sale del token de ejemplo («mock-token-<idInstitucional>»),
// como el servidor la sacaría del JWT. Sin sesión (o sin almacenamiento), null.
export function usuarioDeLaSesion() {
  try {
    const id = (localStorage.getItem(TOKEN_KEY) || '').replace('mock-token-', '')
    return db.users.find((u) => u.idInstitucional === id) || null
  } catch {
    return null
  }
}

// La cuenta de la sesión es Administrador.
export const esAdmin = (u) => u?.rol === 'Administrador'
