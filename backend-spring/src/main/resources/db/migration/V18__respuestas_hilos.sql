-- HU-17: respuestas dentro de una respuesta del foro (como los comentarios de Instagram o Facebook). Un solo nivel: responder a
-- una respuesta que ya es de un hilo la agrega al mismo hilo. id_respuesta_padre es null en las respuestas principales.
ALTER TABLE respuestas ADD COLUMN id_respuesta_padre INT NULL;
ALTER TABLE respuestas ADD CONSTRAINT fk_respuesta_padre FOREIGN KEY (id_respuesta_padre) REFERENCES respuestas(id_respuesta) ON DELETE CASCADE;
CREATE INDEX idx_respuesta_padre ON respuestas(id_respuesta_padre);
