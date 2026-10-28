import api from './api'
import { usaEjemplo } from './config'
import { reply, fail } from './mocks/delay'
import * as db from './mocks/data'
import { deCuenta, cuentaPublica } from './auth'
import { usuarioDeLaSesion } from './mocks/sesion'
import { ROL, ESTADO, SESION } from '../lib/contrato'

// Datos de ejemplo, salvo que esta parte ya esté conectada (VITE_API_REAL).
const USE_MOCKS = usaEjemplo('admin')

// Cuentas de administración con las formas de §6.1: { idInstitucional, nombre, apellidos,
// correo, rol, activo, nExperimentos }. deCuenta las pasa a la forma de las pantallas.

// Contraseña temporal de 12 caracteres, sin los que se confunden (0/O, 1/l/I).
function temporal() {
  const abc = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789'
  return [...crypto.getRandomValues(new Uint32Array(12))].map((n) => abc[n % abc.length]).join('')
}

const adminsActivos = () => db.users.filter((x) => x.rol === 'Administrador' && x.activo)

const mock = {
  listar() {
    return reply(db.users.map((c) => ({ ...cuentaPublica(c), nExperimentos: db.experiments.filter((e) => e.creador?.idInstitucional === c.idInstitucional).length })))
  },
  crear({ idInstitucional, nombre, apellidos, correo }) {
    const e = correo.toLowerCase()
    if (db.users.some((c) => c.correo === e)) return fail(409, 'Ya existe una cuenta con ese correo.')
    if (db.users.some((c) => c.idInstitucional === idInstitucional)) return fail(409, 'Ya existe una cuenta con ese identificador.')
    const password = temporal()
    db.users.push({ idInstitucional, nombre, apellidos, correo: e, rol: 'Investigador', activo: true, cambioRequerido: true, password })
    return reply({ passwordTemporal: password })
  },
  modificar(id, cambios) {
    const c = db.users.find((x) => x.idInstitucional === id)
    if (!c) return fail(404, 'No existe esa cuenta.')
    if (cambios.correo) {
      cambios.correo = cambios.correo.toLowerCase()
      if (db.users.some((x) => x.correo === cambios.correo && x !== c)) return fail(409, 'Ya existe una cuenta con ese correo.')
    }
    if (cambios.idInstitucional && db.users.some((x) => x.idInstitucional === cambios.idInstitucional && x !== c)) return fail(409, 'Ya existe una cuenta con ese identificador.')
    const quitaAdmin = (cambios.rol && cambios.rol !== 'Administrador') || cambios.activo === false
    if (c.rol === 'Administrador' && c.activo && quitaAdmin && adminsActivos().length <= 1) return fail(409, 'Debe existir al menos un Administrador activo.')
    Object.assign(c, cambios)
    return reply(cuentaPublica(c))
  },
  // Las mismas revisiones y en el mismo orden que el backend: 404, la propia cuenta y desactivada.
  temporal(id) {
    const c = db.users.find((x) => x.idInstitucional === id)
    if (!c) return fail(404, 'No existe esa cuenta.')
    if (usuarioDeLaSesion()?.idInstitucional === id) return fail(409, 'Para cambiar tu propia contraseña usa Mi perfil.')
    if (!c.activo) return fail(409, 'La cuenta está desactivada: reactívala antes de darle una contraseña temporal.')
    const password = temporal()
    Object.assign(c, { password, cambioRequerido: true })
    return reply({ passwordTemporal: password })
  },
}

// GET /admin/users → cuentas con nExperimentos (los que creó cada una)
export async function listUsers() {
  const d = USE_MOCKS ? await mock.listar() : (await api.get('/admin/users')).data
  return d.map(deCuenta)
}

