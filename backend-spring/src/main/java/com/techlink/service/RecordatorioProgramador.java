package com.techlink.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// HU-23: cada 5 minutos revisa si hay sesiones próximas a las que falte enviar el recordatorio (24 horas y 1 hora antes).
@Component
public class RecordatorioProgramador {

    private static final Logger logger = LoggerFactory.getLogger(RecordatorioProgramador.class);

    private final RecordatorioSesionService servicio;

    public RecordatorioProgramador(RecordatorioSesionService servicio) {
        this.servicio = servicio;
    }

    @Scheduled(initialDelayString = "${app.recordatorios.espera-inicial-ms:60000}",
            fixedDelayString = "${app.recordatorios.intervalo-ms:300000}")
    public void revisar() {
        try {
            int enviados = servicio.enviarPendientes();
            if (enviados > 0) {
                logger.info("Se enviaron recordatorios de {} sesiones", enviados);
            }
        } catch (Exception e) {
            logger.error("No se pudieron enviar los recordatorios de sesiones", e);
        }
    }
}
