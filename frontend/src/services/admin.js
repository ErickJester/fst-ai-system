import api from './api'
import { USE_MOCKS } from './config'
import { reply, fail } from './mocks/delay'
import * as db from './mocks/data'

const row = ({ password, ...u }) => u

// GET /admin/users
export async function listUsers() {
  if (USE_MOCKS) return reply(db.users.map(row))
  return (await api.get('/admin/users')).data
}

// POST /admin/users → { nombre, apellidos, email, identificador }; se crea como Investigador
// y el servidor envía la contraseña temporal por correo.
export async function createUser(data) {
  if (USE_MOCKS) {
    if (db.users.some((u) => u.email === data.email.toLowerCase())) return fail(409, 'Ya existe una cuenta con ese correo.')
    const u = { id: Date.now(), role: 'INVESTIGADOR', is_active: true, must_change_password: true, ...data, email: data.email.toLowerCase() }
    db.users.push(u)
    return reply(row(u))
  }
  return (await api.post('/admin/users', data)).data
}

// PATCH /admin/users/:id → { is_active }; el servidor impide dejar el sistema sin administradores
export async function setUserActive(id, isActive) {
  if (USE_MOCKS) {
    const u = db.users.find((x) => x.id === id)
    const admins = db.users.filter((x) => x.role === 'ADMIN' && x.is_active)
    if (!isActive && u.role === 'ADMIN' && admins.length <= 1) return fail(409, 'Debe existir al menos un Administrador activo.')
    u.is_active = isActive
    return reply(row(u))
  }
  return (await api.patch(`/admin/users/${id}`, { is_active: isActive })).data
}

// GET /admin/system → disco, cola, conductas y modelo en uso
export async function getSystem() {
  if (USE_MOCKS) return reply({ ...db.system, cola: db.queue })
  return (await api.get('/admin/system')).data
}
