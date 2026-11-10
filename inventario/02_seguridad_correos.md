# 02 · Seguridad, roles, correos y notificaciones

Fuente: el código de **HEAD (ba903f4, v1.9.2)**, leído con `git show HEAD:ruta`. Las rutas son de HEAD. Los valores secretos no aparecen: solo los nombres de las variables. «Desde» indica si el comportamiento ya existía en **15489f4** (producción v1.0.0) o es `nuevo`, comprobado con `git show 15489f4:ruta` y con `git diff 15489f4 HEAD` sobre los archivos citados.

## 1. Autenticación

| Tema | Lo que hace el código | Dónde (HEAD) | Desde |
|---|---|---|---|
| Tipo de token | JWT firmado con HS256 con una clave simétrica. Sin estado en el servidor (`SessionCreationPolicy.STATELESS`). | `config/SeguridadConfig.java:60,66-74,105` | base |
| Clave de firma | Variable **`JWT_SECRET`** (`app.jwt.secreto`), de al menos 32 caracteres. Si es más corta o falta, el backend **no arranca**. | `config/SeguridadConfig.java:51-54`; `application.properties` | base |
| Contenido del token de sesión | `sub` = identificador institucional; `tipo` = `"sesion"`; `iat` y `exp`. **No** lleva el rol: el rol se lee de la base en cada petición. | `service/AuthServiceImpl.java:115-120` | base |
| Validación del token | Firma, vigencia y `tipo = "sesion"`. Un token de enlace o de video no sirve como sesión. | `config/SeguridadConfig.java:63-74` | base |
| Vigencia de la sesión | **`JWT_EXPIRES_HOURS`**, por omisión **168 h** (7 días). | `application.properties` (`app.jwt.horas`); `service/AuthServiceImpl.java:119` | base |
| Dónde viaja el token | Solo en la cabecera `Authorization: Bearer`. Nunca en la URL. | `config/SeguridadConfig.java:117-118` | base |
| Cuenta inactiva o borrada | La conversión del token a usuario lanza error: la petición responde 401 aunque el token no haya vencido. | `config/ConvertidorJwt.java:37-40` | base |
| Hash de contraseña | **BCrypt** (`BCryptPasswordEncoder`). Se compara con `passwordEncoder.matches`. | `config/SeguridadConfig.java:44-46`; `service/AuthServiceImpl.java:108` | base |
| Mensaje de login fallido | Siempre «Correo o contraseña incorrectos» (401), para no revelar si el correo existe (R-01). Vale también para cuenta inactiva. | `service/AuthServiceImpl.java:46-47,104-110` | base |
| Reglas de contraseña nueva | Mínimo **8** caracteres; máximo **72 bytes** (límite de bcrypt). En cambio normal, la nueva debe ser distinta de la actual (400). En restablecer, 422. | `service/AuthServiceImpl.java:49-50,278-285,142-146,239-242` | base |
| Logout | `POST /auth/logout` responde 200 y **no hace nada en el servidor**: no hay lista de tokens revocados. El cliente borra su token. | `controller/AuthController.java:61-64` | base |
| Expiración del token en la práctica | El token dura lo que `JWT_EXPIRES_HOURS`; no hay renovación. Si la cuenta se desactiva, deja de servir en la siguiente petición (lectura en la base). | `service/AuthServiceImpl.java:119`; `config/ConvertidorJwt.java` | base |

### 1.1 Contraseña temporal y primer acceso

