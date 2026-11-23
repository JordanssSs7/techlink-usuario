-- El enlace de cada sesión apunta a la sala virtual de TechLink (/sala/sesion/<id>) y no a una reunión externa de Meet.
-- Las sesiones anteriores se actualizan con la dirección de desarrollo; en producción se corrige con la URL pública real.
UPDATE sesiones SET enlace_reunion = CONCAT('http://localhost:5173/sala/sesion/', id_sesion) WHERE enlace_reunion LIKE '%meet.google.com%';
