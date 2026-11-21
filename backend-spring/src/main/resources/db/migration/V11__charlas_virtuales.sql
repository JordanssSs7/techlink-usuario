-- HU-58: el administrador registra y gestiona charlas virtuales (crear, editar y desactivar).
-- HU-06: al estudiante se le recomiendan las charlas vigentes que coinciden con sus intereses o con sus cursos críticos.
-- HU-25: el estudiante se inscribe a una charla vigente y ve sus inscripciones en "Mis charlas".
-- HU-26: el calendario muestra las sesiones agendadas y las charlas inscritas.

-- Una charla se considera vigente mientras esté activa y su hora de fin no haya pasado.
-- id_curso es opcional: si la charla refuerza un curso, se recomienda a quien lo marcó como crítico (HU-04).
CREATE TABLE charlas (
    id_charla INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(150) NOT NULL,
    tematica VARCHAR(60) NOT NULL,
    descripcion VARCHAR(500) NULL,
    id_curso INT NULL,
    fecha DATE NOT NULL,
    hora_inicio TIME NOT NULL,
    hora_fin TIME NOT NULL,
    enlace VARCHAR(500) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (id_curso) REFERENCES cursos(id_curso) ON DELETE SET NULL,
    CONSTRAINT chk_charla_horas CHECK (hora_fin > hora_inicio),
    INDEX idx_charla_vigentes (activo, fecha)
);

-- Una inscripción por persona y charla (la restricción única impide inscribirse dos veces).
CREATE TABLE charla_inscripciones (
    id_inscripcion INT AUTO_INCREMENT PRIMARY KEY,
    id_charla INT NOT NULL,
    id_usuario INT NOT NULL,
    fecha_inscripcion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (id_charla) REFERENCES charlas(id_charla) ON DELETE CASCADE,
    FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario) ON DELETE CASCADE,
    UNIQUE KEY uk_charla_usuario (id_charla, id_usuario),
    INDEX idx_inscripcion_usuario (id_usuario)
);
