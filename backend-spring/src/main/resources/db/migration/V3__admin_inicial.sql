-- V3: Inserción del usuario administrador inicial
INSERT INTO usuarios (
    nombres,
    apellidos,
    correo_institucional,
    contrasena_hash,
    id_rol,
    estado,
    proveedor,
    intentos_fallidos
) VALUES (
    'Admin',
    'Sistema',
    'admin@techlink.edu.pe',
    '$2b$10$yW46pQV4ABP1ztAaQWN1jemfMAgB6MaJHEJNk7m5nPKe.VwH8AsKi',
    3,
    'ACTIVO',
    'LOCAL',
    0
);