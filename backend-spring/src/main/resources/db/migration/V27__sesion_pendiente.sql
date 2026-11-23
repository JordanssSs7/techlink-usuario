-- Solicitudes de sesión: cuando un estudiante reserva, la sesión queda PENDIENTE hasta que el mentor la acepta (CONFIRMADA) o la rechaza (CANCELADA).
ALTER TABLE sesiones MODIFY estado ENUM('PENDIENTE', 'CONFIRMADA', 'CANCELADA') NOT NULL DEFAULT 'CONFIRMADA';
