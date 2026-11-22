package com.techlink.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// HU-27: cada 15 minutos registra como PENDIENTE la constancia de los mentores que ya llegaron a las horas mínimas, para que
// el administrador los vea en el panel y emita su constancia aunque el mentor no haya entrado a su pantalla.
@Component
public class ConstanciaProgramador {

    private static final Logger logger = LoggerFactory.getLogger(ConstanciaProgramador.class);

    private final ConstanciaService servicio;

    public ConstanciaProgramador(ConstanciaService servicio) {
        this.servicio = servicio;
    }

    @Scheduled(initialDelayString = "${app.constancia.espera-inicial-ms:90000}",
            fixedDelayString = "${app.constancia.intervalo-ms:900000}")
    public void revisar() {
        try {
            int nuevos = servicio.registrarElegibles();
            if (nuevos > 0) {
                logger.info("{} mentores llegaron a las horas de la constancia", nuevos);
            }
        } catch (Exception e) {
            logger.error("No se pudieron revisar las constancias de los mentores", e);
        }
    }
}
