
-- HU-07: Agendamiento de sesiones entre estudiantes y mentores.
-- Registra reservas asociadas a la disponibilidad del mentor.

CREATE TABLE sesiones (
    id_sesion INT AUTO_INCREMENT PRIMARY KEY,

    id_estudiante INT NOT NULL,
    id_mentor INT NOT NULL,
    id_disponibilidad INT NOT NULL,

    fecha DATE NOT NULL,
    hora_inicio TIME NOT NULL,
    hora_fin TIME NOT NULL,

    estado ENUM('CONFIRMADA', 'CANCELADA')
        NOT NULL DEFAULT 'CONFIRMADA',

    enlace_reunion VARCHAR(500) NOT NULL,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_sesion_estudiante
        FOREIGN KEY (id_estudiante)
        REFERENCES estudiantes(id_estudiante),

    CONSTRAINT fk_sesion_mentor
        FOREIGN KEY (id_mentor)
        REFERENCES mentores(id_mentor),

    CONSTRAINT fk_sesion_disponibilidad
        FOREIGN KEY (id_disponibilidad)
        REFERENCES mentor_disponibilidad(id_disponibilidad),

    CONSTRAINT chk_sesion_horas
        CHECK (hora_fin > hora_inicio),

    INDEX idx_sesion_mentor_fecha (id_mentor, fecha),
    INDEX idx_sesion_estudiante (id_estudiante)
);