| Tema | Lo que hace el código | Dónde (HEAD) | Desde |
|---|---|---|---|
| Quién la genera | Un **Administrador** al crear una cuenta (`POST /admin/users`) o al pedir una nueva (`POST /admin/users/{id}/reset-temporal`). | `service/AdminServiceImpl.java:115-150,225-250` | base |
| Cómo se genera | 12 caracteres aleatorios de un alfabeto sin 0/O/1/l/I (unos 69 bits). Se guarda su hash BCrypt. | `service/AdminServiceImpl.java:39-41,299-306` | base |
| Marca | `cambioRequerido = true` en la cuenta. | `service/AdminServiceImpl.java:130` | base |
| Qué puede hacer la cuenta mientras tanto | Solo `PATCH /auth/password`, `POST /auth/logout` y `GET /health`. Lo demás responde **401** «Debes cambiar tu contraseña antes de continuar» (el 401 es a propósito, para que el navegador no entre en un bucle). | `config/FiltroCambioPendiente.java:12-17,29-36,45-51` | base |
| Cómo sale del estado | Al cambiar la contraseña (`PATCH /auth/password`) `cambioRequerido` pasa a `false`, y el mismo token deja de estar bloqueado. | `service/AuthServiceImpl.java:148-151` | base |
| Dónde se entrega | Por correo («Tu cuenta del sistema FST» o «Nueva contraseña temporal del sistema FST»). **Además**, la respuesta del alta y del restablecer de administrador **incluye la contraseña temporal** en el campo `passwordTemporal`, para que el Administrador la pueda dar en mano. | `service/AdminServiceImpl.java:148-150,250,309-325` | base |
| Administrador inicial | Al arrancar, `AdminInicial` crea la primera cuenta de Administrador con las variables **`ADMIN_ID`, `ADMIN_NOMBRE`, `ADMIN_APELLIDOS`, `ADMIN_CORREO`, `ADMIN_PASSWORD`**. Si `ADMIN_ID` está vacío no hace nada; si ya existe la cuenta, tampoco. La contraseña queda con `cambioRequerido = true`. | `config/AdminInicial.java:24-90` | base |

### 1.2 Recuperar contraseña (enlace por correo)

| Tema | Lo que hace el código | Dónde (HEAD) | Desde |
|---|---|---|---|
| Pedir el enlace | `POST /auth/recover` responde **siempre igual** (200), exista o no la cuenta (R-01). Solo actúa si la cuenta existe y está activa. | `controller/AuthController.java:36-41`; `service/AuthServiceImpl.java:194-198` | base |
| Qué contiene el enlace | Un JWT firmado con `tipo = "reset"`, con el identificador, una **huella** del hash de la contraseña de ese momento, y una vigencia de **`RESET_LINK_MINUTES`** (por omisión **60 min**). El enlace es `WEB_URL/restablecer?token=…`. | `service/AuthServiceImpl.java:198,213-226,265-282` | base |
| Un solo uso | No hay tabla de enlaces usados: al cambiar la contraseña, la huella deja de coincidir y el enlace ya no sirve. | `service/AuthServiceImpl.java:221-236`, comentario en 213-214 | base |
| Usar el enlace | `POST /auth/reset` con el token y la contraseña nueva. Enlace vencido, usado o inválido: 400 «El enlace venció o ya se usó.». Contraseña inválida: 422. | `controller/AuthController.java:43-50`; `service/AuthServiceImpl.java:216-246` | base |
| Qué quita el enlace | Al cambiarse el hash por cualquier vía (el enlace, el cambio normal, una temporal nueva), todos los enlaces pendientes dejan de servir. | `service/AdminServiceImpl.java:240` (comentario); `service/AuthServiceImpl.java:241-245` | base |

### 1.3 Permiso de video (token de video)

| Tema | Lo que hace el código | Dónde (HEAD) | Desde |
|---|---|---|---|
| Quién lo pide | `POST /experiments/{id}/analyses/{idAnalisis}/video-token`, con sesión, para quien subió el video o un Administrador. | `controller/RevisionController.java:38`; `service/RevisionServiceImpl.java` (regla de dueño) | base |
| Qué es | Un JWT con `tipo = "video"` y una vigencia de **`VIDEO_TOKEN_MINUTES`** (por omisión **120 min**). | `service/RevisionServiceImpl.java:72-73,211-214` | base |
| Por qué no usa sesión | La etiqueta `<video>` no puede mandar cabecera. Por eso `GET /experiments/*/analyses/*/video` es **público** y exige el token en `?vt=`. Es la única ruta pública de `/experiments`. | `config/SeguridadConfig.java:108-111`; `controller/RevisionController.java:47` | base |
| Qué no sirve | Un token de sesión, de enlace de restablecer o de video no sirve como sesión (el validador exige `tipo = "sesion"`). | `config/SeguridadConfig.java:63-74` | base |
| Dónde queda registrado | El nginx de producción pone `error_log … crit` en esa ruta para que el `?vt=` no quede en el registro de errores. | `frontend/nginx.conf:34-41` | base |

## 2. Roles y permisos

Hay dos roles: **Investigador** (por omisión) y **Administrador** (la cuenta tiene fila en la tabla `administrador`). El rol se lee de la base en cada petición (`config/ConvertidorJwt.java:42`), así que un cambio de rol surte efecto de inmediato.

