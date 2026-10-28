@echo off
rem Prende el Sistema FST (version portable). Se ejecuta con doble clic.
rem Codigos de salida: 0 todo bien, 1 Docker apagado, 2 se creo el .env (falta llenarlo), 3 faltan datos del
rem administrador, 4 no se pudo generar una llave o clave, 5 falta el modelo, 6 fallo al prender el sistema,
rem 7 el sistema no respondio a tiempo, 8 faltan las imagenes de esta version (no hay archivo .tar), 9 fallo la carga de
rem las imagenes, 10 falta el archivo VERSION, 11 no se pudo hacer el respaldo previo a actualizar.
rem (Los mensajes no llevan acentos a proposito: asi se leen bien en cualquier consola de Windows.)
rem Variables solo para pruebas: FST_SIN_NAVEGADOR no abre el navegador al final, FST_SIN_PAUSA no espera una tecla,
rem FST_SIN_RESPALDO no hace el respaldo previo al actualizar (ver el paso 7) y
rem FST_PROYECTO cambia el nombre del proyecto de Docker (por omision fst-portable: NO se cambia en una instalacion real,
rem porque los datos estan en los volumenes de ese proyecto).
setlocal
cd /d "%~dp0"
title Sistema FST - Iniciar
if "%FST_PROYECTO%"=="" set "FST_PROYECTO=fst-portable"

echo ==========================================
echo   Sistema FST - version portable
echo ==========================================
echo.

rem --- 1) Docker Desktop debe estar corriendo --------------------------------------
docker info >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Docker no esta respondiendo.
  echo.
  echo   Abre Docker Desktop y espera a que diga que esta corriendo.
  echo   Despues vuelve a ejecutar este archivo.
  echo.
  if not defined FST_SIN_PAUSA pause
  exit /b 1
)
echo [1/9] Docker esta listo.

rem --- 2) El archivo .env (la configuracion) ----------------------------------------
if not exist ".env" (
  copy ".env.example" ".env" >nul
  echo.
  echo Es la primera vez: se creo el archivo .env y se va a abrir para que lo llenes.
  echo   - Llena la parte "Tu cuenta de administrador" ^(las lineas ADMIN_^).
  echo   - Llena la parte "Correo" si quieres que los avisos y la recuperacion de contrasena lleguen por correo.
  echo   - Guarda el archivo y cierralo.
  echo   - Despues vuelve a ejecutar iniciar.bat.
  echo.
  if not defined FST_SIN_PAUSA start notepad ".env"
  if not defined FST_SIN_PAUSA pause
  exit /b 2
)
echo [2/9] Se encontro el archivo .env.

rem --- 3) La cuenta del administrador debe estar llena ------------------------------
rem Se leen las lineas CLAVE=valor del .env (las que empiezan con # se saltan).
for /f "usebackq eol=# tokens=1,* delims==" %%A in (".env") do set "%%A=%%B"
if "%ADMIN_ID%"=="" goto faltan_admin
if "%ADMIN_NOMBRE%"=="" goto faltan_admin
if "%ADMIN_APELLIDOS%"=="" goto faltan_admin
if "%ADMIN_CORREO%"=="" goto faltan_admin
if "%ADMIN_PASSWORD%"=="" goto faltan_admin
goto admin_listo

:faltan_admin
echo.
echo [ERROR] Falta llenar tu cuenta de administrador en el archivo .env
echo   Llena ADMIN_ID, ADMIN_NOMBRE, ADMIN_APELLIDOS, ADMIN_CORREO y ADMIN_PASSWORD,
echo   guarda el archivo y vuelve a ejecutar iniciar.bat.
echo.
if not defined FST_SIN_PAUSA start notepad ".env"
if not defined FST_SIN_PAUSA pause
exit /b 3

:admin_listo
echo [3/9] La cuenta del administrador esta llena.

rem --- 4) La llave de seguridad y la clave de la base se generan solas ----------------
if "%JWT_SECRET%"=="" goto generar_llave
if /i "%JWT_SECRET%"=="GENERAR" goto generar_llave
echo [4/9] La llave de seguridad ya existe.
goto llave_lista

:generar_llave
echo [4/9] Generando la llave de seguridad ^(solo la primera vez^)...
call :generar_clave JWT_SECRET
if errorlevel 1 (
  echo.
  echo [ERROR] No se pudo generar la llave de seguridad.
  if not defined FST_SIN_PAUSA pause
  exit /b 4
)

