package com.techlink.service;

import com.techlink.model.Notificacion;
import com.techlink.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;

// HU-22: además del correo, cada confirmación, cancelación o reprogramación de una sesión deja un aviso en el centro de
// notificaciones del estudiante y del mentor. Se guarda después de confirmar el cambio (en una transacción nueva): si
// falla, la sesión ya quedó guardada y solo se registra el error.
@Component
public class SesionAvisosListener {

    private static final Logger logger = LoggerFactory.getLogger(SesionAvisosListener.class);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final NotificacionService notificaciones;
    private final UsuarioRepository usuarioRepository;

    public SesionAvisosListener(NotificacionService notificaciones, UsuarioRepository usuarioRepository) {
        this.notificaciones = notificaciones;
        this.usuarioRepository = usuarioRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void alConfirmar(SesionConfirmadaEvent evento) {
        String mensaje = "Tu sesión de mentoría del %s a las %s quedó confirmada. Enlace: %s".formatted(
                evento.fecha().format(FECHA), evento.horaInicio().format(HORA), evento.enlaceReunion());
        avisar(evento.sesionId(), "Sesión confirmada", mensaje, evento.correoEstudiante(), evento.correoMentor());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void alModificar(SesionModificadaEvent evento) {
        String anterior = "%s a las %s".formatted(evento.fechaAnterior().format(FECHA), evento.horaInicioAnterior().format(HORA));
        if (evento.tipo() == SesionModificadaEvent.Tipo.CANCELADA) {
            avisar(evento.sesionId(), "Sesión cancelada", "La sesión de mentoría del " + anterior + " fue cancelada.",
                    evento.correoEstudiante(), evento.correoMentor());
        } else {
            String mensaje = "La sesión del %s fue reprogramada para el %s a las %s. Enlace: %s".formatted(
                    anterior, evento.fecha().format(FECHA), evento.horaInicio().format(HORA), evento.enlaceReunion());
            avisar(evento.sesionId(), "Sesión reprogramada", mensaje, evento.correoEstudiante(), evento.correoMentor());
        }
    }

    private void avisar(Integer sesionId, String titulo, String mensaje, String... correos) {
        try {
            for (String correo : correos) {
                usuarioRepository.findByCorreoInstitucional(correo)
                        .ifPresent(u -> notificaciones.crear(u.getId(), Notificacion.Tipo.SESION, titulo, mensaje));
            }
        } catch (Exception e) {
            logger.error("No se pudieron guardar los avisos de la sesion {}", sesionId, e);
        }
    }
}
