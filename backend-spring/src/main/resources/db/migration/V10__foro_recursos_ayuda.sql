-- HU-17: foro de consultas por curso (consultas, respuestas y respuesta marcada como útil).
-- HU-18: recursos que el mentor comparte en una sesión (archivo PDF o imagen de hasta 10 MB, enlace o código).
-- HU-30: centro de ayuda con preguntas frecuentes agrupadas por tema.
-- HU-54: el administrador oculta (sin borrar) consultas, respuestas y recursos; por eso llevan oculto, motivo y fecha.

-- ---------- HU-17 ----------
CREATE TABLE consultas (
    id_consulta INT AUTO_INCREMENT PRIMARY KEY,
    id_autor INT NOT NULL,
    id_curso INT NOT NULL,
    titulo VARCHAR(150) NOT NULL,
    contenido VARCHAR(2000) NOT NULL,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    oculto BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_moderacion TIMESTAMP NULL,
    motivo_moderacion VARCHAR(300) NULL,

    FOREIGN KEY (id_autor) REFERENCES usuarios(id_usuario) ON DELETE CASCADE,
    FOREIGN KEY (id_curso) REFERENCES cursos(id_curso),
    INDEX idx_consulta_curso (id_curso, fecha_creacion),
    INDEX idx_consulta_fecha (fecha_creacion)
);

-- "util" marca la respuesta que el autor de la consulta eligió como la que le sirvió; solo hay una por consulta.
CREATE TABLE respuestas (
    id_respuesta INT AUTO_INCREMENT PRIMARY KEY,
    id_consulta INT NOT NULL,
    id_autor INT NOT NULL,
    contenido VARCHAR(2000) NOT NULL,
    util BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    oculto BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_moderacion TIMESTAMP NULL,
    motivo_moderacion VARCHAR(300) NULL,

    FOREIGN KEY (id_consulta) REFERENCES consultas(id_consulta) ON DELETE CASCADE,
    FOREIGN KEY (id_autor) REFERENCES usuarios(id_usuario) ON DELETE CASCADE,
    INDEX idx_respuesta_consulta (id_consulta, util, fecha_creacion)
);

-- ---------- HU-18 ----------
-- tipo ARCHIVO usa nombre_archivo, tipo_contenido y tamano_bytes (el contenido va en recurso_archivos);
-- tipo ENLACE usa url; tipo CODIGO usa contenido_texto (y, si quiere, lenguaje).
CREATE TABLE recursos (
    id_recurso INT AUTO_INCREMENT PRIMARY KEY,
    id_sesion INT NOT NULL,
    id_autor INT NOT NULL,
    tipo ENUM('ARCHIVO', 'ENLACE', 'CODIGO') NOT NULL,
    titulo VARCHAR(150) NOT NULL,
    descripcion VARCHAR(300) NULL,
    url VARCHAR(500) NULL,
    contenido_texto TEXT NULL,
    lenguaje VARCHAR(30) NULL,
    nombre_archivo VARCHAR(150) NULL,
    tipo_contenido VARCHAR(30) NULL,
    tamano_bytes INT NULL,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    oculto BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_moderacion TIMESTAMP NULL,
    motivo_moderacion VARCHAR(300) NULL,

    FOREIGN KEY (id_sesion) REFERENCES sesiones(id_sesion) ON DELETE CASCADE,
    FOREIGN KEY (id_autor) REFERENCES usuarios(id_usuario) ON DELETE CASCADE,
    INDEX idx_recurso_sesion (id_sesion, fecha_creacion),
    INDEX idx_recurso_fecha (fecha_creacion)
);

-- El archivo va aparte para no cargarlo al listar recursos.
CREATE TABLE recurso_archivos (
    id_recurso INT PRIMARY KEY,
    contenido MEDIUMBLOB NOT NULL,
    FOREIGN KEY (id_recurso) REFERENCES recursos(id_recurso) ON DELETE CASCADE
);

-- ---------- HU-30 ----------
CREATE TABLE ayuda_temas (
    id_tema INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(80) NOT NULL UNIQUE,
    orden INT NOT NULL DEFAULT 0
);

CREATE TABLE ayuda_preguntas (
    id_pregunta INT AUTO_INCREMENT PRIMARY KEY,
    id_tema INT NOT NULL,
    pregunta VARCHAR(200) NOT NULL,
    respuesta VARCHAR(1500) NOT NULL,
    orden INT NOT NULL DEFAULT 0,
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    FOREIGN KEY (id_tema) REFERENCES ayuda_temas(id_tema) ON DELETE CASCADE,
    INDEX idx_pregunta_tema (id_tema, orden)
);

INSERT INTO ayuda_temas (nombre, orden) VALUES
    ('Cuenta y acceso', 1),
    ('Mentorías y sesiones', 2),
    ('Ser mentor e insignias', 3),
    ('Foro y recursos', 4);

