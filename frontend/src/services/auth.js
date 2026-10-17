import api from './api'
import { usaEjemplo } from './config'
import { reply, fail } from './mocks/delay'
import * as db from './mocks/data'
import { ROL } from '../lib/contrato'

// Datos de ejemplo, salvo que esta parte ya esté conectada (VITE_API_REAL).
const USE_MOCKS = usaEjemplo('sesion')

// Las respuestas siguen §6.1 (camelCase, rol «Investigador» o «Administrador»), con o sin
// backend: los datos de ejemplo ya tienen esa forma. deCuenta las pasa a la forma que usan
// las pantallas. idInstitucional es el id de la cuenta (PARA_VANESA_API.md §4).
export const deCuenta = (c) => ({
  id: c.idInstitucional,
  identificador: c.idInstitucional,
  nombre: c.nombre,
  apellidos: c.apellidos ?? '',
  email: c.correo,
  role: ROL.aFront[c.rol],
  is_active: c.activo ?? true,
  must_change_password: !!c.cambioRequerido,
  ...(c.nExperimentos != null && { n_experimentos: c.nExperimentos }),
})

// Cuenta como la devuelve la API: sin contraseña ni datos internos.
export const cuentaPublica = ({ idInstitucional, nombre, apellidos, correo, rol, activo, cambioRequerido }) =>
  ({ idInstitucional, nombre, apellidos, correo, rol, activo, cambioRequerido })

// RESET_LINK_MINUTES del documento: cuánto vale el enlace de recuperación.
const MINUTOS_ENLACE = 60

// Sin backend, un correo desconocido entra como cuenta nueva con
// contraseña temporal, para poder recorrer el primer acceso (2i).
function cuentaNueva(correo) {
  const c = { idInstitucional: String(Date.now()), nombre: correo.split('@')[0], apellidos: '', correo, rol: 'Investigador', activo: true, cambioRequerido: true }
  db.users.push(c)
  return c
}

const mock = {
  login(correo, contrasena) {
    const e = correo.trim().toLowerCase()
    const c = db.users.find((x) => x.correo === e) || cuentaNueva(e)
    if (!c.activo || !contrasena || (c.password && contrasena !== c.password)) return fail(401, 'Correo o contraseña incorrectos.')
    return reply({ token: `mock-token-${c.idInstitucional}`, ...cuentaPublica(c) })
  },
  recover(correo) {
    const c = db.users.find((x) => x.correo === correo.trim().toLowerCase() && x.activo)
    if (!c) return reply({})
    const token = crypto.getRandomValues(new Uint32Array(4)).join('').slice(0, 24)
    db.resetTokens[token] = { idInstitucional: c.idInstitucional, vence: Date.now() + MINUTOS_ENLACE * 60000, usado: false }
    // Sin correo, el enlace vuelve en la respuesta para poder abrirlo desde la pantalla.
    return reply({ demoEnlace: '/restablecer?token=' + token })
  },
  reset(token, contrasenaNueva) {
    const t = db.resetTokens[token]
    if (!t || t.usado || Date.now() > t.vence) return fail(400, 'El enlace venció o ya se usó.')
    t.usado = true
    Object.assign(db.users.find((x) => x.idInstitucional === t.idInstitucional), { password: contrasenaNueva, cambioRequerido: false })
    return reply({})
  },
  password(id, contrasenaActual, contrasenaNueva) {
    const c = db.users.find((x) => x.idInstitucional === id)
    if (!c) return fail(401, 'La sesión ya no es válida.')
    if (c.password && contrasenaActual !== c.password) return fail(400, 'La contraseña actual no es correcta.')
    Object.assign(c, { password: contrasenaNueva, cambioRequerido: false })
    return reply(cuentaPublica(c))
  },
  usersMe(id, { nombre, apellidos, correo }) {
    const c = db.users.find((x) => x.idInstitucional === id)
    if (!c) return fail(401, 'La sesión ya no es válida.')
    const e = correo.toLowerCase()
    if (db.users.some((x) => x.correo === e && x.idInstitucional !== id)) return fail(409, 'Ya existe una cuenta con ese correo.')
    Object.assign(c, { nombre, apellidos, correo: e })
    return reply(cuentaPublica(c))
  },
}

// POST /auth/login { correo, contrasena } → { token, rol, nombre, cambioRequerido } y, además
// de la guía, idInstitucional, apellidos y correo (PARA_VANESA_API.md §4).
// 401 con mensaje genérico (R-01).
export async function login(correo, contrasena) {
  const d = USE_MOCKS ? await mock.login(correo, contrasena) : (await api.post('/auth/login', { correo, contrasena })).data
  return { token: d.token, user: deCuenta(d) }
}

// POST /auth/recover { correo } → misma respuesta exista o no la cuenta; el enlace llega por
// correo. demo_enlace: solo sin backend, para abrir el enlace desde la pantalla.
export async function forgotPassword(correo) {
  const d = USE_MOCKS ? await mock.recover(correo) : (await api.post('/auth/recover', { correo })).data
  return { ok: true, demo_enlace: d?.demoEnlace }
}

// POST /auth/reset { token, contrasenaNueva }; 400 si el enlace venció o ya se usó.
export async function resetPassword(token, nueva) {
  if (USE_MOCKS) await mock.reset(token, nueva)
  else await api.post('/auth/reset', { token, contrasenaNueva: nueva })
  return { ok: true }
}

// PATCH /auth/password { contrasenaActual, contrasenaNueva } → la cuenta. También deja la
// contraseña temporal del primer acceso (2i). Los nombres del cuerpo y la respuesta no están
// en la guía: por confirmar con Erick.
export async function changePassword(id, actual, nueva) {
  const d = USE_MOCKS
    ? await mock.password(id, actual, nueva)
    : (await api.patch('/auth/password', { contrasenaActual: actual, contrasenaNueva: nueva })).data
  return deCuenta(d)
}

// PATCH /users/me { nombre, apellidos, correo } → la cuenta. 409 si el correo ya existe.
export async function updateMe(id, { nombre, apellidos, email }) {
  const cambios = { nombre, apellidos, correo: email }
  const d = USE_MOCKS ? await mock.usersMe(id, cambios) : (await api.patch('/users/me', cambios)).data
  return deCuenta(d)
}
