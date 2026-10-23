@echo off
rem Apaga el Sistema FST SIN borrar nada: los experimentos, los videos y las cuentas quedan guardados.
rem Para volver a prenderlo, ejecuta iniciar.bat.
cd /d "%~dp0"
title Sistema FST - Detener

echo ==========================================
echo   Sistema FST - apagando
echo ==========================================
echo.

docker info >nul 2>&1
if errorlevel 1 (
  echo Docker Desktop no esta corriendo, asi que el sistema ya esta apagado.
  pause
  exit /b 0
)

docker compose -f ..\docker-compose.yml --env-file .env stop
if errorlevel 1 (
  echo.
  echo [ERROR] No se pudo apagar el sistema. Revisa el mensaje de arriba.
  pause
  exit /b 1
)

echo.
echo Listo. El sistema esta apagado y tus datos siguen guardados.
echo Para volver a prenderlo, ejecuta iniciar.bat.
pause
exit /b 0
