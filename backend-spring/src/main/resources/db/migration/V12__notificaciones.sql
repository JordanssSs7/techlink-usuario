-- HU-22: centro de notificaciones (confirmaciones, cambios de sesión, recordatorios y anuncios).
-- HU-23: recordatorios por correo 24 horas y 1 hora antes de cada sesión.
-- HU-24: preferencias de notificación por tipo y canal.
-- HU-57: el administrador publica anuncios por rol; el panel de admin inserta una notificación ANUNCIO por destinatario.

-- Una fila por aviso recibido. tipo: SESION (confirmaciones y cambios), RECORDATORIO o ANUNCIO.
CREATE TABLE notificaciones (
    id_notificacion INT AUTO_INCREMENT PRIMARY KEY,
    id_usuario INT NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    titulo VARCHAR(150) NOT NULL,
    mensaje VARCHAR(500) NOT NULL,
    leida BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario) ON DELETE CASCADE,
    INDEX idx_notificacion_usuario (id_usuario, leida, fecha_creacion)
);

-- Preferencias por usuario y tipo. Si no hay fila, se reciben los dos canales (en la aplicación y por correo).
CREATE TABLE preferencias_notificacion (
    id_preferencia INT AUTO_INCREMENT PRIMARY KEY,
    id_usuario INT NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    en_app BOOLEAN NOT NULL DEFAULT TRUE,
    correo BOOLEAN NOT NULL DEFAULT TRUE,

    FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario) ON DELETE CASCADE,
    UNIQUE KEY uk_preferencia_usuario_tipo (id_usuario, tipo)
);

-- Recordatorios ya enviados. La combinación (sesión, momento, inicio de la sesión) es única: así no se repite el aviso,
-- y si la sesión se reprograma (cambia su inicio) se vuelve a avisar para el nuevo horario.
-- momento: H24 (a menos de 24 horas) o H1 (a menos de 1 hora).
CREATE TABLE sesion_recordatorios (
    id_recordatorio INT AUTO_INCREMENT PRIMARY KEY,
    id_sesion INT NOT NULL,
    momento VARCHAR(5) NOT NULL,
    inicio_sesion DATETIME NOT NULL,
    fecha_envio TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (id_sesion) REFERENCES sesiones(id_sesion) ON DELETE CASCADE,
    UNIQUE KEY uk_recordatorio (id_sesion, momento, inicio_sesion)
);

-- HU-57: anuncios que el administrador publica para un rol. Al publicar, el panel de admin inserta una notificación
-- ANUNCIO por cada usuario activo del rol (salvo quien desactivó los anuncios en sus preferencias).
-- rol_destino: ESTUDIANTE, MENTOR o TODOS. destinatarios: a cuántos usuarios les llegó.
CREATE TABLE anuncios (
    id_anuncio INT AUTO_INCREMENT PRIMARY KEY,
    titulo VARCHAR(150) NOT NULL,
    mensaje VARCHAR(500) NOT NULL,
    rol_destino VARCHAR(12) NOT NULL,
    id_admin INT NULL,
    destinatarios INT NOT NULL DEFAULT 0,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (id_admin) REFERENCES usuarios(id_usuario) ON DELETE SET NULL,
    INDEX idx_anuncio_fecha (fecha_creacion)
);
