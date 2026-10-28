"""Pruebas del correo de las notificaciones del worker (encargo 41): enviar_correos de comun.py contra un servidor SMTP falso
(un socket en un hilo, sin dependencias) y los asuntos de los seis tipos de aviso. Sin base de datos. Se corren dentro de la
imagen del worker: python -m unittest test_correos -v"""
import os
import re
import socketserver
import threading
import unittest
from email import message_from_bytes, policy
from pathlib import Path
from unittest import mock

import comun


class _Manejador(socketserver.StreamRequestHandler):
    """Un SMTP mínimo: EHLO, MAIL, RCPT, DATA, QUIT. Rechaza (550) a los destinatarios de servidor.rechazar."""

    def _responder(self, texto):
        self.wfile.write(texto.encode("ascii") + b"\r\n")

    def handle(self):
        servidor = self.server
        self._responder("220 prueba ESMTP")
        remitente = None
        destinatarios = []
        while True:
            linea = self.rfile.readline()
            if not linea:
                return
            comando = linea.decode("ascii", "replace").strip()
            mayusculas = comando.upper()
            if mayusculas.startswith(("EHLO", "HELO")):
                self._responder("250 prueba")
            elif mayusculas.startswith("MAIL FROM"):
                remitente = re.search(r"<(.*)>", comando).group(1)
                destinatarios = []
                self._responder("250 ok")
            elif mayusculas.startswith("RCPT TO"):
                destinatario = re.search(r"<(.*)>", comando).group(1)
                if destinatario in servidor.rechazar:
                    self._responder("550 buzon no disponible")
                else:
                    destinatarios.append(destinatario)
                    self._responder("250 ok")
            elif mayusculas == "DATA":
                self._responder("354 adelante")
                cuerpo = b""
                while True:
                    renglon = self.rfile.readline()
                    if renglon in (b".\r\n", b""):
                        break
                    cuerpo += renglon[1:] if renglon.startswith(b"..") else renglon
                with servidor.candado:
                    servidor.mensajes.append((remitente, list(destinatarios), cuerpo))
                self._responder("250 recibido")
            elif mayusculas == "QUIT":
                self._responder("221 adios")
                return
            else:
                self._responder("250 ok")


