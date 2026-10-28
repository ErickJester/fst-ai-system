"""Carga de cuatro sesiones simultáneas contra el sistema FST (backend CHECKLIST §11: «responden en 3 s o menos»).

Cuatro cuentas distintas entran a la vez y, en bucle durante unos minutos, hacen lo que hace una persona en la web: la lista de
experimentos (con y sin filtros), el detalle, el estado de los análisis, los resultados, los totales por minuto, los bloques
de 5 s, las notificaciones y, de vez en cuando, un reporte PDF, CSV o XLSX. TODO va por la dirección que se le da (la de nginx,
con `/api` delante), igual que un navegador. Solo usa la biblioteca estándar de Python.

Uso (la PC con el sistema ya prendido, por ejemplo con portable\\iniciar.bat):

    set FST_ADMIN_PASSWORD=la-contrasena-del-administrador
    python tools/carga_4_sesiones.py --base http://localhost:8088 --admin-correo admin@lab.mx --duracion 120

  --base            dirección del sistema, sin /api al final (por omisión http://localhost:8088)
  --admin-correo    cuenta de Administrador que crea las cuatro cuentas de la prueba (la contraseña va en FST_ADMIN_PASSWORD,
                    no en la línea de comandos). Si todavía tiene la contraseña temporal, el script la cambia por la misma
                    que le des en FST_ADMIN_NUEVA (obligatoria en ese caso).
  --experimento     id del experimento con resultados que se consulta (por omisión, el primero «completado» con un Día 2
                    analizado); sin ninguno, la prueba mide solo la lista y las notificaciones.
  --duracion        segundos de la prueba (por omisión 120)
  --pausa           segundos que descansa cada sesión entre una petición y otra (por omisión 0.3)
  --limite          p95 máximo aceptable en segundos (por omisión 3.0)
  --json            guarda además la tabla en este archivo

Crea cuatro cuentas «carga46-N@fst.local» (investigadores; si ya existen, las reutiliza y les restablece la temporal) y les deja
una contraseña conocida solo por esta corrida. No borra nada: al terminar, desactívalas desde Administración si no las quieres.

Imprime por ruta: n, mediana, p95 y máximo en segundos, y los errores. Los reportes se miden aparte la 1.ª vez (cuando se genera)
y las siguientes (cuando salen guardados). Termina con código 0 si el p95 de TODAS las rutas es ≤ --limite y no hubo errores
(4xx/5xx o de red) y con 1 si no.
"""
import argparse
import json
import os
import random
import secrets
import socket
import statistics
import sys
import threading
import time
import urllib.error
import urllib.request

REPORTES = ('pdf', 'csv', 'xlsx')

# Un navegador prueba IPv6 y IPv4 a la vez; urllib prueba primero IPv6 y, si nadie escucha ahí (Docker Desktop publica en IPv4),
# en Windows espera unos 21 s antes de pasar a IPv4 con «localhost». Eso no es del sistema: aquí se usa solo IPv4 cuando existe.
_resolver = socket.getaddrinfo


def _solo_ipv4(host, *args, **kwargs):
    resultados = _resolver(host, *args, **kwargs)
    return [r for r in resultados if r[0] == socket.AF_INET] or resultados


socket.getaddrinfo = _solo_ipv4


class Sesion:
    def __init__(self, base, correo, contrasena):
        self.base, self.correo, self.contrasena, self.token = base.rstrip('/'), correo, contrasena, None

    def pedir(self, metodo, ruta, cuerpo=None, token=True):
        """Devuelve (código, cuerpo en bytes, segundos). Un error de red sale como código 0."""
        datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
        peticion = urllib.request.Request(self.base + '/api' + ruta, data=datos, method=metodo)
        if datos is not None:
            peticion.add_header('Content-Type', 'application/json')
        if token and self.token:
            peticion.add_header('Authorization', 'Bearer ' + self.token)
        inicio = time.perf_counter()
        try:
            with urllib.request.urlopen(peticion, timeout=60) as r:
                cuerpo_r, codigo = r.read(), r.status
        except urllib.error.HTTPError as e:
            cuerpo_r, codigo = e.read(), e.code
        except Exception as e:   # sin conexión, tiempo agotado...
            cuerpo_r, codigo = str(e).encode(), 0
        return codigo, cuerpo_r, time.perf_counter() - inicio

    def datos(self, metodo, ruta, cuerpo=None, token=True):
        codigo, crudo, _ = self.pedir(metodo, ruta, cuerpo, token)
        try:
            doc = json.loads(crudo)
        except ValueError:
            doc = {}
        return codigo, doc.get('data'), doc.get('error')

    def entrar(self):
        codigo, d, error = self.datos('POST', '/auth/login', {'correo': self.correo, 'contrasena': self.contrasena}, token=False)
        if codigo != 200:
            raise SystemExit('No se pudo entrar como %s: %s %s' % (self.correo, codigo, error))
        self.token = d['token']
        return d


