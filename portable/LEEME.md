# Sistema FST: versión portable (producción)

Esta carpeta es el sistema completo, listo para dejarse funcionando en **una computadora del laboratorio** con Windows y
Docker Desktop. Se instala con imágenes ya construidas (no necesita internet ni construir nada), se le da doble clic a un
archivo y queda funcionando: tú entras desde esa PC, y las demás PCs de la red del laboratorio entran con su dirección.

## Qué necesitas

- Windows 10 u 11.
- **Docker Desktop** instalado (es gratis: docker.com). Se instala una vez. Conviene activar en su configuración
  «Start Docker Desktop when you sign in» (que arranque al iniciar sesión), para que el sistema vuelva solo después de
  reiniciar la PC.
- **Memoria:** 16 GB de RAM en la computadora, y en Docker Desktop (Configuración, Recursos) **al menos 8 GB** asignados.
  El análisis de un video usa hasta unos 3.5 GB (el sistema le pone un tope de 5 GB), el servidor hasta 1.5 GB y lo demás
  menos de 1 GB.
- **Disco:** 20 GB libres para empezar. Las imágenes ocupan unos 5 GB, y cada video que subas ocupa cerca de 1 GB
  (más unos 200 MB cuando se analiza).
- **Internet:** NO hace falta para instalar ni para usar el sistema (solo para el correo, si lo configuras).
- **La PC no debe suspenderse** mientras el sistema esté en uso: en Configuración, Sistema, Inicio/apagado y suspensión
  (o Opciones de energía), pon «Suspender: nunca» con la PC conectada. Una PC suspendida no responde a las demás, y un
  análisis a medias se pausa.

## Qué trae el paquete

El administrador del sistema te entrega **dos archivos**:

- `fst-portable-X.Y.Z.zip`: el paquete chico (menos de 150 MB): el compose, esta carpeta `portable`, el modelo de análisis
  y el esquema de la base.
- `fst-imagenes-X.Y.Z.tar` (o `.tar.gz`): las imágenes de Docker ya construidas (poco más de 1 GB el archivo; ya cargadas en
  Docker ocupan unos 5 GB): el servidor, el worker que analiza los videos, la página y la base de datos. `X.Y.Z` es la versión; los dos archivos tienen que ser de la **misma**
  versión.

## Instalación (una sola vez)

1. Descomprime el `.zip` en una carpeta de la PC, por ejemplo `C:\fst-portable`. Debe verse así, con `modelos` y
   `docker-compose.yml` junto a `portable`: `C:\fst-portable\portable`, `C:\fst-portable\modelos`,
   `C:\fst-portable\docker-compose.yml`, `C:\fst-portable\VERSION`, etc. El `docker-compose.yml` de esa carpeta es el compose
   definitivo del sistema: **no lo ejecutes a mano**, se usa con `portable\iniciar.bat`.
2. Copia el archivo `fst-imagenes-X.Y.Z.tar` **dentro de esa misma carpeta** (junto a `docker-compose.yml`).
3. Abre Docker Desktop y espera a que diga que está corriendo.
4. Entra a la carpeta `portable` y dale doble clic a **iniciar.bat**.
5. La primera vez se abre un archivo de texto (`.env`). Llena:
   - **La cuenta de administrador** (las líneas `ADMIN_...`): tu boleta o número de empleado, tu nombre, tus apellidos, tu
     correo y una contraseña temporal.
   - **El correo** (la parte «Correo»), para que lleguen los avisos y se pueda recuperar la contraseña (ver
     «Correo» más abajo). Si lo dejas vacío, el sistema funciona, pero sin correo.
   - No toques `JWT_SECRET` ni `POSTGRES_PASSWORD`: dicen `GENERAR` y `iniciar.bat` las llena solo con claves largas y
     aleatorias la primera vez.

   Guarda y cierra.
6. Vuelve a darle doble clic a **iniciar.bat**. Carga las imágenes (unos minutos la primera vez) y prende el sistema. No cierres
   la ventana. Las siguientes veces tarda menos de un minuto.
7. Cuando diga «Listo», se abre el navegador en **http://localhost:8088** y la ventana te dice también la dirección para las
   otras PCs.

## Primer ingreso

