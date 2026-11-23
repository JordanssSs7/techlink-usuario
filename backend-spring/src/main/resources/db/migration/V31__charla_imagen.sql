-- Foto del invitado de la charla (o una imagen alusiva): la sube el administrador y se usa de fondo de la tarjeta de la charla.
-- Se guarda como imagen reducida en base64 (data URL), de unos cientos de KB como mucho.
ALTER TABLE charlas ADD COLUMN imagen LONGTEXT NULL;
