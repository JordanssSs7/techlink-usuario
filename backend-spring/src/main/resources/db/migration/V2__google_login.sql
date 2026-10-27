-- HU-02: login con Google y bloqueo por intentos fallidos (decision del equipo).
-- Las cuentas creadas con Google no tienen contrasena propia (contrasena_hash puede ser NULL).
ALTER TABLE usuarios MODIFY contrasena_hash VARCHAR(255) NULL;
-- proveedor: como se creo la cuenta (LOCAL = correo y contrasena de administrador, GOOGLE = primer ingreso con Google).
ALTER TABLE usuarios ADD COLUMN proveedor ENUM('LOCAL','GOOGLE') NOT NULL DEFAULT 'LOCAL';
-- google_id: identificador estable de la cuenta de Google ("sub").
ALTER TABLE usuarios ADD COLUMN google_id VARCHAR(50) NULL UNIQUE;
-- CA-02: 3 intentos fallidos bloquean la cuenta 15 minutos.
ALTER TABLE usuarios ADD COLUMN intentos_fallidos INT NOT NULL DEFAULT 0;
ALTER TABLE usuarios ADD COLUMN bloqueado_hasta TIMESTAMP NULL;
