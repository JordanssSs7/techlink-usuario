-- HU-27: constancia de mentoría. Cuando un mentor llega a las horas mínimas (50) se registra aquí como PENDIENTE; un
-- administrador la EMITE desde el panel (HU-27 / panel de admin) y recién entonces el mentor puede descargarla.
-- Una sola fila por mentor.
CREATE TABLE constancias (
    id_constancia INT AUTO_INCREMENT PRIMARY KEY,
    id_mentor INT NOT NULL UNIQUE,
    horas_al_cumplir DECIMAL(7,2) NOT NULL,
    estado VARCHAR(10) NOT NULL DEFAULT 'PENDIENTE',
    fecha_elegible TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_emision TIMESTAMP NULL,
    id_admin INT NULL,

    FOREIGN KEY (id_mentor) REFERENCES mentores(id_mentor) ON DELETE CASCADE,
    FOREIGN KEY (id_admin) REFERENCES usuarios(id_usuario) ON DELETE SET NULL,
    INDEX idx_constancia_estado (estado, fecha_elegible)
);
