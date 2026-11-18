-- HU-13: perfil del usuario (descripción, intereses y foto).
-- HU-03: postulación de un estudiante para ser mentor, con su comprobante de cursos aprobados.
-- HU-15: insignias que se otorgan a los mentores al alcanzar un umbral de horas dictadas o de calificación.

-- ---------- HU-13 ----------
ALTER TABLE usuarios
    ADD COLUMN descripcion VARCHAR(300) NULL;

CREATE TABLE usuario_intereses (
    id_usuario INT NOT NULL,
    interes VARCHAR(40) NOT NULL,
    PRIMARY KEY (id_usuario, interes),
    FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario) ON DELETE CASCADE
);

-- La foto va en su propia tabla para no cargarla cada vez que se lee un usuario.
CREATE TABLE usuario_fotos (
    id_usuario INT PRIMARY KEY,
    tipo_contenido VARCHAR(30) NOT NULL,
    contenido MEDIUMBLOB NOT NULL,
    fecha_actualizacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario) ON DELETE CASCADE
);

-- ---------- HU-03 ----------
-- Estado de la postulación: EN_REVISION al enviarla; el administrador la pasa a APROBADA o RECHAZADA (HU-48).
CREATE TABLE postulaciones (
    id_postulacion INT AUTO_INCREMENT PRIMARY KEY,
    id_estudiante INT NOT NULL,
    estado ENUM('EN_REVISION', 'APROBADA', 'RECHAZADA') NOT NULL DEFAULT 'EN_REVISION',
    nombre_archivo VARCHAR(150) NOT NULL,
    tipo_contenido VARCHAR(30) NOT NULL,
    tamano_bytes INT NOT NULL,
    comentario VARCHAR(500) NULL,
    fecha_envio TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_revision TIMESTAMP NULL,
    id_revisor INT NULL,
    motivo_revision VARCHAR(500) NULL,

    FOREIGN KEY (id_estudiante) REFERENCES estudiantes(id_estudiante) ON DELETE CASCADE,
    FOREIGN KEY (id_revisor) REFERENCES usuarios(id_usuario),
    INDEX idx_postulacion_estado (estado, fecha_envio),
    INDEX idx_postulacion_estudiante (id_estudiante)
);

-- El comprobante (PDF o imagen, máximo 5 MB) va aparte para no cargarlo al listar postulaciones.
CREATE TABLE postulacion_archivos (
    id_postulacion INT PRIMARY KEY,
    contenido MEDIUMBLOB NOT NULL,
    FOREIGN KEY (id_postulacion) REFERENCES postulaciones(id_postulacion) ON DELETE CASCADE
);

-- ---------- HU-15 ----------
-- tipo HORAS: se otorga al llegar a "umbral" horas dictadas.
-- tipo PROMEDIO: se otorga si el promedio de estrellas es >= "umbral" con al menos "minimo_calificaciones" calificaciones.
CREATE TABLE insignias (
    id_insignia INT AUTO_INCREMENT PRIMARY KEY,
    codigo VARCHAR(40) NOT NULL UNIQUE,
    nombre VARCHAR(60) NOT NULL,
    descripcion VARCHAR(200) NOT NULL,
    tipo ENUM('HORAS', 'PROMEDIO') NOT NULL,
    umbral DECIMAL(5, 2) NOT NULL,
    minimo_calificaciones INT NOT NULL DEFAULT 0
);

CREATE TABLE mentor_insignias (
    id_mentor INT NOT NULL,
    id_insignia INT NOT NULL,
    fecha_otorgada TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_mentor, id_insignia),
    FOREIGN KEY (id_mentor) REFERENCES mentores(id_mentor) ON DELETE CASCADE,
    FOREIGN KEY (id_insignia) REFERENCES insignias(id_insignia) ON DELETE CASCADE
);

INSERT INTO insignias (codigo, nombre, descripcion, tipo, umbral, minimo_calificaciones) VALUES
    ('PRIMERA_MENTORIA', 'Primera mentoría', 'Dictó su primera hora de mentoría.', 'HORAS', 1, 0),
    ('MENTOR_CONSTANTE', 'Mentor constante', 'Dictó 10 horas de mentoría.', 'HORAS', 10, 0),
    ('MENTOR_EXPERTO', 'Mentor experto', 'Dictó 25 horas de mentoría.', 'HORAS', 25, 0),
    ('MENTOR_ESTRELLA', 'Mentor estrella', 'Promedio de 4.5 estrellas o más (con al menos 3 calificaciones).', 'PROMEDIO', 4.5, 3);
