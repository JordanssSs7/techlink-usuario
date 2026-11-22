-- HU-17: quien publica una consulta o una respuesta puede editarla; se guarda cuándo, para mostrar "(editado)".
ALTER TABLE consultas ADD COLUMN fecha_edicion TIMESTAMP NULL;
ALTER TABLE respuestas ADD COLUMN fecha_edicion TIMESTAMP NULL;

-- HU-29: cualquier usuario puede reportar una consulta o una respuesta del foro. contenido_reportado guarda lo que decía en el
-- momento del reporte: si su autor la edita o la elimina después, el administrador igual puede ver qué se reportó.
ALTER TABLE reportes MODIFY tipo ENUM('USUARIO', 'SESION', 'CONSULTA', 'RESPUESTA') NOT NULL;
ALTER TABLE reportes ADD COLUMN id_consulta INT NULL;
ALTER TABLE reportes ADD COLUMN id_respuesta INT NULL;
ALTER TABLE reportes ADD COLUMN contenido_reportado VARCHAR(500) NULL;
ALTER TABLE reportes ADD CONSTRAINT fk_reporte_consulta FOREIGN KEY (id_consulta) REFERENCES consultas(id_consulta) ON DELETE SET NULL;
ALTER TABLE reportes ADD CONSTRAINT fk_reporte_respuesta FOREIGN KEY (id_respuesta) REFERENCES respuestas(id_respuesta) ON DELETE SET NULL;
