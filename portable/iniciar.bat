@echo off
rem Prende el Sistema FST (version portable). Se ejecuta con doble clic.
rem Codigos de salida: 0 todo bien, 1 Docker apagado, 2 se creo el .env (falta llenarlo), 3 faltan datos del
rem administrador, 4 no se pudo generar la llave, 5 falta el modelo, 6 fallo la construccion,
rem 7 el sistema no respondio a tiempo.
rem (Los mensajes no llevan acentos a proposito: asi se leen bien en cualquier consola de Windows.)
rem Para pruebas: con la variable FST_SIN_NAVEGADOR definida no abre el navegador al final.
setlocal
cd /d "%~dp0"
title Sistema FST - Iniciar

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
  pause
  exit /b 1
)
echo [1/7] Docker esta listo.

rem --- 2) El archivo .env (la configuracion) ----------------------------------------
if not exist ".env" (
  copy ".env.example" ".env" >nul
  echo.
  echo Es la primera vez: se creo el archivo .env y se va a abrir para que lo llenes.
  echo   - Llena SOLO la parte "Tu cuenta de administrador" ^(las lineas ADMIN_^).
  echo   - Guarda el archivo y cierralo.
  echo   - Despues vuelve a ejecutar iniciar.bat.
  echo.
  start notepad ".env"
  pause
  exit /b 2
)
echo [2/7] Se encontro el archivo .env.

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
start notepad ".env"
pause
exit /b 3

:admin_listo
echo [3/7] La cuenta del administrador esta llena.

rem --- 4) La llave de seguridad se genera sola --------------------------------------
if "%JWT_SECRET%"=="" goto generar_llave
if /i "%JWT_SECRET%"=="GENERAR" goto generar_llave
echo [4/7] La llave de seguridad ya existe.
goto llave_lista

:generar_llave
echo [4/7] Generando la llave de seguridad ^(solo la primera vez^)...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$b = New-Object byte[] 32; [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); $s = -join ($b | ForEach-Object { $_.ToString('x2') }); $ruta = Join-Path (Get-Location) '.env'; $lineas = [System.IO.File]::ReadAllLines($ruta); $hecho = $false; $nuevas = foreach ($l in $lineas) { if ($l -match '^JWT_SECRET=') { $hecho = $true; 'JWT_SECRET=' + $s } else { $l } }; if (-not $hecho) { $nuevas = @($nuevas) + ('JWT_SECRET=' + $s) }; [System.IO.File]::WriteAllLines($ruta, [string[]]$nuevas, (New-Object System.Text.UTF8Encoding($false)))"
if errorlevel 1 (
  echo.
  echo [ERROR] No se pudo generar la llave de seguridad.
  pause
  exit /b 4
)

:llave_lista

rem --- 5) El modelo que analiza los videos ------------------------------------------
if not exist "..\modelos\fst_cnn_seg.pt" (
  echo.
  echo [ERROR] No se encontro el modelo: ..\modelos\fst_cnn_seg.pt
  echo   Copia la carpeta "modelos" completa junto a la carpeta "portable" y vuelve a ejecutar.
  echo.
  pause
  exit /b 5
)
echo [5/7] Se encontro el modelo de analisis.

rem --- 6) Construir y prender el sistema --------------------------------------------
echo.
echo [6/7] Preparando el sistema. La PRIMERA vez tarda de 20 a 40 minutos y necesita
echo       internet ^(descarga e instala todo^). Las siguientes veces tarda menos de un minuto.
echo       No cierres esta ventana.
echo.
rem El compose es el de la raiz (..\docker-compose.yml). Con --env-file se usa portable\.env tambien para las
rem variables del compose, y no el .env de la raiz.
docker compose -f ..\docker-compose.yml --env-file .env up -d --build
if errorlevel 1 (
  echo.
  echo [ERROR] No se pudo preparar el sistema. Revisa el mensaje de arriba.
  echo   Si dice algo de internet o de espacio en disco, resuelvelo y vuelve a ejecutar.
  echo.
  pause
  exit /b 6
)

rem --- 7) Esperar a que responda (maximo 5 minutos) y abrir el navegador -------------
if "%PUERTO_WEB%"=="" set "PUERTO_WEB=8088"
echo.
echo [7/7] Esperando a que el sistema responda...
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
echo       docker compose -f ..\docker-compose.yml --env-file .env logs api
echo       docker compose -f ..\docker-compose.yml --env-file .env logs web
echo.
pause
exit /b 7

:listo
echo.
echo ==========================================
echo   Listo. El sistema esta funcionando en:
echo       http://localhost:%PUERTO_WEB%
echo ==========================================
echo   Entra con el correo y la contrasena temporal de tu cuenta de administrador;
echo   el sistema te pedira cambiar la contrasena.
echo.
if not defined FST_SIN_NAVEGADOR start "" "http://localhost:%PUERTO_WEB%"
pause
exit /b 0
