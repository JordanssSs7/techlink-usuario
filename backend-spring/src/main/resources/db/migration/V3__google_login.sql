-- HU-02 (login con Google): las cuentas creadas con Google no tienen contrasena propia.
-- proveedor: como se creo la cuenta (LOCAL = correo y contrasena, GOOGLE = primer ingreso con Google).
-- google_id: identificador estable de la cuenta de Google ("sub"), para reconocerla aunque cambie el correo.
ALTER TABLE usuarios
    MODIFY COLUMN contrasena_hash VARCHAR(255) NULL,
    ADD COLUMN proveedor ENUM('LOCAL','GOOGLE') NOT NULL DEFAULT 'LOCAL',
    ADD COLUMN google_id VARCHAR(50) NULL,
    ADD UNIQUE KEY uk_usuarios_google_id (google_id);