| Qué puede hacer | Quién | Dónde (HEAD) | Desde |
|---|---|---|---|
| Entrar, cambiar su contraseña (si está temporal, solo eso), editar su perfil (nombre, apellidos, correo) | Cualquier cuenta activa | `controller/UsuarioController.java:29`; `service/AuthServiceImpl.java:158-188` | base |
| Ver la lista de experimentos, sus detalles, resultados y reportes | **Cualquier cuenta** (R-07; todos ven todos los experimentos) | `controller/ExperimentoController.java:68`; `controller/ResultadosController.java:38-100` | base |
| Crear experimento | Cualquier cuenta; el creador queda registrado | `controller/ExperimentoController.java:149`; `service/ExperimentoServiceImpl.java` | base |
| Editar un experimento | **Solo quien lo creó o un Administrador** (403 si no) | `controller/ExperimentoController.java:157`; mensaje en `service/ExperimentoServiceImpl.java` | base |
| Eliminar un experimento o una tanda | **Solo quien lo creó o un Administrador** (403 si no). El experimento no se puede borrar si hay análisis en proceso o en pausa (409). | `controller/ExperimentoController.java:166,175` | base |
| Subir un video a una tanda | **Solo quien creó el experimento** («Solo quien creó el experimento sube sus videos.») | `controller/VideoController.java:30`; `service/VideoServiceImpl.java` | base |
| Ver el video de una revisión (token), corregir segundos, reiniciar un análisis | **Quien subió el video o un Administrador** (403 si no) | `controller/RevisionController.java:38,57,64` | base |
| Ver las notificaciones propias y marcarlas como leídas | Cualquier cuenta, solo las suyas | `controller/NotificacionController.java` | base |
| Administrar cuentas: listar, crear, modificar, contraseña temporal, ver el sistema | **Solo Administrador**: `/admin/**` (403 a los demás, por la regla de la ruta) | `config/SeguridadConfig.java:115`; `controller/AdminController.java` | base |
| Dar o quitar el rol de Administrador, desactivar una cuenta | Administrador, con dos candados: **siempre debe quedar al menos un Administrador activo** (409, R-16), y nadie cambia su propio acceso desde `/admin` (usa «Mi perfil») | `service/ReglaAdministradores.java:11-51`; `service/AdminServiceImpl.java:156-220` | base |
| Pedir una contraseña temporal para sí mismo | No: un Administrador no la puede pedir para su propia cuenta (409 «usa Mi perfil») | `service/AdminServiceImpl.java:225-240` | base |
| Ver el modelo del análisis y la salida del modelo | Cualquier cuenta con sesión, **solo con `MODO_DESARROLLADOR=true`**; si no, 404 | `service/ResultadosServiceImpl.java` (`salidaDelModelo`); `controller/DevModeloController.java`; `service/ModeloActualDeDesarrollo.java` | `nuevo` (la salida ya existía en base; `/dev/modelo-actual` es nuevo) |

## 3. Correos que envía el sistema

El envío del backend es `utilerias/service/impl/EmailServiceImpl.java` con `JavaMailSender` y texto plano. Un fallo de envío se registra y **no** deshace la operación. El envío se hace **después del commit**: si la operación se deshace, no sale ningún correo.

### 3.1 Desde la API (backend)

| # | Cuándo | Destinatario | Asunto | Contenido (resumen) | Dónde (HEAD) | Desde |
|---|---|---|---|---|---|---|
| A1 | Un Administrador crea una cuenta (`POST /admin/users`) | La cuenta nueva | «Tu cuenta del sistema FST» | Se creó la cuenta; identificador; **contraseña temporal**; enlace a la web; la temporal deja de servir al cambiarla. | `service/AdminServiceImpl.java:309-325` (llamado en 148) | base |
| A2 | Un Administrador pide una temporal nueva (`POST /admin/users/{id}/reset-temporal`) | La cuenta | «Nueva contraseña temporal del sistema FST» | «Un Administrador restableció tu contraseña»; identificador; contraseña temporal. | `service/AdminServiceImpl.java:309-325` (llamado en 250) | base |
| A3 | Alguien pide restablecer su contraseña (`POST /auth/recover`), con cuenta activa | La cuenta | «Restablecer tu contraseña» | Solicitud recibida; enlace válido **60 min** (`RESET_LINK_MINUTES`), de un solo uso; «si no lo pediste, ignora este correo». | `service/AuthServiceImpl.java:197-209` | base |

No hay más correos desde la API. La API no envía avisos de experimentos ni de análisis: eso lo hace el worker.

### 3.2 Desde el worker