class _Servidor(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True

    def __init__(self):
        super().__init__(("127.0.0.1", 0), _Manejador)
        self.mensajes = []
        self.rechazar = set()
        self.candado = threading.Lock()


class EnvioPorCorreo(unittest.TestCase):
    def setUp(self):
        self.servidor = _Servidor()
        hilo = threading.Thread(target=self.servidor.serve_forever, daemon=True)
        hilo.start()
        self.addCleanup(self.servidor.server_close)
        self.addCleanup(self.servidor.shutdown)
        entorno = {"SMTP_HOST": "127.0.0.1", "SMTP_PORT": str(self.servidor.server_address[1]),
                   "SMTP_USER": "", "SMTP_PASSWORD": "", "SMTP_STARTTLS": "false", "SMTP_FROM": "no-reply@fst.local"}
        parche = mock.patch.dict(os.environ, entorno)
        parche.start()
        self.addCleanup(parche.stop)

    def _recibidos(self):
        return [message_from_bytes(cuerpo, policy=policy.default) for _, _, cuerpo in self.servidor.mensajes]

    def test_manda_un_mensaje_por_pendiente_con_asunto_destinatario_y_texto(self):
        pendientes = [
            ("ana@fst.mx", comun.ASUNTOS["analisis completado"], "El análisis del Día 2 de Control · Tanda A terminó."),
            ("luis@fst.mx", comun.ASUNTOS["error del pipeline"], "Falló la etapa de cilindros: no coinciden."),
        ]
        comun.enviar_correos(pendientes)

        mensajes = self._recibidos()
        self.assertEqual(2, len(mensajes))
        self.assertEqual("ana@fst.mx", mensajes[0]["To"])
        self.assertEqual("Análisis completado", mensajes[0]["Subject"])
        self.assertEqual("no-reply@fst.local", mensajes[0]["From"])
        self.assertIn("El análisis del Día 2 de Control · Tanda A terminó.", mensajes[0].get_content())
        self.assertEqual("luis@fst.mx", mensajes[1]["To"])
        self.assertEqual("Error en el análisis", mensajes[1]["Subject"])
        self.assertIn("no coinciden", mensajes[1].get_content())
        # La lista de pendientes queda vacía: lo que ya se intentó no se vuelve a mandar.
        self.assertEqual([], pendientes)

    def test_salta_los_pendientes_sin_correo(self):
        comun.enviar_correos([(None, "Asunto", "a"), ("", "Asunto", "b"), ("ana@fst.mx", "Asunto", "c")])
        mensajes = self._recibidos()
        self.assertEqual(1, len(mensajes))
        self.assertEqual("ana@fst.mx", mensajes[0]["To"])

    def test_un_correo_rechazado_solo_se_registra_y_los_demas_salen(self):
        self.servidor.rechazar.add("malo@fst.mx")
        pendientes = [("uno@fst.mx", "A", "1"), ("malo@fst.mx", "B", "2"), ("tres@fst.mx", "C", "3")]
        with self.assertLogs("worker", level="ERROR") as registro:
            comun.enviar_correos(pendientes)        # no lanza excepción
        self.assertEqual(["uno@fst.mx", "tres@fst.mx"], [m["To"] for m in self._recibidos()])
        self.assertEqual(1, len(registro.output))
        self.assertIn("No se pudo enviar el correo 'B' a malo@fst.mx", registro.output[0])
        self.assertEqual([], pendientes)

    def test_sin_servidor_no_lanza_ni_corta_el_resto(self):
        # Un puerto donde no escucha nadie: cada envío falla con ConnectionRefusedError (OSError).
        servidor_cerrado = _Servidor()
        puerto = servidor_cerrado.server_address[1]
        servidor_cerrado.server_close()
        with mock.patch.dict(os.environ, {"SMTP_PORT": str(puerto)}):
            with self.assertLogs("worker", level="ERROR") as registro:
                comun.enviar_correos([("a@fst.mx", "A", "1"), ("b@fst.mx", "B", "2")])
        self.assertEqual(2, len(registro.output))       # los dos se intentaron
        self.assertEqual([], self._recibidos())

    def test_con_usuario_inicia_sesion_y_con_starttls_lo_pide(self):
        with mock.patch.object(comun.smtplib, "SMTP") as smtp_falso, \
                mock.patch.dict(os.environ, {"SMTP_USER": "usuario", "SMTP_PASSWORD": "clave", "SMTP_STARTTLS": "true"}):
            comun.enviar_correos([("ana@fst.mx", "Asunto", "texto")])
        sesion = smtp_falso.return_value.__enter__.return_value
        sesion.starttls.assert_called_once_with()
        sesion.login.assert_called_once_with("usuario", "clave")
        sesion.send_message.assert_called_once()


class AsuntosDeLasNotificaciones(unittest.TestCase):
    def test_cada_tipo_de_notificacion_tiene_su_asunto(self):
        # Los seis tipos del dominio tipo_notificacion del esquema, sin repetirlos a mano: se leen de 001_esquema.sql.
        esquema = None
        for carpeta in (Path(__file__).resolve().parent.parent / "backend-spring" / "db" / "init",
                        Path("/data/esquema")):
            if (carpeta / "001_esquema.sql").is_file():
                esquema = (carpeta / "001_esquema.sql").read_text(encoding="utf-8")
                break
        if esquema is None:
            self.skipTest("001_esquema.sql no está disponible en esta imagen")
        dominio = re.search(r"CREATE DOMAIN tipo_notificacion AS varchar\(\d+\)\s+CHECK \(VALUE IN \((.*?)\)\);", esquema, re.S)
        tipos = set(re.findall(r"'([^']+)'", dominio.group(1)))
        self.assertEqual(6, len(tipos))
        self.assertEqual(tipos, set(comun.ASUNTOS))

    def test_los_seis_asuntos_tienen_texto(self):
        self.assertEqual(6, len(comun.ASUNTOS))
        for tipo, asunto in comun.ASUNTOS.items():
            self.assertTrue(asunto.strip(), tipo)

    def test_notificar_deja_el_correo_pendiente_con_el_asunto_del_tipo(self):
        cur = mock.Mock()
        pendientes = []
        for tipo in comun.ASUNTOS:
            comun.notificar(cur, pendientes, "1", "ana@fst.mx", tipo, f"mensaje de {tipo}", 5)
        self.assertEqual([("ana@fst.mx", asunto, f"mensaje de {tipo}") for tipo, asunto in comun.ASUNTOS.items()], pendientes)
        # Y la fila de la campanita se inserta una vez por tipo.
        self.assertEqual(6, cur.execute.call_count)


if __name__ == "__main__":
    unittest.main()
