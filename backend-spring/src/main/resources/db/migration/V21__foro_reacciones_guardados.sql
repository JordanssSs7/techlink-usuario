-- Foro: reacciones (me gusta, me encanta, me divierte, me sorprende, me molesta) a consultas y respuestas, y consultas guardadas.
-- objeto: CONSULTA o RESPUESTA; id_objeto es el id de esa consulta o respuesta. Una sola reacción por persona y objeto.
CREATE TABLE foro_reacciones (
    id_reaccion INT AUTO_INCREMENT PRIMARY KEY,
    id_usuario INT NOT NULL,
    objeto VARCHAR(12) NOT NULL,
    id_objeto INT NOT NULL,
    reaccion VARCHAR(20) NOT NULL,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario) ON DELETE CASCADE,
    UNIQUE KEY uk_reaccion_usuario_objeto (id_usuario, objeto, id_objeto),
    INDEX idx_reaccion_objeto (objeto, id_objeto)
);

-- Consultas que una persona guardó para volver a verlas ("Foros guardados").
CREATE TABLE foro_guardados (
    id_guardado INT AUTO_INCREMENT PRIMARY KEY,
    id_usuario INT NOT NULL,
    id_consulta INT NOT NULL,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario) ON DELETE CASCADE,
    FOREIGN KEY (id_consulta) REFERENCES consultas(id_consulta) ON DELETE CASCADE,
    UNIQUE KEY uk_guardado_usuario_consulta (id_usuario, id_consulta)
);
