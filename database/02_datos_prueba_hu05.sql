-- HU-05: datos de PRUEBA, solo para desarrollo local (no es una migracion de Flyway).
-- Crea 3 mentores de ejemplo con cursos, fortalezas, areas y horarios, para que HU-04 (emparejamiento con IA)
-- y HU-07 (agendar sesion) puedan probarse sin esperar a que existan mentores reales.
-- Ejecutar DESPUES de que el backend haya corrido una vez (Flyway crea las tablas):
--   mysql -u root techlink < database/02_datos_prueba_hu05.sql
-- Se puede ejecutar varias veces: no duplica nada. Las cuentas son de Google (sin contrasena).

-- 1. Usuarios con rol MENTOR
INSERT IGNORE INTO usuarios (nombres, apellidos, correo_institucional, contrasena_hash, id_rol, estado, proveedor)
SELECT n, a, c, NULL, (SELECT id_rol FROM roles WHERE nombre_rol = 'MENTOR'), 'ACTIVO', 'GOOGLE'
FROM (
    SELECT 'Lucía' AS n, 'Ramírez' AS a, 'mentor.lucia@tecsup.edu.pe' AS c
    UNION ALL SELECT 'Diego', 'Torres', 'mentor.diego@tecsup.edu.pe'
    UNION ALL SELECT 'Valeria', 'Campos', 'mentor.valeria@tecsup.edu.pe'
) AS demo;

-- 2. Perfil de mentor
INSERT INTO mentores (id_usuario, especialidad, biografia)
SELECT u.id_usuario, d.especialidad, d.biografia
FROM usuarios u
JOIN (
    SELECT 'mentor.lucia@tecsup.edu.pe' AS correo, 'Backend con Java y Spring' AS especialidad, 'Mentora de bases de datos y APIs.' AS biografia
    UNION ALL SELECT 'mentor.diego@tecsup.edu.pe', 'Desarrollo web con React', 'Mentor de frontend y diseño de interfaces.'
    UNION ALL SELECT 'mentor.valeria@tecsup.edu.pe', 'Algoritmos e inteligencia artificial', 'Mentora de estructuras de datos y fundamentos de IA.'
) d ON d.correo = u.correo_institucional
WHERE NOT EXISTS (SELECT 1 FROM mentores m WHERE m.id_usuario = u.id_usuario);

-- 3. Cursos dominados
INSERT IGNORE INTO mentor_cursos (id_mentor, id_curso)
SELECT m.id_mentor, c.id_curso
FROM mentores m
JOIN usuarios u ON u.id_usuario = m.id_usuario
JOIN (
    SELECT 'mentor.lucia@tecsup.edu.pe' AS correo, 'Base de Datos' AS curso
    UNION ALL SELECT 'mentor.lucia@tecsup.edu.pe', 'Programación Orientada a Objetos'
    UNION ALL SELECT 'mentor.lucia@tecsup.edu.pe', 'Ingeniería de Requerimientos y Diseño de Software'
    UNION ALL SELECT 'mentor.diego@tecsup.edu.pe', 'Desarrollo de Aplicaciones Web'
    UNION ALL SELECT 'mentor.diego@tecsup.edu.pe', 'Programación en Móviles'
    UNION ALL SELECT 'mentor.valeria@tecsup.edu.pe', 'Estructuras de Datos y Algoritmos'
    UNION ALL SELECT 'mentor.valeria@tecsup.edu.pe', 'Fundamentos de Programación'
    UNION ALL SELECT 'mentor.valeria@tecsup.edu.pe', 'Fundamentos de Inteligencia Artificial'
) x ON x.correo = u.correo_institucional
JOIN cursos c ON c.nombre = x.curso;

-- 4. Fortalezas tecnicas
INSERT IGNORE INTO mentor_fortalezas (id_mentor, fortaleza)
SELECT m.id_mentor, x.fortaleza
FROM mentores m
JOIN usuarios u ON u.id_usuario = m.id_usuario
JOIN (
    SELECT 'mentor.lucia@tecsup.edu.pe' AS correo, 'Java' AS fortaleza
    UNION ALL SELECT 'mentor.lucia@tecsup.edu.pe', 'Spring Boot'
    UNION ALL SELECT 'mentor.lucia@tecsup.edu.pe', 'MySQL'
    UNION ALL SELECT 'mentor.diego@tecsup.edu.pe', 'React'
    UNION ALL SELECT 'mentor.diego@tecsup.edu.pe', 'JavaScript'
    UNION ALL SELECT 'mentor.valeria@tecsup.edu.pe', 'Python'
    UNION ALL SELECT 'mentor.valeria@tecsup.edu.pe', 'Estructuras de datos'
) x ON x.correo = u.correo_institucional;

-- 5. Areas de conocimiento
INSERT IGNORE INTO mentor_areas (id_mentor, area)
SELECT m.id_mentor, x.area
FROM mentores m
JOIN usuarios u ON u.id_usuario = m.id_usuario
JOIN (
    SELECT 'mentor.lucia@tecsup.edu.pe' AS correo, 'Backend' AS area
    UNION ALL SELECT 'mentor.lucia@tecsup.edu.pe', 'Bases de datos'
    UNION ALL SELECT 'mentor.diego@tecsup.edu.pe', 'Frontend'
    UNION ALL SELECT 'mentor.valeria@tecsup.edu.pe', 'Inteligencia artificial'
    UNION ALL SELECT 'mentor.valeria@tecsup.edu.pe', 'Algoritmos'
) x ON x.correo = u.correo_institucional;

-- 6. Disponibilidad (1 = lunes ... 7 = domingo). Un horario inactivo de ejemplo para Diego.
INSERT INTO mentor_disponibilidad (id_mentor, dia_semana, hora_inicio, hora_fin, activo)
SELECT m.id_mentor, x.dia, x.inicio, x.fin, x.activo
FROM mentores m
JOIN usuarios u ON u.id_usuario = m.id_usuario
JOIN (
    SELECT 'mentor.lucia@tecsup.edu.pe' AS correo, 1 AS dia, '09:00:00' AS inicio, '11:00:00' AS fin, TRUE AS activo
    UNION ALL SELECT 'mentor.lucia@tecsup.edu.pe', 3, '15:00:00', '17:00:00', TRUE
    UNION ALL SELECT 'mentor.diego@tecsup.edu.pe', 2, '10:00:00', '12:00:00', TRUE
    UNION ALL SELECT 'mentor.diego@tecsup.edu.pe', 4, '16:00:00', '18:00:00', TRUE
    UNION ALL SELECT 'mentor.diego@tecsup.edu.pe', 5, '08:00:00', '10:00:00', FALSE
    UNION ALL SELECT 'mentor.valeria@tecsup.edu.pe', 1, '14:00:00', '16:00:00', TRUE
    UNION ALL SELECT 'mentor.valeria@tecsup.edu.pe', 5, '11:00:00', '13:00:00', TRUE
) x ON x.correo = u.correo_institucional
WHERE NOT EXISTS (SELECT 1 FROM mentor_disponibilidad d WHERE d.id_mentor = m.id_mentor);
