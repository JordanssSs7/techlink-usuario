-- HU-05: perfil del mentor (fortalezas tecnicas, cursos dominados, areas de conocimiento y disponibilidad).
-- Estos datos los usan HU-04 (emparejamiento con IA) y HU-07 (agendar sesion).

-- Catalogo de cursos: lo comparten el mentor (cursos que domina) y el estudiante (cursos criticos, HU-04).
CREATE TABLE cursos (
    id_curso INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(120) NOT NULL UNIQUE,
    activo BOOLEAN NOT NULL DEFAULT TRUE
);

-- Cursos de programacion de la carrera, de los primeros ciclos a los avanzados.
INSERT INTO cursos (nombre) VALUES
    ('Fundamentos de Programación'),
    ('Diseño de Interfaces de Programación'),
    ('Programación Orientada a Objetos'),
    ('Base de Datos'),
    ('Arquitectura de Computadoras'),
    ('Diseño Avanzado de Interfaces de Programación'),
    ('Ingeniería de Requerimientos y Diseño de Software'),
    ('Estructuras de Datos y Algoritmos'),
    ('Desarrollo de Aplicaciones en Internet'),
    ('Base de Datos Avanzado'),
    ('Construcción y Pruebas de Software'),
    ('Tecnologías Emergentes'),
    ('Desarrollo de Aplicaciones Empresariales'),
    ('Desarrollo de Aplicaciones Web'),
    ('Programación en Móviles'),
    ('Aplicaciones Móviles Multiplataforma'),
    ('Fundamentos de Inteligencia Artificial'),
    ('Desarrollo de Aplicaciones Web Avanzado'),
    ('Programación Avanzada en Móviles'),
    ('Desarrollo de Soluciones en la Nube'),
    ('Gestión de Procesos y Servicios de Software'),
    ('Desarrollo Avanzado de Aplicaciones Empresariales'),
    ('Integración Avanzada de Sistemas Empresariales'),
    ('Tópicos Avanzados de Software'),
    ('Inteligencia Artificial Aplicada');

-- Cursos que domina cada mentor.
CREATE TABLE mentor_cursos (
    id_mentor INT NOT NULL,
    id_curso INT NOT NULL,
    PRIMARY KEY (id_mentor, id_curso),
    FOREIGN KEY (id_mentor) REFERENCES mentores(id_mentor) ON DELETE CASCADE,
    FOREIGN KEY (id_curso) REFERENCES cursos(id_curso)
);

-- Fortalezas tecnicas (texto libre, por ejemplo "Java", "React").
CREATE TABLE mentor_fortalezas (
    id_mentor INT NOT NULL,
    fortaleza VARCHAR(100) NOT NULL,
    PRIMARY KEY (id_mentor, fortaleza),
    FOREIGN KEY (id_mentor) REFERENCES mentores(id_mentor) ON DELETE CASCADE
);

-- Areas de conocimiento (texto libre, por ejemplo "Backend", "Inteligencia artificial").
CREATE TABLE mentor_areas (
    id_mentor INT NOT NULL,
    area VARCHAR(100) NOT NULL,
    PRIMARY KEY (id_mentor, area),
    FOREIGN KEY (id_mentor) REFERENCES mentores(id_mentor) ON DELETE CASCADE
);

-- Disponibilidad semanal. dia_semana: 1 = lunes ... 7 = domingo.
-- Solo los horarios con activo = TRUE se consideran al recomendar o agendar.
CREATE TABLE mentor_disponibilidad (
    id_disponibilidad INT AUTO_INCREMENT PRIMARY KEY,
    id_mentor INT NOT NULL,
    dia_semana INT NOT NULL,
    hora_inicio TIME NOT NULL,
    hora_fin TIME NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    FOREIGN KEY (id_mentor) REFERENCES mentores(id_mentor) ON DELETE CASCADE,
    CONSTRAINT chk_disponibilidad_dia CHECK (dia_semana BETWEEN 1 AND 7),
    CONSTRAINT chk_disponibilidad_horas CHECK (hora_fin > hora_inicio)
);
