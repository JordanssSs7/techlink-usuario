-- Grabaciones de las sesiones: el mentor graba la reunión desde la sala; el navegador manda el video en partes y el servidor las guarda
-- en un archivo. Cuando termina queda LISTA y el estudiante y el mentor la ven desde su historial.
CREATE TABLE sesion_grabaciones (
    id_sesion INT PRIMARY KEY,
    archivo VARCHAR(200) NOT NULL,
    bytes BIGINT NOT NULL DEFAULT 0,
    partes INT NOT NULL DEFAULT 0,
    duracion_seg INT NULL,
    estado VARCHAR(10) NOT NULL DEFAULT 'EN_CURSO',
    fecha_inicio TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_fin TIMESTAMP NULL,
    FOREIGN KEY (id_sesion) REFERENCES sesiones(id_sesion) ON DELETE CASCADE
);
