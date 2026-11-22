-- HU-27: código de verificación de la constancia. Lo genera el panel de administración al emitirla y va impreso en el PDF junto a un
-- código QR; cualquiera puede comprobar en la página pública de TechLink (/verificar/<código>) que la constancia es auténtica.
ALTER TABLE constancias ADD COLUMN codigo_verificacion VARCHAR(20) NULL;
ALTER TABLE constancias ADD UNIQUE KEY uk_constancia_codigo (codigo_verificacion);
