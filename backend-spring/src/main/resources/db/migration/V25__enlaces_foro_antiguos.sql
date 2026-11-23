-- Los avisos del foro anteriores a V24 no tienen enlace: se les asigna la consulta cuyo título aparece entre « » en el mensaje.
UPDATE notificaciones n
JOIN consultas c ON n.mensaje LIKE CONCAT('%«', c.titulo, '»%')
SET n.enlace = CONCAT('foro:', c.id_consulta)
WHERE n.tipo = 'FORO' AND n.enlace IS NULL;
