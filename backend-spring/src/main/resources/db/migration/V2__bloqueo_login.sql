-- HU-02: control de intentos fallidos de inicio de sesion (bloqueo temporal de 15 minutos)
ALTER TABLE usuarios
    ADD COLUMN intentos_fallidos INT NOT NULL DEFAULT 0,
    ADD COLUMN bloqueado_hasta TIMESTAMP NULL DEFAULT NULL;
