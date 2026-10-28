@echo off
rem Manda un correo de prueba a la cuenta del administrador (ADMIN_CORREO del .env) usando el contenedor del worker, con la
rem misma configuracion y el mismo codigo que los avisos de los analisis, y dice si salio o el error del servidor de correo.
rem El sistema tiene que estar prendido (iniciar.bat). Si cambiaste la parte "Correo" del .env, vuelve a ejecutar
rem iniciar.bat antes de probar, para que el worker tome los valores nuevos.
rem Codigos de salida: 0 salio, 1 no se pudo enviar, 2 no hay correo configurado o falta el administrador,
rem 3 Docker apagado o el sistema no esta prendido.
rem (FST_PROYECTO y FST_SIN_PAUSA son solo para pruebas; ver iniciar.bat.)
setlocal
cd /d "%~dp0"
title Sistema FST - Probar el correo
if "%FST_PROYECTO%"=="" set "FST_PROYECTO=fst-portable"

echo ==========================================
echo   Sistema FST - prueba del correo
echo ==========================================
echo.

if not exist ".env" (
  echo [ERROR] No hay archivo .env. Ejecuta primero iniciar.bat.
  if not defined FST_SIN_PAUSA pause
  exit /b 2
)
for /f "usebackq eol=# tokens=1,* delims==" %%A in (".env") do set "%%A=%%B"
if "%ADMIN_CORREO%"=="" (
  echo [ERROR] Falta ADMIN_CORREO en el .env: es la cuenta a la que se manda la prueba.
  if not defined FST_SIN_PAUSA pause
  exit /b 2
)

docker info >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Docker no esta respondiendo. Abre Docker Desktop, prende el sistema con iniciar.bat y vuelve a intentar.
  if not defined FST_SIN_PAUSA pause
  exit /b 3
)

echo Mandando un correo de prueba a %ADMIN_CORREO% ...
echo.
docker compose -p %FST_PROYECTO% -f ..\docker-compose.yml --env-file .env exec -T -u app worker python probar_correo.py "%ADMIN_CORREO%"
set "RESULTADO=%errorlevel%"
echo.
if "%RESULTADO%"=="0" (
  echo Si el correo no aparece en unos minutos, revisa la carpeta de spam.
) else if "%RESULTADO%"=="1" (
  echo Revisa SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASSWORD y SMTP_STARTTLS en el .env. Con Gmail hace falta una
  echo "contrasena de aplicacion", no la contrasena normal. Despues de cambiar el .env, ejecuta iniciar.bat y vuelve a probar.
) else if "%RESULTADO%"=="2" (
  echo Llena la parte "Correo" del .env, ejecuta iniciar.bat y vuelve a probar.
) else (
  echo No se pudo ejecutar la prueba: el sistema tiene que estar prendido ^(iniciar.bat^).
  set "RESULTADO=3"
)
if not defined FST_SIN_PAUSA pause
exit /b %RESULTADO%