El worker crea una fila en la tabla `notificacion` (la campanita) y **después del commit** manda el correo al investigador (`worker/comun.py:144-172`). Los asuntos están en `worker/comun.py:39-46`.

| Tipo (`notificacion.tipo`) | Asunto del correo | Cuándo | Texto (resumen) | Dónde (worker) |
|---|---|---|---|---|
| `analisis completado` | «Análisis completado» | Termina el análisis de una tanda | «El análisis de {tanda} del experimento '{nombre}' terminó. Ya puedes ver los resultados.» | `worker/worker.py:564-566` |
| `analisis en pausa` | «Análisis en pausa: ajusta el recuadro» | El cilindro no se ve y el análisis se pausa | «…se pausó: el cilindro N no se ve desde el segundo S. Ajusta el recuadro para continuar.» | `worker/worker.py:437-440` |
| `analisis cancelado` | «Análisis cancelado» | Pasan **72 h** sin que se ajuste el recuadro (`PAUSE_TIMEOUT_HOURS`) | «…se canceló: pasaron 72 horas sin que se ajustara el recuadro.» | `worker/cola.py:79-82` |
| `error del pipeline` | «Error en el análisis» | Un paso del análisis falla | «…terminó con error en la etapa '…': <mensaje>» | `worker/cola.py:138-140` |
| `alerta de disco` | «Alerta de disco» | El disco de videos y reportes pasa del umbral de aviso | «El disco de videos y reportes está al N %. Al pasar del 90 % se borran los videos más antiguos.» (el aviso salta al pasar `DISK_ALERT_PERCENT`, por omisión 80 %; el % de limpieza es `DISK_CLEANUP_PERCENT`, por omisión 90 %; `worker/comun.py:27-28`) | `worker/tareas_programadas.py:228-230` |
| `video borrado` | «Videos borrados por falta de disco» | La limpieza borra videos por disco | «Se borraron N video(s) del experimento '…' porque el disco pasó del N %. Los resultados y reportes se conservan.» | `worker/tareas_programadas.py:267-269` |

**Lo que el worker NO envía:** el aviso de videos a menos de 7 días de su borrado (`avisar_vencimientos`) solo escribe en el registro; no hay correo ni notificación (D-45 lo deja abierto). Ver `worker/tareas_programadas.py:128-149`.

Variables de correo (nombres; los valores no se muestran): **`SMTP_HOST`**, **`SMTP_PORT`** (en `application.properties`, por omisión 1025), **`SMTP_USER`**, **`SMTP_PASSWORD`**, **`SMTP_AUTH`**, **`SMTP_STARTTLS`**, **`SMTP_FROM`** (remitente, por omisión `no-reply@fst.local`). El worker tiene el mismo conjunto en `worker/comun.py:155-166`.

## 4. Tipos de notificación en la aplicación

La tabla `notificacion` guarda cada aviso. El dominio cerrado (`tipo_notificacion`) tiene **seis** valores, en `domain/valores/TipoNotificacion.java:9-14` (en la base, `db/init/001_esquema.sql`):

1. `analisis completado`
2. `error del pipeline`
3. `analisis en pausa`
4. `analisis cancelado`
5. `alerta de disco`
6. `video borrado`

- **Quién crea las notificaciones:** solo el worker (`INSERT INTO notificacion` en `worker/comun.py:146`). La API **no crea** ninguna: solo las lista, cuenta las no leídas y las marca como leídas (`controller/NotificacionController.java`, `service/NotificacionServiceImpl.java`).
- **Cuál es la notificación de cada cuenta:** una fila por aviso, con su `idinstitucional` (quien subió el video) y, cuando aplica, `idexperimento`.
- **Desde:** los seis tipos ya estaban en producción (`15489f4`); la alerta de disco y la limpieza por disco se comprobaron con `git show 15489f4:worker/tareas_programadas.py`.

## 5. Qué no pude confirmar

- No ejecuté el sistema: lo anterior es lectura del código de HEAD. No confirmé que un correo real llegue, ni el texto exacto que recibe cada cuenta (el texto de los correos de la API está en el código; el de cada notificación depende de los datos del análisis).
- La vigencia de la sesión depende de `JWT_EXPIRES_HOURS` en el `.env` de cada despliegue; aquí se da el valor por omisión.
- No confirmé qué variables pone el `.env` de la versión portable: solo se citan las de `application.properties` y el código.
- `passwordTemporal` en la respuesta de la API es un dato sensible que viaja al navegador del Administrador: está en el código de HEAD, no lo evalué como riesgo.