:llave_lista
rem La clave de la base solo se genera en una instalacion NUEVA (todavia no existe el volumen de la base): la base guarda
rem la clave con la que se creo, y cambiarla en el .env de una instalacion que ya tiene datos la dejaria afuera.
if not "%POSTGRES_PASSWORD%"=="" if /i not "%POSTGRES_PASSWORD%"=="GENERAR" goto clave_db_lista
docker volume inspect %FST_PROYECTO%_pgdata >nul 2>&1
if errorlevel 1 goto clave_db_nueva
echo       [AVISO] Ya existe la base de datos de este sistema y el .env no trae su clave: se usa "fst",
echo       que es la clave con la que se creaban las instalaciones anteriores. Si la base se creo con otra, escribela
echo       en POSTGRES_PASSWORD del .env.
call :poner_clave POSTGRES_PASSWORD fst
goto clave_db_lista

:clave_db_nueva
echo       Generando la clave de la base de datos ^(solo la primera vez^)...
call :generar_clave POSTGRES_PASSWORD
if errorlevel 1 (
  echo.
  echo [ERROR] No se pudo generar la clave de la base de datos.
  if not defined FST_SIN_PAUSA pause
  exit /b 4
)

:clave_db_lista

rem --- 5) El modelo que analiza los videos ------------------------------------------
if not exist "..\modelos\fst_cnn_seg.pt" (
  echo.
  echo [ERROR] No se encontro el modelo: ..\modelos\fst_cnn_seg.pt
  echo   Copia la carpeta "modelos" completa junto a la carpeta "portable" y vuelve a ejecutar.
  echo.
  if not defined FST_SIN_PAUSA pause
  exit /b 5
)
echo [5/9] Se encontro el modelo de analisis.

rem --- 6) La version y las imagenes ---------------------------------------------------
rem La version es la del archivo VERSION del paquete. Se copia a FST_VERSION en el .env en cada arranque: asi las
rem imagenes que busca el compose (fst-api:X.Y.Z ...) siempre son las de ESTE paquete y los demas comandos del LEEME
rem (logs, detener) tambien las encuentran.
if not exist "..\VERSION" (
  echo.
  echo [ERROR] Falta el archivo ..\VERSION. El paquete esta incompleto: copialo de nuevo completo.
  if not defined FST_SIN_PAUSA pause
  exit /b 10
)
rem La que traia el .env es la version con la que se arranco la ultima vez (vacia en una instalacion nueva).
set "FST_VERSION_ANTERIOR=%FST_VERSION%"
set "FST_VERSION="
set /p FST_VERSION=<"..\VERSION"
call :poner_clave FST_VERSION %FST_VERSION%
echo [6/9] Version del sistema: %FST_VERSION%

rem Las imagenes que necesita el compose, sacadas del propio compose (una sola fuente de verdad).
set "FST_FALTAN="
set "FST_IMAGENES=0"
for /f "usebackq delims=" %%I in (`docker compose -p %FST_PROYECTO% -f ..\docker-compose.yml --env-file .env config --images`) do (
  set /a FST_IMAGENES+=1
  docker image inspect %%I >nul 2>&1
  if errorlevel 1 set "FST_FALTAN=1"
)
if "%FST_IMAGENES%"=="0" (
  echo.
  echo [ERROR] No se pudo leer el compose ^(..\docker-compose.yml^). Revisa que el paquete este completo.
  if not defined FST_SIN_PAUSA pause
  exit /b 6
)
if not defined FST_FALTAN goto imagenes_listas

echo       Faltan las imagenes de la version %FST_VERSION% en Docker; se buscan en el archivo de imagenes...
set "FST_TAR="
for %%T in ("..\fst-imagenes-%FST_VERSION%.tar" "..\fst-imagenes-%FST_VERSION%.tar.gz" ".\fst-imagenes-%FST_VERSION%.tar" ".\fst-imagenes-%FST_VERSION%.tar.gz") do (
  if not defined FST_TAR if exist %%T set "FST_TAR=%%~T"
)
if not defined FST_TAR (
  echo.
  echo [ERROR] No estan las imagenes de la version %FST_VERSION% y no se encontro el archivo fst-imagenes-%FST_VERSION%.tar
  echo   Copia ese archivo ^(sale de empaquetar.ps1, en la carpeta dist^) junto a docker-compose.yml, o dentro de
  echo   esta carpeta, y vuelve a ejecutar iniciar.bat. No hace falta internet.
  echo.
  if not defined FST_SIN_PAUSA pause
  exit /b 8
)
echo       Cargando %FST_TAR% ^(tarda unos minutos, no cierres la ventana^)...
docker load -i "%FST_TAR%"
if errorlevel 1 (
  echo.
  echo [ERROR] No se pudieron cargar las imagenes. Revisa el mensaje de arriba ^(a veces es falta de espacio en disco^).
  if not defined FST_SIN_PAUSA pause
  exit /b 9
)