def preparar_cuentas(base, correo_admin, clave_admin, nueva_admin, cuantas):
    admin = Sesion(base, correo_admin, clave_admin)
    d = admin.entrar()
    if d.get('cambioRequerido'):
        if not nueva_admin:
            raise SystemExit('El Administrador tiene contraseña temporal: pon la nueva en FST_ADMIN_NUEVA.')
        codigo, _, error = admin.datos('PATCH', '/auth/password', {'contrasenaActual': clave_admin, 'contrasenaNueva': nueva_admin})
        if codigo != 200:
            raise SystemExit('No se pudo cambiar la contraseña del Administrador: %s %s' % (codigo, error))
        admin.contrasena = nueva_admin
        admin.entrar()
    existentes = {c['correo']: c for c in (admin.datos('GET', '/admin/users')[1] or [])}
    sesiones = []
    for n in range(1, cuantas + 1):
        correo = 'carga46-%d@fst.local' % n
        if correo in existentes:
            codigo, d, error = admin.datos('POST', '/admin/users/%s/reset-temporal' % existentes[correo]['idInstitucional'])
        else:
            codigo, d, error = admin.datos('POST', '/admin/users', {'idInstitucional': '9046%05d' % n, 'nombre': 'Carga %d' % n,
                                                                     'apellidos': 'Prueba', 'correo': correo})
        if codigo not in (200, 201):
            raise SystemExit('No se pudo preparar la cuenta %s: %s %s' % (correo, codigo, error))
        temporal, clave = d['passwordTemporal'], 'Carga-' + secrets.token_hex(6)
        s = Sesion(base, correo, temporal)
        s.entrar()
        codigo, _, error = s.datos('PATCH', '/auth/password', {'contrasenaActual': temporal, 'contrasenaNueva': clave})
        if codigo != 200:
            raise SystemExit('No se pudo estrenar la cuenta %s: %s %s' % (correo, codigo, error))
        s.contrasena = clave
        s.entrar()
        sesiones.append(s)
    return sesiones


def experimento_con_resultados(sesion):
    pagina = 1
    while True:
        codigo, d, _ = sesion.datos('GET', '/experiments?pagina=%d' % pagina)
        if codigo != 200 or not d['items']:
            return None
        for e in d['items']:
            if e['estado'] == 'completado' and e['videosDia2']['analizados'] > 0:
                return e['idExperimento']
        if pagina * 20 >= d['total']:
            return None
        pagina += 1


def percentil(valores, p):
    ordenados = sorted(valores)
    return ordenados[min(len(ordenados) - 1, max(0, int(round(p * len(ordenados) + 0.5)) - 1))]


