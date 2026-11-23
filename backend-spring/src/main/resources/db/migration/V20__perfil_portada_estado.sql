-- Perfil estilo red social: foto de portada y una frase de estado ("¿Qué estás pensando?").
-- La portada va en su propia tabla (igual que la foto de perfil) para no cargarla cada vez que se lee un usuario.
CREATE TABLE usuario_portadas (
    id_usuario INT PRIMARY KEY,
    tipo_contenido VARCHAR(30) NOT NULL,
    contenido MEDIUMBLOB NOT NULL,
    fecha_actualizacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario) ON DELETE CASCADE
);

ALTER TABLE usuarios ADD COLUMN frase VARCHAR(80) NULL;
