# Sistema FST: versión portable

Esta carpeta es el sistema completo. Se lleva a otra computadora con Windows, se le da doble clic a un archivo y
queda funcionando: entras, creas un experimento, subes un video y ves el análisis.

## Qué necesitas

- Windows 10 u 11.
- **Docker Desktop** instalado (es gratis: docker.com). Se instala una vez y se deja corriendo.
- **Memoria:** 16 GB de RAM en la computadora, y en Docker Desktop (Configuración, Recursos) al menos 8 GB
  asignados. El análisis de un video usa hasta unos 3.5 GB; lo demás del sistema, menos de 1 GB.
- **Disco:** 20 GB libres para empezar. El sistema ocupa unos 4.5 GB, y cada video que subas ocupa cerca de 1 GB
  (más unos 200 MB cuando se analiza).
- **Internet** la primera vez (descarga lo que falta). Después ya no hace falta.

## Instalación (una sola vez)

1. Copia la carpeta completa a la computadora. Debe verse así, con `modelos` junto a `portable`:
   `fst-portable\portable`, `fst-portable\modelos`, `fst-portable\backend-spring`, etc.
2. Abre Docker Desktop y espera a que diga que está corriendo.
3. Entra a la carpeta `portable` y dale doble clic a **iniciar.bat**.
4. La primera vez se abre un archivo de texto (`.env`). Llena SOLO la parte de la cuenta de administrador
   (las líneas `ADMIN_...`): tu boleta o número de empleado, tu nombre, tus apellidos, tu correo y una contraseña
   temporal. Guarda y cierra.
5. Vuelve a darle doble clic a **iniciar.bat**. La primera vez tarda de 20 a 40 minutos o más, según tu internet
   (descarga e instala todo). No cierres la ventana. Las siguientes veces tarda menos de un minuto.
6. Cuando diga "Listo", se abre el navegador en **http://localhost:8088**.

## Primer ingreso

1. Entra con tu correo y la contraseña temporal que escribiste en el paso 4.
2. El sistema te pide **cambiar la contraseña** antes de usar nada. Elige una nueva (mínimo 8 caracteres).
3. Listo: ya estás en la lista de experimentos.

## Subir un video

1. Crea un experimento ("Nuevo experimento").
2. Entra al experimento y usa el botón para cargar videos.
3. Escribe el grupo (por ejemplo "Control"), elige la sesión y el archivo.
   - **Día 1** es la habituación: el video se guarda pero **no se analiza**.
   - **Día 2** es la prueba: este video **sí se analiza**.
4. El video debe ser **horizontal** (los cilindros de lado a lado) y de tipo **.mp4 o .mov**. Si no, el sistema
   lo rechaza y te explica por qué.
5. Un video de 1 GB tarda alrededor de un minuto en subirse.

## Cuánto tarda un análisis

Mucho: **de 40 minutos a más de una hora** por video de unos 6 minutos, porque la computadora trabaja sin tarjeta
gráfica. Un video corto de 25 segundos tarda unos 5. El sistema analiza **un video a la vez**; los demás esperan su
turno. Puedes cerrar el navegador: el análisis sigue, y al terminar aparece un aviso en la campana de la página.

## Apagar y volver a prender

- Para apagar: doble clic a **detener.bat**. No borra nada.
- Para volver a prender: doble clic a **iniciar.bat**.
- Si apagas la computadora con un análisis a medias, se reinicia solo la próxima vez que prendas el sistema.

## Dónde quedan los datos

En Docker Desktop (en "volúmenes"), no en esta carpeta. **No se borran al apagar**, ni al apagar la computadora.
No uses "Delete" sobre los volúmenes de `fst-portable` en Docker Desktop salvo que quieras borrar todo.

## Si algo falla

- "Docker no está respondiendo": abre Docker Desktop y espera a que diga que está corriendo.
- El puerto 8088 está ocupado: abre `.env`, cambia `PUERTO_WEB=8088` por otro número (por ejemplo 8090) y vuelve a
  darle a iniciar.bat. La dirección será `http://localhost:` con ese número.
- Un análisis terminó en "error": en la pantalla del experimento puedes ver su reporte de diagnóstico.
- Para ver qué pasó por dentro, abre una ventana de comandos en la carpeta `portable` y escribe
  `docker compose logs worker` (el que analiza) o `docker compose logs api` (el servidor).

## Qué NO hace todavía

- Crear más cuentas de usuario (por ahora solo existe la del administrador).
- Recuperar la contraseña por correo.
- La revisión de la conducta segundo a segundo.
- Descargar los resultados en CSV y XLSX (el PDF del reporte sí funciona).
- La pausa cuando no se ve a un espécimen.
- La gráfica de "Bloques de 5 s" de los resultados.
- Avisos por correo: los avisos salen solo en la campana de la página.
