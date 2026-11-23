package com.techlink.service;

import com.techlink.model.Notificacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

// Avisa al mentor que un estudiante le pidió una sesión (la aprueba o rechaza en «Mis sesiones»).
@Component
public class SesionSolicitudListener {

    private static final Logger logger = LoggerFactory.getLogger(SesionSolicitudListener.class);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final NotificacionService notificaciones;

    public SesionSolicitudListener(NotificacionService notificaciones) {
        this.notificaciones = notificaciones;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void alSolicitar(SesionSolicitadaEvent evento) {
        try {
            String mensaje = "%s quiere una sesión contigo el %s de %s a %s. Acéptala o recházala en Mis sesiones.".formatted(
                    evento.nombreEstudiante(), evento.fecha().format(FECHA), evento.horaInicio().format(HORA),
                    evento.horaFin().format(HORA));
            notificaciones.crear(evento.mentorUsuarioId(), Notificacion.Tipo.SESION, "Nueva solicitud de sesión", mensaje, "seccion:sesiones");
        } catch (Exception e) {
            logger.error("No se pudo avisar la solicitud de la sesion {}", evento.sesionId(), e);
        }
    }
}