:imagenes_listas
echo [7/9] Las imagenes de la version %FST_VERSION% estan en Docker.

rem Si esta es una ACTUALIZACION (ya hay base de datos y el .env traia otra version, o ninguna: la instalacion anterior a
rem esta forma de empaquetar no la guardaba), antes de prender la version nueva se hace un respaldo de la base con la imagen
rem nueva (tareas_programadas.py --respaldo: pg_dump a la carpeta respaldos). Los datos no se tocan al actualizar, pero un
rem respaldo reciente es lo que permite volver atras si algo sale mal. Si el respaldo falla, NO se sigue.
if "%FST_VERSION_ANTERIOR%"=="%FST_VERSION%" goto sin_respaldo_previo
if defined FST_SIN_RESPALDO goto sin_respaldo_previo
docker volume inspect %FST_PROYECTO%_pgdata >nul 2>&1
if errorlevel 1 goto sin_respaldo_previo
if defined FST_VERSION_ANTERIOR (
  echo       Actualizacion de la version %FST_VERSION_ANTERIOR% a la %FST_VERSION%: haciendo un respaldo de la base antes de prender...
) else (
  echo       Ya hay una base de datos de una instalacion anterior: haciendo un respaldo antes de prender la version %FST_VERSION%...
)
docker compose -p %FST_PROYECTO% -f ..\docker-compose.yml --env-file .env run --rm -T worker python tareas_programadas.py --respaldo
if errorlevel 1 (
  echo.
  echo [ERROR] No se pudo hacer el respaldo previo a la actualizacion, asi que NO se prendio la version nueva.
  echo   Tus datos siguen intactos. Revisa el mensaje de arriba y vuelve a ejecutar iniciar.bat. Si la base esta vacia
  echo   o ya no importa el respaldo, define la variable FST_SIN_RESPALDO=1 y vuelve a ejecutar.
  echo.
  if not defined FST_SIN_PAUSA pause
  exit /b 11
)
echo       Respaldo listo en la carpeta respaldos.
:sin_respaldo_previo

rem --- 8) Prender el sistema -----------------------------------------------------------
if "%PUERTO_WEB%"=="" set "PUERTO_WEB=8088"
rem La direccion que va en los correos (recuperar contrasena, primer acceso). Si DIRECCION_WEB esta vacia en el .env, se usa
rem el nombre de esta PC, que es lo que otras PCs de la red pueden escribir.
if "%DIRECCION_WEB%"=="" set "DIRECCION_WEB=http://%COMPUTERNAME%:%PUERTO_WEB%"
if "%SMTP_HOST%"=="" (
  echo.
  echo       [AVISO] SMTP_HOST esta vacio en el .env: NO hay correo configurado. Los avisos saldran solo en la campana
  echo       de la pagina y los correos de recuperar contrasena no van a llegar. Llena la parte "Correo" del .env y
  echo       vuelve a ejecutar iniciar.bat; luego prueba con probar_correo.bat.
)
echo.
echo [8/9] Prendiendo el sistema ^(sin construir ni descargar nada^)...
echo       No cierres esta ventana.
echo.
rem El compose es el de la raiz (..\docker-compose.yml). Con --env-file se usa portable\.env tambien para las
rem variables del compose, y no el .env de la raiz. --no-build: en produccion nunca se construye nada.
docker compose -p %FST_PROYECTO% -f ..\docker-compose.yml --env-file .env up -d --no-build
if errorlevel 1 (
  echo.
  echo [ERROR] No se pudo prender el sistema. Revisa el mensaje de arriba.
  echo   Para ver que paso, abre una ventana de comandos en esta carpeta y escribe:
  echo       docker compose -p %FST_PROYECTO% -f ..\docker-compose.yml --env-file .env logs api
  echo.
  if not defined FST_SIN_PAUSA pause
  exit /b 6
)

rem --- 9) Esperar a que responda (maximo 5 minutos) y abrir el navegador -------------
echo.
echo [9/9] Esperando a que el sistema responda...
set /a INTENTOS=0
:espera
rem Se usa curl.exe (viene con Windows 10 y 11) y no Invoke-WebRequest de PowerShell: con "localhost" este ultimo
rem intenta primero IPv6, se queda esperando y nunca responde aunque el sistema este funcionando.
curl.exe -s -f -m 5 -o NUL "http://localhost:%PUERTO_WEB%/salud"
if errorlevel 1 goto reintentar
curl.exe -s -f -m 5 -o NUL "http://localhost:%PUERTO_WEB%/api/health"
if not errorlevel 1 goto listo
:reintentar
set /a INTENTOS+=1
if %INTENTOS% GEQ 60 goto no_responde
ping -n 6 127.0.0.1 >nul
goto espera