def main():
    ap = argparse.ArgumentParser(description='Carga de cuatro sesiones simultáneas contra el sistema FST')
    ap.add_argument('--base', default='http://localhost:8088')
    ap.add_argument('--admin-correo', required=True)
    ap.add_argument('--experimento', type=int)
    ap.add_argument('--duracion', type=float, default=120)
    ap.add_argument('--pausa', type=float, default=0.3)
    ap.add_argument('--limite', type=float, default=3.0)
    ap.add_argument('--sesiones', type=int, default=4)
    ap.add_argument('--json')
    a = ap.parse_args()
    clave = os.environ.get('FST_ADMIN_PASSWORD')
    if not clave:
        raise SystemExit('Falta la variable de entorno FST_ADMIN_PASSWORD (la contraseña del Administrador).')

    sesiones = preparar_cuentas(a.base, a.admin_correo, clave, os.environ.get('FST_ADMIN_NUEVA'), a.sesiones)
    experimento = a.experimento or experimento_con_resultados(sesiones[0])
    grupo = None
    if experimento:
        grupos = (sesiones[0].datos('GET', '/experiments/%s' % experimento)[1] or {}).get('grupos') or []
        grupo = grupos[0]['idGrupo'] if grupos else None   # el CSV y el XLSX piden ?grupo=<idGrupo>
    print('Sesiones: %d · experimento con resultados: %s · duración: %.0f s' % (len(sesiones), experimento or 'ninguno', a.duracion))

    # (nombre, ruta) de lo que hace una persona; el peso es cuántas veces entra en el sorteo.
    acciones = [('GET /experiments', '/experiments?pagina=1', 4),
                ('GET /experiments?estado=', '/experiments?pagina=1&estado=completado', 2),
                ('GET /experiments?q=', '/experiments?pagina=1&q=prueba', 2),
                ('GET /notifications', '/notifications', 3)]
    if experimento:
        e = '/experiments/%s' % experimento
        acciones += [('GET /experiments/{id}', e, 3), ('GET .../status', e + '/status', 4), ('GET .../results', e + '/results', 3),
                     ('GET .../results/byminute', e + '/results/byminute', 2), ('GET .../results/blocks', e + '/results/blocks', 2)]
    sorteo = [(n, r) for n, r, peso in acciones for _ in range(peso)]

    muestras, errores, vistos = {}, {}, set()
    candado = threading.Lock()
    fin = time.monotonic() + a.duracion

    def anotar(nombre, codigo, segundos):
        with candado:
            muestras.setdefault(nombre, []).append(segundos)
            if codigo != 200:
                errores[nombre] = errores.get(nombre, 0) + 1

    def sesion_en_bucle(s, semilla):
        azar = random.Random(semilla)
        while time.monotonic() < fin:
            if experimento and azar.random() < 0.08:
                formato = azar.choice(REPORTES)
                with candado:
                    primera = formato not in vistos
                    vistos.add(formato)
                nombre = 'GET .../report/%s (%s)' % (formato, '1.ª, se genera' if primera else 'guardado')
                extra = '' if formato == 'pdf' or grupo is None else '?grupo=%s' % grupo
                codigo, _, segundos = s.pedir('GET', '/experiments/%s/report/%s%s' % (experimento, formato, extra))
                anotar(nombre, codigo, segundos)
            else:
                nombre, ruta = azar.choice(sorteo)
                codigo, _, segundos = s.pedir('GET', ruta)
                anotar(nombre, codigo, segundos)
            time.sleep(a.pausa)

    hilos = [threading.Thread(target=sesion_en_bucle, args=(s, i)) for i, s in enumerate(sesiones)]
    for h in hilos:
        h.start()
    for h in hilos:
        h.join()

    filas, pasa = [], True
    for nombre in sorted(muestras):
        v = muestras[nombre]
        p95 = percentil(v, 0.95)
        malo = p95 > a.limite or errores.get(nombre, 0) > 0
        pasa = pasa and not malo
        filas.append({'ruta': nombre, 'n': len(v), 'mediana': round(statistics.median(v), 3), 'p95': round(p95, 3),
                      'maximo': round(max(v), 3), 'errores': errores.get(nombre, 0)})
    ancho = max(len(f['ruta']) for f in filas)
    print('\n%-*s %5s %9s %9s %9s %8s' % (ancho, 'ruta', 'n', 'mediana', 'p95', 'máximo', 'errores'))
    for f in filas:
        marca = '  <-- pasa de %.1f s o falló' % a.limite if f['p95'] > a.limite or f['errores'] else ''
        print('%-*s %5d %8.3fs %8.3fs %8.3fs %8d%s' % (ancho, f['ruta'], f['n'], f['mediana'], f['p95'], f['maximo'], f['errores'], marca))
    todas = [x for v in muestras.values() for x in v]
    print('\nTotal: %d peticiones · p95 de todas: %.3f s · máximo: %.3f s' % (len(todas), percentil(todas, 0.95), max(todas)))
    print('RESULTADO: ' + ('PASA (p95 ≤ %.1f s en todas las rutas, sin errores)' % a.limite if pasa else 'NO PASA'))
    if a.json:
        with open(a.json, 'w', encoding='utf-8') as f:
            json.dump({'base': a.base, 'duracion': a.duracion, 'sesiones': len(sesiones), 'experimento': experimento, 'filas': filas}, f,
                      ensure_ascii=False, indent=2)
    return 0 if pasa else 1


if __name__ == '__main__':
    sys.exit(main())
