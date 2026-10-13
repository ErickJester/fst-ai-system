# worker_fake

Simulador del worker (sección 4 y 10 de `INSTRUCCIONES-WEB.md`). Cumple el mismo contrato que el
worker real, solo a través de la base y del volumen `videos_reportes`, pero **inventa** las conductas:
no analiza ningún video. El worker real (con el pipeline) lo reemplazará sin tocar el backend.

| Archivo | Qué hace |
|---|---|
| `worker_fake.py` | El bucle: reencola huérfanos al arrancar, revisa pausas vencidas, toma un análisis a la vez (el más antiguo) y lo lleva a `completado`, `error` o `en pausa` |
| `tareas_programadas.py` | Las tareas diarias (sección 8): borrado a 30 días, pausas huérfanas, aviso a 7 días y vigilancia del disco. Módulo aparte para que el worker real lo traiga tal cual |
| `simular_carga.py` | Hace lo que hará el backend y todavía no existe: subir un video, ajustar y cancelar una pausa |
| `comun.py` | Configuración, conexión, rutas del volumen y avisos (notificación + correo) |

Se levanta con el compose de `backend-spring/` (servicio `worker`).

## Casos de prueba

El backend guarda el video como `videos/{idVideo}.mp4`, así que el nombre original no llega al worker.
Por eso la palabra que dispara cada caso se busca en el nombre del **experimento**, del grupo o del archivo:

| Palabra | Resultado |
|---|---|
| (ninguna) | `completado`: observaciones, 300 segundos, intervalos, `presenta`, `web.mp4`, `cajas.json` y aviso |
| `falla` | `error` en la etapa de cilindros, con `diagnostico.pdf` y aviso |
| `pausa` | `en pausa` en la clasificación, con `pausa.jpg` y `pausa.json`; el worker sigue con la cola |

Desde `backend-spring/`:

```
docker compose exec worker python simular_carga.py encolar "Prueba normal" --cilindros 3
docker compose exec worker python simular_carga.py encolar "Prueba pausa"
docker compose exec worker python simular_carga.py ajustar 5      # o: cancelar 5
docker compose exec worker python simular_carga.py encolar "Prueba dia 1" --sesion "Dia 1"
docker compose exec worker python simular_carga.py estado
docker compose exec worker python tareas_programadas.py           # todas las tareas, ya
docker compose exec -e DISK_SIMULATED_PERCENT=95 worker python tareas_programadas.py --disco
```

`ajustar` y `cancelar` responden 409 si el análisis ya no está en pausa. Sin respuesta, la pausa se
cancela sola a los `PAUSE_TIMEOUT_MINUTES`. La alerta de disco va a los administradores activos; el
usuario de desarrollo no es administrador, así que sin uno solo queda un aviso en el log.

## Diferencias con el worker real

- No analiza el video: las conductas salen de un generador con semilla (el mismo análisis da siempre lo mismo).
- `web.mp4` es una copia del original, no una recodificación a H.264.
- Al retomar una pausa ajustada, termina el análisis completo; no recalcula desde el segundo de la pausa.
- `DISK_SIMULATED_PERCENT` y `WORKER_STAGE_SECONDS` solo existen en el simulador.
