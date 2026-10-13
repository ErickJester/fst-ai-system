import { TOKEN_KEY } from '../config'
import * as db from './data'

// Sin backend, la cuenta de la sesión sale del token de ejemplo («mock-token-<id>»),
// como el servidor la sacaría del JWT. Sin sesión (o sin almacenamiento), null.
export function usuarioDeLaSesion() {
  try {
    const id = Number((sessionStorage.getItem(TOKEN_KEY) || '').replace('mock-token-', ''))
    return db.users.find((u) => u.id === id) || null
  } catch {
    return null
  }
}
