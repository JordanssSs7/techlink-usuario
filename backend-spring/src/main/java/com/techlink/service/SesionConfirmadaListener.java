
package com.techlink.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class SesionConfirmadaListener {

    private static final Logger logger =
            LoggerFactory.getLogger(SesionConfirmadaListener.class);

    private final SesionNotificacionService notificacionService;

    public SesionConfirmadaListener(
            SesionNotificacionService notificacionService
    ) {
        this.notificacionService = notificacionService;
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void procesarSesionConfirmada(
            SesionConfirmadaEvent evento
    ) {
        try {
            notificacionService.enviarConfirmacion(
                    evento.correoEstudiante(),
                    evento.correoMentor(),
                    evento.fecha(),
                    evento.horaInicio(),
                    evento.horaFin(),
                    evento.enlaceReunion()
            );

            logger.info(
                    "Notificaciones procesadas para la sesion {}",
                    evento.sesionId()
            );

        } catch (Exception e) {
            logger.error(
                    "No se pudieron enviar las notificaciones de la sesion {}",
                    evento.sesionId(),
                    e
            );
        }
    }
}
