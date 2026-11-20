-- HU-29: un usuario reporta a otro usuario o una sesión.
-- HU-52: el administrador revisa los reportes (los resuelve o los descarta con una nota).
-- HU-53: el administrador oculta calificaciones o comentarios inapropiados. Una calificación oculta no se muestra
--        en el perfil público del mentor ni cuenta para su reputación.
-- HU-59: historial de las acciones que hace el administrador en el panel (auditoría).

-- ---------- HU-29 / HU-52 ----------
-- tipo SESION: id_sesion es la sesión reportada e id_usuario_reportado es la otra persona de esa sesión.
-- tipo USUARIO: solo se indica id_usuario_reportado.
CREATE TABLE reportes (
    id_reporte INT AUTO_INCREMENT PRIMARY KEY,
    id_reportante INT NOT NULL,
    tipo ENUM('USUARIO', 'SESION') NOT NULL,
    id_usuario_reportado INT NOT NULL,
    id_sesion INT NULL,
    motivo ENUM('CONDUCTA_INADECUADA', 'ACOSO', 'CONTENIDO_OFENSIVO', 'INCUMPLIMIENTO', 'SPAM', 'OTRO') NOT NULL,
    descripcion VARCHAR(500) NOT NULL,
    estado ENUM('PENDIENTE', 'RESUELTO', 'DESCARTADO') NOT NULL DEFAULT 'PENDIENTE',
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_revision TIMESTAMP NULL,
    id_revisor INT NULL,
    nota_revision VARCHAR(500) NULL,

    FOREIGN KEY (id_reportante) REFERENCES usuarios(id_usuario) ON DELETE CASCADE,
    FOREIGN KEY (id_usuario_reportado) REFERENCES usuarios(id_usuario) ON DELETE CASCADE,
    FOREIGN KEY (id_sesion) REFERENCES sesiones(id_sesion) ON DELETE CASCADE,
    FOREIGN KEY (id_revisor) REFERENCES usuarios(id_usuario),
    INDEX idx_reporte_estado (estado, fecha_creacion),
    INDEX idx_reporte_reportado (id_usuario_reportado)
);

-- ---------- HU-53 ----------
ALTER TABLE calificaciones
    ADD COLUMN oculto BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN fecha_moderacion TIMESTAMP NULL,
    ADD COLUMN motivo_moderacion VARCHAR(300) NULL;

-- ---------- HU-59 ----------
-- id_admin queda en NULL si la acción se hizo sin identificar al administrador (el panel sin token configurado).
CREATE TABLE acciones_admin (
    id_accion INT AUTO_INCREMENT PRIMARY KEY,
    id_admin INT NULL,
    tipo VARCHAR(40) NOT NULL,
    entidad VARCHAR(30) NOT NULL,
    id_entidad INT NULL,
    detalle VARCHAR(500) NULL,
    fecha TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (id_admin) REFERENCES usuarios(id_usuario) ON DELETE SET NULL,
    INDEX idx_accion_fecha (fecha),
    INDEX idx_accion_tipo (tipo, fecha),
    INDEX idx_accion_admin (id_admin, fecha)
);
