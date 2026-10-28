"""Manda un correo de prueba con la MISMA configuración y el MISMO código que usan los avisos del worker (enviar_correos de
comun.py) y dice si salió o por qué no. Lo llama portable\\probar_correo.bat dentro del contenedor del worker:

    python probar_correo.py destinatario@ejemplo.com

Códigos de salida: 0 el servidor de correo aceptó el mensaje, 1 no se pudo enviar (el motivo sale en pantalla), 2 falta el
destinatario o la configuración (SMTP_HOST vacío)."""
import logging
import os
import sys

import comun


class _Errores(logging.Handler):
    """enviar_correos no lanza excepciones (un correo que falla no debe tumbar al worker): solo las registra en el log del
    worker. Aquí se atrapan para poder decir qué pasó."""

    def __init__(self):
        super().__init__(level=logging.ERROR)
        self.mensajes = []

    def emit(self, record):
        self.mensajes.append(record.getMessage())


def probar(destinatario):
    """Devuelve (salio, texto). No escribe nada en la base."""
    host = os.environ.get("SMTP_HOST", "").strip()
    if not host:
        return False, ("SMTP_HOST esta vacio en .env: no hay servidor de correo configurado, asi que los avisos y la "
                       "recuperacion de contrasena no van a llegar. Llena la seccion de correo del .env y vuelve a "
                       "ejecutar iniciar.bat.")
    errores = _Errores()
    registro = logging.getLogger("worker")
    registro.addHandler(errores)
    try:
        pendientes = [(destinatario, "Prueba de correo del Sistema FST",
                       "Este es un correo de prueba del Sistema FST. Si lo lees, los avisos de los analisis y la "
                       "recuperacion de contrasena van a llegar a esta cuenta.")]
        comun.enviar_correos(pendientes)
    finally:
        registro.removeHandler(errores)
    if errores.mensajes:
        return False, "No se pudo enviar el correo. " + " | ".join(errores.mensajes)
    return True, (f"Correo de prueba enviado a {destinatario} (servidor {host}:{os.environ.get('SMTP_PORT', '1025')}). "
                  "Revisa la bandeja (y la carpeta de spam).")


def main(argumentos):
    if len(argumentos) != 2 or not argumentos[1].strip():
        print("Uso: python probar_correo.py destinatario@ejemplo.com")
        return 2
    salio, texto = probar(argumentos[1].strip())
    print(("[OK] " if salio else "[ERROR] ") + texto)
    if not salio and texto.startswith("SMTP_HOST esta vacio"):
        return 2
    return 0 if salio else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
