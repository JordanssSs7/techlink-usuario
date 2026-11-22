-- HU-11: metas académicas semanales. Las acuerda el estudiante con su mentor (o se las pone él mismo) y las marca al cumplirlas.
-- HU-12: autoevaluaciones del estudiante (de 1 a 5, con una nota opcional de 0 a 20) para ver cómo evoluciona su rendimiento
--        junto a las horas de asesoría que recibió.
-- HU-61: pesos con los que el emparejamiento ordena a los mentores. El administrador los ajusta desde el panel.

-- ---------- HU-11 ----------
-- La semana de una meta sale de su fecha de entrega. id_mentor es nulo si la meta la puso el propio estudiante.
CREATE TABLE metas (
    id_meta INT AUTO_INCREMENT PRIMARY KEY,
    id_estudiante INT NOT NULL,
    id_mentor INT NULL,
    id_curso INT NULL,
    titulo VARCHAR(150) NOT NULL,
    fecha_entrega DATE NOT NULL,
    completada BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_completada TIMESTAMP NULL,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (id_estudiante) REFERENCES estudiantes(id_estudiante) ON DELETE CASCADE,
    FOREIGN KEY (id_mentor) REFERENCES mentores(id_mentor) ON DELETE SET NULL,
    FOREIGN KEY (id_curso) REFERENCES cursos(id_curso) ON DELETE SET NULL,
    INDEX idx_meta_estudiante (id_estudiante, fecha_entrega),
    INDEX idx_meta_mentor (id_mentor)
);

-- ---------- HU-12 ----------
CREATE TABLE autoevaluaciones (
    id_autoevaluacion INT AUTO_INCREMENT PRIMARY KEY,
    id_estudiante INT NOT NULL,
    id_curso INT NULL,
    puntaje INT NOT NULL,
    nota DECIMAL(4, 2) NULL,
    comentario VARCHAR(200) NULL,
    fecha DATE NOT NULL,

    FOREIGN KEY (id_estudiante) REFERENCES estudiantes(id_estudiante) ON DELETE CASCADE,
    FOREIGN KEY (id_curso) REFERENCES cursos(id_curso) ON DELETE SET NULL,
    CONSTRAINT chk_autoevaluacion_puntaje CHECK (puntaje BETWEEN 1 AND 5),
    CONSTRAINT chk_autoevaluacion_nota CHECK (nota IS NULL OR nota BETWEEN 0 AND 20),
    INDEX idx_autoevaluacion_estudiante (id_estudiante, fecha)
);

-- ---------- HU-61 ----------
-- Tres criterios con un peso de 0 a 100 cada uno; el puntaje de un mentor es el promedio ponderado de los tres.
-- Los pesos iniciales (70, 0 y 30) reproducen el cálculo que el emparejamiento ya usaba antes de que fueran ajustables.
CREATE TABLE emparejamiento_criterios (
    clave VARCHAR(20) PRIMARY KEY,
    peso INT NOT NULL,
    fecha_actualizacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    id_admin INT NULL,

    FOREIGN KEY (id_admin) REFERENCES usuarios(id_usuario) ON DELETE SET NULL,
    CONSTRAINT chk_criterio_peso CHECK (peso BETWEEN 0 AND 100)
);

INSERT INTO emparejamiento_criterios (clave, peso) VALUES
    ('CURSOS', 70),
    ('CALIFICACION', 0),
    ('DISPONIBILIDAD', 30);