1. Entra con tu correo y la contraseña temporal que escribiste en el paso 5.
2. El sistema te pide **cambiar la contraseña** antes de usar nada. Elige una nueva (mínimo 8 caracteres).
3. Listo: ya estás en la lista de experimentos.

## Entrar desde otras PCs de la red

1. Al terminar, `iniciar.bat` muestra algo como:
   ```
   En esta PC:        http://localhost:8088
   Desde otras PCs de la red entra a:  http://NOMBRE-DE-LA-PC:8088
   o con la IP actual:  http://192.168.1.50:8088
   ```
   Las otras PCs escriben esa dirección en su navegador. No instalan nada. (Si el nombre no funciona en alguna PC, usa la IP.)
2. **Firewall de Windows:** ejecuta **una sola vez** `portable\abrir_red.bat` como administrador (clic derecho, «Ejecutar como
   administrador»). Abre el puerto del sistema (`PUERTO_WEB`) **solo para redes de tipo Privado**. Si Windows tiene la red del
   laboratorio como «Pública», cámbiala a «Privada» (Configuración, Red e Internet, propiedades de la conexión). `iniciar.bat`
   no toca el firewall por su cuenta.
3. **Dirección fija:** la IP de la PC puede cambiar cuando se reinicia el router o la PC. Pídele a quien administra la red una
   **IP fija** (reservada) para esta PC, o usa **el nombre de la PC**, que no cambia. Si quieres que los correos de recuperar
   contraseña lleven otra dirección, escríbela completa en `DIRECCION_WEB` del `.env` (por ejemplo
   `http://192.168.1.50:8088`) y vuelve a ejecutar `iniciar.bat`. Si la dejas vacía, se usa `http://NOMBRE-DE-LA-PC:8088`.
4. La PC tiene que estar **encendida, sin suspenderse y con Docker Desktop corriendo** para que las demás entren (ver arriba).
5. Es una red de laboratorio: la página va **sin cifrado (http)**. No la expongas a internet.

## Correo

El sistema manda correos por dos motivos: los **avisos** de los análisis (terminado, error, pausa, cancelado, disco lleno) y la
**recuperación de contraseña**. Sin correo, los avisos salen solo en la campana de la página y nadie puede recuperar su
contraseña por correo (un administrador puede darle una temporal).

Llena la parte «Correo» del `.env` con **una** de estas dos opciones:

- **El servidor de correo de tu institución** (pide los datos a quien lo administra): `SMTP_HOST`, `SMTP_PORT` (587, 25 o 465
  según el servidor), `SMTP_USER` y `SMTP_PASSWORD` si pide usuario, `SMTP_AUTH=true` si pide usuario y `SMTP_STARTTLS=true`
  si usa STARTTLS.
- **Una cuenta de Gmail con contraseña de aplicación:** en tu cuenta de Google activa la verificación en dos pasos y crea una
  «contraseña de aplicación» (no sirve la contraseña normal). Después: `SMTP_HOST=smtp.gmail.com`, `SMTP_PORT=587`,
  `SMTP_USER=` y `SMTP_FROM=` con la misma cuenta, `SMTP_PASSWORD=` con la contraseña de aplicación, `SMTP_AUTH=true` y
  `SMTP_STARTTLS=true`.

Sin comillas y sin espacios al final. Guarda el `.env`, ejecuta **iniciar.bat** (para que el sistema tome los valores) y luego
**probar_correo.bat**: manda un correo de prueba a la cuenta del administrador y dice si salió o cuál fue el error del servidor
de correo. Si dice que no salió, revisa los valores del `.env` y vuelve a probar. Cuando `iniciar.bat` ve `SMTP_HOST` vacío te
avisa que no habrá correo.

## Subir un video

1. Crea un experimento («Nuevo experimento»).
2. Entra al experimento y usa el botón para cargar videos.
3. Escribe el grupo (por ejemplo «Control»), elige la sesión y el archivo.
   - **Día 1** es la habituación: el video se guarda pero **no se analiza**.
   - **Día 2** es la prueba: este video **sí se analiza**.
4. El video debe ser **horizontal** (los cilindros de lado a lado) y de tipo **.mp4 o .mov**. Si no, el sistema
   lo rechaza y te explica por qué.
