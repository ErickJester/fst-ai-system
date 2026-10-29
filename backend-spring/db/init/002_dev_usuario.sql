-- ============================================================
--  002_dev_usuario.sql  -  SOLO PARA DESARROLLO
-- ============================================================
-- Todavia no hay inicio de sesion ni administracion de cuentas (secciones 2 y 3 de la
-- guia), asi que nadie puede crear un USUARIO desde la API. Pero todo EXPERIMENTO
-- necesita un creador (idinstitucional NOT NULL). Este usuario existe solo para poder
-- probar el CRUD de experimentos, el PDF y el correo.
--
-- contrasenahash no es un hash bcrypt valido a proposito: cuando exista el login,
-- nadie podra entrar con esta cuenta. Su correo cae en MailHog (perfil dev).
-- No es administrador: no se inserta en ADMINISTRADOR.

INSERT INTO usuario (idinstitucional, nombre, apellidos, correo, contrasenahash, activo, cambiorequerido)
VALUES ('DEV0000001', 'Usuario', 'De Desarrollo', 'dev@fst.local', '!sin-login-dev', true, true);