:no_responde
echo.
echo [ERROR] El sistema no respondio en 5 minutos.
echo   Para ver que paso, abre una ventana de comandos en esta carpeta y escribe:
echo       docker compose -p %FST_PROYECTO% -f ..\docker-compose.yml --env-file .env logs api
echo       docker compose -p %FST_PROYECTO% -f ..\docker-compose.yml --env-file .env logs web
echo.
if not defined FST_SIN_PAUSA pause
exit /b 7

:listo
rem La IP de esta PC en la red (la de la conexion que tiene salida), como alternativa al nombre.
set "FST_IP="
powershell -NoProfile -Command "$c = Get-NetIPConfiguration | Where-Object { $_.IPv4DefaultGateway -ne $null -and $_.NetAdapter.Status -eq 'Up' } | Select-Object -First 1; if ($c) { $c.IPv4Address.IPAddress | Out-File -Encoding ascii '%TEMP%\fst_ip.txt' }" >nul 2>&1
if exist "%TEMP%\fst_ip.txt" set /p FST_IP=<"%TEMP%\fst_ip.txt"
del "%TEMP%\fst_ip.txt" >nul 2>&1
echo.
echo ==========================================
echo   Listo. El sistema esta funcionando.
echo.
echo   En esta PC:        http://localhost:%PUERTO_WEB%
echo   Desde otras PCs de la red entra a:
echo                      %DIRECCION_WEB%
if defined FST_IP echo   o con la IP actual:  http://%FST_IP%:%PUERTO_WEB%
echo ==========================================
echo   Si otra PC no puede entrar, ejecuta abrir_red.bat UNA vez como administrador
echo   ^(abre el puerto en el firewall de Windows^).
echo   Conviene que esta PC tenga IP fija, o usar su nombre: la IP puede cambiar.
echo.
echo   Entra con el correo y la contrasena temporal de tu cuenta de administrador;
echo   el sistema te pedira cambiar la contrasena.
echo.
if not defined FST_SIN_NAVEGADOR start "" "http://localhost:%PUERTO_WEB%"
if not defined FST_SIN_PAUSA pause
exit /b 0

rem ---------------------------------------------------------------------------------
rem Subrutinas. Escriben en el .env; el resto de sus lineas queda igual.

rem :generar_clave NOMBRE  -  pone en NOMBRE=... una clave aleatoria de 64 caracteres hexadecimales (32 bytes de
rem RandomNumberGenerator); si el .env no tiene esa linea, la agrega al final.
:generar_clave
set "FST_K=%~1"
powershell -NoProfile -Command "$k = $env:FST_K; $b = New-Object byte[] 32; [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); $s = -join ($b | ForEach-Object { $_.ToString('x2') }); $ruta = Join-Path (Get-Location) '.env'; $lineas = [System.IO.File]::ReadAllLines($ruta); $hecho = $false; $nuevas = foreach ($l in $lineas) { if ($l -match ('^' + [regex]::Escape($k) + '=')) { $hecho = $true; $k + '=' + $s } else { $l } }; if (-not $hecho) { $nuevas = @($nuevas) + ($k + '=' + $s) }; [System.IO.File]::WriteAllLines($ruta, [string[]]$nuevas, (New-Object System.Text.UTF8Encoding($false)))"
exit /b %errorlevel%

rem :poner_clave NOMBRE VALOR  -  pone NOMBRE=VALOR en el .env (reemplaza la linea o la agrega al final).
:poner_clave
set "FST_K=%~1"
set "FST_V=%~2"
powershell -NoProfile -Command "$k = $env:FST_K; $v = $env:FST_V; $ruta = Join-Path (Get-Location) '.env'; $lineas = [System.IO.File]::ReadAllLines($ruta); $hecho = $false; $nuevas = foreach ($l in $lineas) { if ($l -match ('^' + [regex]::Escape($k) + '=')) { $hecho = $true; $k + '=' + $v } else { $l } }; if (-not $hecho) { $nuevas = @($nuevas) + ($k + '=' + $v) }; [System.IO.File]::WriteAllLines($ruta, [string[]]$nuevas, (New-Object System.Text.UTF8Encoding($false)))"
exit /b %errorlevel%
