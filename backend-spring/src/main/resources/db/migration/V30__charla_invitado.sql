-- Enlace secreto del invitado de cada charla. Solo lo ve el administrador en su panel y se lo manda al invitado, que con él entra a la
-- sala de la charla sin tener cuenta. Los estudiantes y mentores nunca lo reciben.
ALTER TABLE charlas ADD COLUMN token_invitado VARCHAR(64) NULL;

UPDATE charlas SET token_invitado = MD5(CONCAT(UUID(), id_charla, RAND())) WHERE token_invitado IS NULL;

ALTER TABLE charlas ADD UNIQUE INDEX uq_charlas_token_invitado (token_invitado);
