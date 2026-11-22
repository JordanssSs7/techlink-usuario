-- HU-60: parámetros de seguridad que el administrador cambia desde el panel (dominio institucional permitido, intentos
-- máximos de login y minutos de bloqueo). Siempre hay UNA sola fila (id 1) y los cambios se aplican en los siguientes
-- inicios de sesión.
CREATE TABLE parametros_seguridad (
    id_parametros INT PRIMARY KEY,
    dominio_permitido VARCHAR(100) NOT NULL,
    max_intentos INT NOT NULL,
    minutos_bloqueo INT NOT NULL,
    id_admin INT NULL,
    fecha_actualizacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (id_admin) REFERENCES usuarios(id_usuario) ON DELETE SET NULL,
    CONSTRAINT chk_parametros_fila CHECK (id_parametros = 1),
    CONSTRAINT chk_parametros_intentos CHECK (max_intentos BETWEEN 1 AND 10),
    CONSTRAINT chk_parametros_bloqueo CHECK (minutos_bloqueo BETWEEN 1 AND 1440)
);

-- Valores con los que ya funcionaba el login (HU-02): dominio de Tecsup, 3 intentos y 15 minutos de bloqueo.
INSERT INTO parametros_seguridad (id_parametros, dominio_permitido, max_intentos, minutos_bloqueo)
VALUES (1, 'tecsup.edu.pe', 3, 15);

-- HU-08: historial del chat de la sala virtual. sala_tipo: SESION (una sesión de mentoría) o CHARLA (una charla virtual);
-- sala_id es el id de esa sesión o charla. tipo: TEXTO o CODIGO (un fragmento de código, con su lenguaje).
CREATE TABLE sala_mensajes (
    id_mensaje BIGINT AUTO_INCREMENT PRIMARY KEY,
    sala_tipo VARCHAR(10) NOT NULL,
    sala_id INT NOT NULL,
    id_usuario INT NOT NULL,
    tipo VARCHAR(10) NOT NULL,
    lenguaje VARCHAR(30) NULL,
    contenido TEXT NOT NULL,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario) ON DELETE CASCADE,
    INDEX idx_sala_mensajes (sala_tipo, sala_id, id_mensaje)
);
