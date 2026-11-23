-- Sesiones de seguimiento: después de la primera reunión, el mentor puede agendar otra sesión con el estudiante en cualquier día y hora,
-- aunque no estén en su horario semanal. Esas sesiones no pertenecen a ninguna franja de disponibilidad.
ALTER TABLE sesiones MODIFY id_disponibilidad INT NULL;
