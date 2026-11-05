-- HU-04: cursos criticos del estudiante, los que quiere reforzar. Con ellos el emparejamiento recomienda mentores.
-- El catalogo de cursos (tabla cursos) lo creo la V4 de HU-05.
CREATE TABLE estudiante_cursos_criticos (
    id_estudiante INT NOT NULL,
    id_curso INT NOT NULL,
    PRIMARY KEY (id_estudiante, id_curso),
    FOREIGN KEY (id_estudiante) REFERENCES estudiantes(id_estudiante) ON DELETE CASCADE,
    FOREIGN KEY (id_curso) REFERENCES cursos(id_curso)
);
