-- HU-09, HU-19, HU-20 y HU-21: ciclo de vida de la sesión de mentoría.
--  - La sesión guarda (opcional) el curso que se va a reforzar, para el historial (HU-20).
--  - Una sesión "completada" no tiene estado propio: es una sesión CONFIRMADA cuya hora de fin ya pasó.
--  - calificaciones: una calificación (1 a 5 estrellas) por sesión (HU-09). La reputación del mentor se guarda
--    ya calculada en mentores para no recalcularla en cada consulta.
--  - sesion_notas: las notas que escribe el mentor y el estudiante lee (HU-21).

ALTER TABLE sesiones
    ADD COLUMN id_curso INT NULL AFTER id_disponibilidad,
    ADD CONSTRAINT fk_sesion_curso FOREIGN KEY (id_curso) REFERENCES cursos(id_curso);

ALTER TABLE mentores
    ADD COLUMN reputacion DECIMAL(3,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN total_calificaciones INT NOT NULL DEFAULT 0;

CREATE TABLE calificaciones (
    id_calificacion INT AUTO_INCREMENT PRIMARY KEY,
    id_sesion INT NOT NULL UNIQUE,
    estrellas INT NOT NULL,
    comentario VARCHAR(500) NULL,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_calificacion_sesion
        FOREIGN KEY (id_sesion) REFERENCES sesiones(id_sesion) ON DELETE CASCADE,
    CONSTRAINT chk_calificacion_estrellas CHECK (estrellas BETWEEN 1 AND 5)
);

CREATE TABLE sesion_notas (
    id_nota INT AUTO_INCREMENT PRIMARY KEY,
    id_sesion INT NOT NULL UNIQUE,
    contenido TEXT NOT NULL,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_nota_sesion
        FOREIGN KEY (id_sesion) REFERENCES sesiones(id_sesion) ON DELETE CASCADE
);
