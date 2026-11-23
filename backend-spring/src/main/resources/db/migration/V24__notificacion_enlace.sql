-- A dónde lleva una notificación al hacer clic (por ejemplo, foro:12 abre la consulta 12 del foro).
ALTER TABLE notificaciones ADD COLUMN enlace VARCHAR(60) NULL;