5. Un video de 1 GB tarda alrededor de un minuto en subirse.

## Cuánto tarda un análisis

Mucho: **de 40 minutos a más de una hora** por video de unos 6 minutos, porque la computadora trabaja sin tarjeta
gráfica. Un video corto de 25 segundos tarda unos 5. El sistema analiza **un video a la vez**; los demás esperan su
turno. Puedes cerrar el navegador: el análisis sigue, y al terminar aparece un aviso en la campana de la página (y por correo,
si lo configuraste).

## Apagar y volver a prender

- Para apagar: doble clic a **detener.bat**. No borra nada.
- Para volver a prender: doble clic a **iniciar.bat**.
- Si apagas la computadora con un análisis a medias, se reinicia solo la próxima vez que prendas el sistema.
- Después de reiniciar la PC, el sistema vuelve solo si Docker Desktop arranca con Windows (ver «Qué necesitas»): los servicios
  se reinician solos. Si no, dale doble clic a `iniciar.bat`.

## Actualizar a una versión nueva

Te llegan dos archivos nuevos (`fst-portable-X.Y.Z.zip` y `fst-imagenes-X.Y.Z.tar`, de la versión nueva).

1. Haz **detener.bat** (opcional, pero ordenado).
2. **Copia el contenido del zip nuevo encima de la carpeta** en la que instalaste (reemplaza archivos). **No borres** el `.env`
   (está en `portable`) ni la carpeta `respaldos`: el zip no los trae, así que no se pisan.
3. Copia el `fst-imagenes-X.Y.Z.tar` nuevo junto a `docker-compose.yml`.
4. Ejecuta **iniciar.bat**. Detecta la versión nueva, **hace solo un respaldo de la base** antes de prender (si falla, no
   continúa y no toca nada), carga las imágenes nuevas y prende el sistema. Tus experimentos, cuentas, resultados y videos
   siguen ahí: viven en volúmenes de Docker, no en la carpeta, y las migraciones de la base son seguras de repetir.
5. Entra y comprueba que todo esté. Puedes borrar el `.tar` y el `.zip` viejos (y, si quieres liberar espacio,
   las imágenes viejas desde Docker Desktop: solo las de versiones que ya no usas).

### Volver a la versión anterior

Si algo salió mal con la versión nueva:

1. Haz **detener.bat**.
2. Pon de nuevo el paquete de la versión anterior (su zip encima, y su `.tar` junto al compose; las imágenes viejas siguen en
   Docker si no las borraste).
3. Ejecuta **iniciar.bat**: usa la versión de ese paquete. Los datos siguen en los volúmenes. Si la versión nueva alcanzó a
   cambiar algo en la base que la vieja no entiende (muy raro: las migraciones solo agregan), restaura el respaldo que hizo
   `iniciar.bat` al actualizar (ver «Respaldos» más abajo).

## Dónde quedan los datos

En Docker Desktop (en «volúmenes»), no en esta carpeta. **No se borran al apagar**, ni al apagar la computadora, ni al
actualizar. No uses «Delete» sobre los volúmenes de `fst-portable` en Docker Desktop salvo que quieras borrar todo.

## Respaldos de la base

Cada día, a las 3 de la mañana (o la próxima vez que prendas el sistema, si estaba apagado a esa hora), el sistema
guarda una copia de la **base** (experimentos, cuentas y resultados; **no** de los videos) en la carpeta **`respaldos`**,
junto a `portable` (`C:\fst-portable\respaldos`), con el nombre `fst-AAAAMMDD.dump`. Se conservan los 7 más nuevos. Esa
carpeta está fuera de los volúmenes de Docker, así que los respaldos siguen ahí aunque alguien borre los volúmenes. Copia
la carpeta a otro lugar de vez en cuando (otra PC, una USB). También se hace un respaldo al actualizar a una versión nueva.

**Restaurar un respaldo REEMPLAZA todos los datos actuales** (experimentos, cuentas y resultados) por los del respaldo;
los videos no se tocan. Para hacerlo, abre una ventana de comandos en la carpeta `portable`:

