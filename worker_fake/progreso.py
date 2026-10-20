"""Avance de un análisis para la pantalla de progreso: analisis/{id}/progreso.json.

El avance va en un archivo del volumen y NO en la base (el esquema es el de la guía y no se toca), con el
mismo patrón que pausa.json: el worker lo escribe aquí y el backend lo lee en GET /experiments/{id}/status.

    {"etapa": "cilindros", "porcentajeEtapa": 41, "porcentaje": 18, "actualizado": "2026-10-19T12:00:00-06:00"}

  - porcentajeEtapa: de 0 a 100 dentro de la etapa actual.
  - porcentaje: el avance GENERAL, de 0 a 99 mientras el análisis corre. El 100 solo llega con terminar(),
    cuando el análisis ya quedó completado: así nunca se ve "100 %" en un análisis que sigue procesando.
  - Nunca baja: se guarda el máximo visto de cada etapa y del general (una lectura rara del vigilante no
    hace retroceder la barra).
  - Se escribe a lo más cada INTERVALO_ESCRITURA_S, con un archivo temporal y os.replace, para que el backend
    nunca lea un archivo a medias. Un fallo al escribir se avisa una vez en el log y no detiene el análisis.
"""

import json
import os
import time

from comun import ahora, carpeta_analisis, log

ETAPAS = ("preprocesamiento", "cilindros", "clasificacion")

# Cuánto pesa cada etapa en el avance general (suman 100). MEDIDOS con un clip real de 2 minutos (1080p, 4 tubos,
# worker solo en Docker con 8 CPU, 19-oct-2026): ffprobe 0.3 s, cilindros 116 s, clasificación 1065 s
# (probar_seg 933.5 s + web.mp4 132 s), total 1181 s -> 0.03 % / 9.8 % / 90.2 %. El detalle de cada paso
# está en worker/vigilancia.py. El simulador usa los mismos pesos para que se vea igual que el real.
PESO_ETAPA = {"preprocesamiento": 1, "cilindros": 9, "clasificacion": 90}

INTERVALO_ESCRITURA_S = 2.5


class RegistroProgreso:
    def __init__(self, id_analisis, pesos=None):
        self.pesos = pesos or PESO_ETAPA
        self.archivo = carpeta_analisis(id_analisis) / "progreso.json"
        self._etapa = ETAPAS[0]
        self._maximo_etapa = {}
        self._maximo_general = 0.0
        self._ultima_escritura = 0.0
        self._avisado = False
        # Un análisis nuevo, reiniciado o reencolado empieza desde 0: se reescribe el archivo de la corrida anterior.
        self._escribir()

    def avance(self, etapa, pct_etapa, forzar=False):
        """pct_etapa: de 0 a 100 dentro de la etapa. Escribe a lo más cada INTERVALO_ESCRITURA_S, salvo si cambia
        la etapa o se pide forzar."""
        pct = max(0.0, min(100.0, float(pct_etapa)))
        self._maximo_etapa[etapa] = max(self._maximo_etapa.get(etapa, 0.0), pct)
        previas = 0.0
        for e in ETAPAS:
            if e == etapa:
                break
            previas += self.pesos[e]
        general = previas + self.pesos[etapa] * self._maximo_etapa[etapa] / 100.0
        self._maximo_general = max(self._maximo_general, min(general, 99.0))
        cambio = etapa != self._etapa
        self._etapa = etapa
        if forzar or cambio or time.monotonic() - self._ultima_escritura >= INTERVALO_ESCRITURA_S:
            self._escribir()

    def etapa_hecha(self, etapa):
        """La etapa llegó al 100 %: se escribe al momento."""
        self.avance(etapa, 100, forzar=True)

    def terminar(self):
        """El análisis quedó completado: general 100 y última etapa 100."""
        self._maximo_general = 100.0
        self._maximo_etapa[ETAPAS[-1]] = 100.0
        self._etapa = ETAPAS[-1]
        self._escribir()

    def avanzar_por_tiempo(self, etapa, segundos, paso_s=0.5):
        """Para el simulador: la etapa avanza de 0 a 100 durante `segundos`, sin hacer nada más."""
        inicio = time.monotonic()
        while True:
            transcurrido = time.monotonic() - inicio
            if transcurrido >= segundos:
                break
            self.avance(etapa, 100.0 * transcurrido / segundos)
            time.sleep(min(paso_s, segundos - transcurrido))
        self.etapa_hecha(etapa)

    def _escribir(self):
        datos = {
            "etapa": self._etapa,
            "porcentajeEtapa": int(self._maximo_etapa.get(self._etapa, 0.0)),
            "porcentaje": int(self._maximo_general),
            "actualizado": ahora().isoformat(timespec="seconds"),
        }
        temporal = self.archivo.with_suffix(".json.tmp")
        try:
            self.archivo.parent.mkdir(parents=True, exist_ok=True)
            temporal.write_text(json.dumps(datos), encoding="utf-8")
            os.replace(temporal, self.archivo)
        except OSError as e:
            if not self._avisado:
                self._avisado = True
                log.warning("No se pudo escribir el avance en %s: %s (el análisis sigue)", self.archivo, e)
        self._ultima_escritura = time.monotonic()
