package com.techlink.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// HU-15: los procesos que otorgan insignias solos.
//  - Cada 15 minutos revisa a todos los mentores (las horas dictadas suben con el tiempo, sin que nadie haga nada).
//  - Apenas un estudiante califica una sesión, revisa a ese mentor (el promedio cambia en ese momento).
@Component
public class InsigniaProgramador {

    // Se envía al guardar una calificación.
    public record CalificacionRegistrada(Integer mentorId) {
    }

    private static final Logger logger = LoggerFactory.getLogger(InsigniaProgramador.class);

    private final InsigniaService insigniaService;

    public InsigniaProgramador(InsigniaService insigniaService) {
        this.insigniaService = insigniaService;
    }

    @Scheduled(initialDelayString = "${app.insignias.espera-inicial-ms:60000}",
            fixedDelayString = "${app.insignias.intervalo-ms:900000}")
    public void revisarTodos() {
        try {
            int otorgadas = insigniaService.evaluarTodos();
            if (otorgadas > 0) {
                logger.info("Se otorgaron {} insignias nuevas", otorgadas);
            }
        } catch (Exception e) {
            logger.error("No se pudieron revisar las insignias", e);
        }
    }

    // Después de guardar la calificación; en una transacción nueva porque la anterior ya terminó.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void alCalificar(CalificacionRegistrada evento) {
        try {
            insigniaService.evaluar(evento.mentorId());
        } catch (Exception e) {
            logger.error("No se pudieron revisar las insignias del mentor {}", evento.mentorId(), e);
        }
    }
}