INSERT INTO ayuda_preguntas (id_tema, pregunta, respuesta, orden)
SELECT t.id_tema, p.pregunta, p.respuesta, p.orden
FROM (
    SELECT 'Cuenta y acceso' AS tema, 1 AS orden,
           '¿Cómo inicio sesión en TechLink?' AS pregunta,
           'Pulsa «Iniciar sesión con Google» y elige tu cuenta institucional (@tecsup.edu.pe). La primera vez que entras, tu cuenta se crea sola y te pedimos elegir tu ciclo.' AS respuesta
    UNION ALL SELECT 'Cuenta y acceso', 2,
           '¿Puedo entrar con mi correo personal de Gmail?',
           'No. TechLink es solo para la comunidad de Tecsup, así que únicamente se aceptan cuentas de Google con el correo institucional.'
    UNION ALL SELECT 'Cuenta y acceso', 3,
           '¿Cómo cambio mi foto, mi descripción o mi ciclo?',
           'Entra a «Mi cuenta» en el menú. Ahí puedes subir una foto (JPG o PNG de hasta 2 MB), escribir una descripción de hasta 300 caracteres, cambiar tu ciclo y agregar tus intereses. No olvides pulsar «Guardar cambios».'
    UNION ALL SELECT 'Mentorías y sesiones', 1,
           '¿Cómo encuentro un mentor?',
           'Tienes tres caminos: «Buscar mentor» (eliges tus cursos y tu horario y la IA te propone los 3 mejores), el asistente LlamaLink (le cuentas qué necesitas en un chat) o la sección «Mentores», con el ranking y el perfil de cada uno.'
    UNION ALL SELECT 'Mentorías y sesiones', 2,
           '¿Cómo agendo una sesión?',
           'Desde las recomendaciones o desde el perfil de un mentor pulsa «Agendar sesión», elige la fecha y uno de sus horarios libres y confirma. Te llegará un correo con el enlace de la reunión.'
    UNION ALL SELECT 'Mentorías y sesiones', 3,
           '¿Puedo cancelar o reprogramar una sesión?',
           'Sí, desde «Mis sesiones», hasta 2 horas antes de que empiece. Al cancelar se libera el horario del mentor y se le avisa por correo. Con menos de 2 horas ya no es posible.'
    UNION ALL SELECT 'Mentorías y sesiones', 4,
           '¿Cómo califico una sesión?',
           'Cuando la sesión termina, aparece el botón «Calificar» en «Mis sesiones». Eliges de 1 a 5 estrellas y, si quieres, dejas un comentario. Solo se puede calificar una vez.'
    UNION ALL SELECT 'Ser mentor e insignias', 1,
           '¿Cómo postulo para ser mentor?',
           'Desde el ciclo 4, entra a «Ser mentor» y adjunta tu comprobante de cursos aprobados (PDF o imagen de hasta 5 MB). Tu postulación queda «En revisión» y un administrador la evalúa; te avisamos el resultado.'
    UNION ALL SELECT 'Ser mentor e insignias', 2,
           '¿Cómo gano insignias?',
           'Se otorgan solas: «Primera mentoría» con tu primera hora dictada, «Mentor constante» con 10 horas, «Mentor experto» con 25 horas y «Mentor estrella» con un promedio de 4.5 o más (con al menos 3 calificaciones). Se ven en tu perfil público y en el ranking.'
    UNION ALL SELECT 'Foro y recursos', 1,
           '¿Cómo publico una duda en el foro?',
           'Entra a «Foro», pulsa «Nueva consulta», elige el curso, escribe un título y tu duda. Otros estudiantes y mentores podrán responderte.'
    UNION ALL SELECT 'Foro y recursos', 2,
           '¿Qué es una respuesta útil?',
           'Si una respuesta resolvió tu duda, márcala como útil. El autor de la consulta es el único que puede hacerlo, y esa respuesta se muestra primero para quien llegue después.'
    UNION ALL SELECT 'Foro y recursos', 3,
           '¿Dónde veo los recursos que compartió mi mentor?',
           'En «Mis sesiones», dentro de cada sesión, en el botón «Recursos». Los mentores comparten archivos PDF o imágenes (hasta 10 MB), enlaces y fragmentos de código, y tú puedes verlos y descargarlos.'
    UNION ALL SELECT 'Foro y recursos', 4,
           '¿Por qué ocultaron una publicación mía?',
           'Un administrador oculta las consultas, respuestas y recursos que son inapropiados o ajenos a lo académico. Te llega un correo con el motivo. Si crees que fue un error, escribe a soporte.'
) p
JOIN ayuda_temas t ON t.nombre = p.tema;
