@echo off
rem Abre en el firewall de Windows el puerto del Sistema FST (PUERTO_WEB del .env) para que OTRAS PCs de la red del
rem laboratorio puedan abrir la pagina. Se ejecuta UNA sola vez, como administrador (clic derecho, "Ejecutar como
rem administrador"). iniciar.bat NO lo ejecuta solo.
rem La regla es solo para redes de tipo PRIVADO (la del laboratorio), no para las publicas.
rem Codigos de salida: 0 la regla quedo creada (o ya existia), 1 no es administrador, 2 no se pudo crear la regla.
rem (FST_SIN_PAUSA es solo para pruebas; ver iniciar.bat.)
setlocal
cd /d "%~dp0"
title Sistema FST - Abrir el puerto en el firewall

echo ==========================================
echo   Sistema FST - abrir el puerto en la red
echo ==========================================
echo.

rem --- Hace falta ser administrador para cambiar el firewall ------------------------
net session >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Este archivo necesita permisos de administrador.
  echo.
  echo   Cierra esta ventana, haz clic DERECHO sobre abrir_red.bat y elige
  echo   "Ejecutar como administrador".
  echo.
  if not defined FST_SIN_PAUSA pause
  exit /b 1
)

rem --- El puerto sale del .env (por omision 8088) ------------------------------------
set "PUERTO_WEB="
if exist ".env" for /f "usebackq eol=# tokens=1,* delims==" %%A in (".env") do if /i "%%A"=="PUERTO_WEB" set "PUERTO_WEB=%%B"
if "%PUERTO_WEB%"=="" set "PUERTO_WEB=8088"
set "REGLA=Sistema FST (puerto %PUERTO_WEB%)"

rem Si la regla ya existe se borra y se vuelve a crear: asi, si cambiaste el puerto o el perfil, queda bien.
netsh advfirewall firewall show rule name="%REGLA%" >nul 2>&1
if not errorlevel 1 netsh advfirewall firewall delete rule name="%REGLA%" >nul 2>&1

netsh advfirewall firewall add rule name="%REGLA%" dir=in action=allow protocol=TCP localport=%PUERTO_WEB% profile=private
if errorlevel 1 (
  echo.
  echo [ERROR] No se pudo crear la regla del firewall. Revisa el mensaje de arriba.
  if not defined FST_SIN_PAUSA pause
  exit /b 2
)

echo.
echo ==========================================
echo   Listo. Se abrio el puerto %PUERTO_WEB% para la red PRIVADA.
echo ==========================================
echo   Las otras PCs entran con la direccion que muestra iniciar.bat ^(el nombre o la IP de esta PC^).
echo   Si Windows tiene esta red como "Publica", cambiala a "Privada" en Configuracion, Red e Internet:
echo   la regla no vale en redes publicas.
echo   Para quitar la regla: netsh advfirewall firewall delete rule name="%REGLA%"
echo.
if not defined FST_SIN_PAUSA pause
exit /b 0