1. Apaga la página y el servidor: `docker compose -f ..\docker-compose.yml --env-file .env stop web api worker`
2. Restaura el respaldo que quieras (cambia el nombre del archivo; no cambies el resto):
   ```
   docker compose -f ..\docker-compose.yml --env-file .env run --rm worker sh -c "PGPASSWORD=$POSTGRES_PASSWORD pg_restore -h db -U $POSTGRES_USER -d $POSTGRES_DB --clean --if-exists --no-owner /data/respaldos/fst-20261023.dump"
   ```
   Al final puede decir `errors ignored on restore: 1` por una línea llamada `SET transaction_timeout`: es normal, los
   datos se restauraron completos.
3. Vuelve a prender el sistema con **iniciar.bat**.

## Si algo falla

- «Docker no está respondiendo»: abre Docker Desktop y espera a que diga que está corriendo.
- «No estaban las imágenes… no se encontró el archivo fst-imagenes-X.Y.Z.tar»: copia ese archivo junto a `docker-compose.yml`
  (o dentro de `portable`) y vuelve a ejecutar `iniciar.bat`.
- El puerto 8088 está ocupado: abre `.env`, cambia `PUERTO_WEB=8088` por otro número (por ejemplo 8090), vuelve a darle a
  `iniciar.bat` y, si usas otras PCs, ejecuta de nuevo `abrir_red.bat` como administrador. La dirección será `http://localhost:`
  con ese número.
- Otra PC no puede entrar: revisa que la PC esté encendida y sin suspender, que hayas ejecutado `abrir_red.bat` (como
  administrador) y que la red esté como «Privada»; prueba con la IP en lugar del nombre. Si la página se queda cargando y
  nunca contesta (no da un error), casi siempre es el firewall de Windows: la red está como «Pública» o falta `abrir_red.bat`.
  Con `http://localhost:PUERTO` desde la propia PC sí debe abrir.
- Un análisis terminó en «error»: en la pantalla del experimento puedes ver su reporte de diagnóstico.
- Para ver qué pasó por dentro, abre una ventana de comandos en la carpeta `portable` y escribe
  `docker compose -f ..\docker-compose.yml --env-file .env logs worker` (el que analiza),
  `docker compose -f ..\docker-compose.yml --env-file .env logs api` (el servidor) o
  `docker compose -f ..\docker-compose.yml --env-file .env logs web` (la página). Cada registro se rota solo (máximo 5 archivos
  de 10 MB por servicio): no llenan el disco.
- Para ver si todo está sano: `docker compose -f ..\docker-compose.yml --env-file .env ps` (la columna «STATUS» dice
  «healthy» en la base, el servidor y el worker).

## Para quien arma el paquete (con el código fuente)

- `portable\empaquetar.ps1` construye las imágenes con la versión del archivo `VERSION`, las guarda en
  `dist\fst-imagenes-X.Y.Z.tar` (opción `-Comprimir` para `.tar.gz`) y arma `dist\fst-portable-X.Y.Z.zip`. Sin opciones usa lo
  commiteado (`git archive HEAD`: el paquete oficial); con `-DesdeCarpeta`, lo que hay en la carpeta de trabajo (para probar).
  Necesita `modelos\fst_cnn_seg.pt` (no está en git) y Docker Desktop corriendo. Las imágenes base están fijadas a versiones
  exactas en los Dockerfile y el compose, y `worker\requirements.txt` fija todas las bibliotecas de Python con `==`.
- La versión sube con `python tools\version.py --siguiente feat --escribir` (o `fix`) antes del commit.
- Para probar un paquete sin tocar la instalación real: define `FST_PROYECTO=fst-prueba` (otro proyecto de Docker, con
  volúmenes propios), `PUERTO_WEB` distinto en el `.env` y, si quieres que `iniciar.bat` no abra el navegador ni espere una
  tecla, `FST_SIN_NAVEGADOR=1` y `FST_SIN_PAUSA=1`.

## Qué NO hace todavía

- Nada de lo anterior está fuera del sistema actual: la administración de cuentas, la recuperación de contraseña por correo,
  la revisión segundo a segundo, los reportes en PDF, CSV y XLSX, la pausa cuando no se ve a un espécimen y las gráficas por
  bloques de 5 s ya funcionan. La página va sin cifrado (http): es para la red del laboratorio, no para internet.
