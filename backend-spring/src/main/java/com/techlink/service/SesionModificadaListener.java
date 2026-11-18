package com.techlink.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// HU-19: avisa por correo cuando una sesión se cancela o se reprograma. Si el correo falla, el cambio ya está guardado.
@Component
public class SesionModificadaListener {

    private static final Logger logger = LoggerFactory.getLogger(SesionModificadaListener.class);

    private final SesionNotificacionService notificacionService;

    public SesionModificadaListener(SesionNotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void procesar(SesionModificadaEvent evento) {
        try {
            notificacionService.enviarCambio(evento);
            logger.info("Aviso de {} enviado para la sesion {}", evento.tipo(), evento.sesionId());
        } catch (Exception e) {
            logger.error("No se pudo avisar el cambio de la sesion {}", evento.sesionId(), e);
        }
    }
}