// POST /admin/users { idInstitucional, nombre, apellidos, correo }; se crea como Investigador.
// El 201 trae { passwordTemporal } una sola vez: el servidor solo guarda su hash. Devuelve la
// cuenta creada con password_temporal, que es lo que muestra la pantalla.
export async function createUser({ nombre, apellidos, email, identificador }) {
  const datos = { idInstitucional: identificador, nombre, apellidos, correo: email.toLowerCase() }
  const d = USE_MOCKS ? await mock.crear(datos) : (await api.post('/admin/users', datos)).data
  return { ...deCuenta({ ...datos, rol: 'Investigador', activo: true, cambioRequerido: true }), password_temporal: d.passwordTemporal }
}

// PATCH /admin/users/:idInstitucional { nombre, apellidos, correo, rol } (9.6, editar cuenta). 409 si el
// correo ya existe o si deja el sistema sin administradores activos (R-16). El identificador es la llave de
// la cuenta y no se cambia: no se manda (el backend rechaza uno distinto con 400).
export async function updateUser(id, { nombre, apellidos, email, role }) {
  const cambios = { nombre, apellidos, correo: email, rol: ROL.aGuia[role] }
  const d = USE_MOCKS ? await mock.modificar(id, cambios) : (await api.patch(`/admin/users/${id}`, cambios)).data
  return deCuenta(d)
}

// POST /admin/users/:idInstitucional/reset-temporal → { passwordTemporal }. Ruta oficial (la guía
// no la trae; Erick la aceptó el 28-oct-2026). Genera otra temporal (se muestra una sola vez),
// invalida la contraseña anterior y la cuenta vuelve a pedir el cambio en su próximo acceso.
// 403 si la sesión no es de un Administrador, 404 si la cuenta no existe, 409 si es la propia
// cuenta (para eso está «Mi perfil») o si está desactivada.
export async function resetTemporal(id) {
  const d = USE_MOCKS ? await mock.temporal(id) : (await api.post(`/admin/users/${id}/reset-temporal`)).data
  return { password_temporal: d.passwordTemporal }
}

// PATCH /admin/users/:idInstitucional { activo }; el servidor impide dejar el sistema sin
// administradores (R-16).
export async function setUserActive(id, activo) {
  const d = USE_MOCKS ? await mock.modificar(id, { activo }) : (await api.patch(`/admin/users/${id}`, { activo })).data
  return deCuenta(d)
}

// GET /admin/system → disco y cola (§6). La forma no está en §6.1; propuesta: { disco,
// conductas, cola: [{ idAnalisis, idExperimento, experimento, grupo, letra, sesion, estado }] },
// la cola en orden de llegada y con el que se procesa primero.
function sistema() {
  const cola = []
  for (const exp of Object.values(db.experimentDetail)) {
    for (const g of exp.grupos) {
      for (const t of g.tandas) {
        if (t.id_analisis && (t.estado === 'RUNNING' || t.estado === 'QUEUED')) {
          cola.push({ idAnalisis: t.id_analisis, idExperimento: exp.clave, experimento: exp.titulo.split(' · ')[0], grupo: g.nombre, letra: t.letra, sesion: 'Dia 2', estado: ESTADO.aGuia[t.estado] })
        }
      }
    }
  }
  const primero = (j) => (j.estado === 'procesando' ? 0 : 1)
  cola.sort((a, b) => primero(a) - primero(b) || a.idAnalisis - b.idAnalisis)
  return reply({ ...db.system, cola })
}

export async function getSystem() {
  return USE_MOCKS ? sistema() : (await api.get('/admin/system')).data
}

// La cola de /admin/system con la forma que usa Administración: { cola: [trabajos] }.
export async function getColaSistema() {
  const d = await getSystem()
  return {
    cola: (d.cola || []).map((j, i) => ({
      job_id: j.idAnalisis, posicion: i + 1, clave: String(j.idExperimento), experimento: j.experimento, grupo: j.grupo,
      tanda: j.letra, dia: SESION.aFront[j.sesion] || 'DAY2', status: ESTADO.aFront[j.estado],
    })),
  }
}
