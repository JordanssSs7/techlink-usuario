-- Amigos entre usuarios (estudiantes y mentores): uno envía una solicitud y el otro la acepta. Los amigos pueden ver el perfil completo
-- del otro (descripción e intereses). Una sola fila por pareja: quien la envió es id_solicitante.
CREATE TABLE amistades (
    id_amistad INT AUTO_INCREMENT PRIMARY KEY,
    id_solicitante INT NOT NULL,
    id_destinatario INT NOT NULL,
    estado VARCHAR(10) NOT NULL DEFAULT 'PENDIENTE',
    fecha_solicitud TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_respuesta TIMESTAMP NULL,

    FOREIGN KEY (id_solicitante) REFERENCES usuarios(id_usuario) ON DELETE CASCADE,
    FOREIGN KEY (id_destinatario) REFERENCES usuarios(id_usuario) ON DELETE CASCADE,
    UNIQUE KEY uk_amistad_pareja (id_solicitante, id_destinatario),
    INDEX idx_amistad_destinatario (id_destinatario, estado)
);
