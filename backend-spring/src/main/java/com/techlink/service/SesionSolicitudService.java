package com.techlink.service;

import com.techlink.exception.SesionException;
import com.techlink.model.Notificacion;
import com.techlink.model.Sesion;
import com.techlink.repository.SesionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// El mentor responde las solicitudes de sesión que le hacen los estudiantes: al aceptarla queda CONFIRMADA (aparece en su calendario y
// en su sala, y a ambos les llega el aviso con el enlace); al rechazarla queda CANCELADA y el estudiante lo sabe.
@Service
public class SesionSolicitudService {

    private final SesionRepository sesionRepository;
    private final SesionService sesionService;
    private final NotificacionService notificaciones;
    private final ApplicationEventPublisher eventos;

    public SesionSolicitudService(SesionRepository sesionRepository, SesionService sesionService, NotificacionService notificaciones,
                                  ApplicationEventPublisher eventos) {
        this.sesionRepository = sesionRepository;
        this.sesionService = sesionService;
        this.notificaciones = notificaciones;
        this.eventos = eventos;
    }

    @Transactional
    public Sesion aceptar(Integer mentorUsuarioId, Integer sesionId) {
        Sesion sesion = pendienteDelMentor(mentorUsuarioId, sesionId);
        if (!LocalDateTime.of(sesion.getFecha(), sesion.getHoraInicio()).isAfter(LocalDateTime.now())) {
            throw new SesionException(HttpStatus.CONFLICT, "Esa hora ya pasó: rechaza la solicitud.");
        }
        try {
            // Mientras estaba pendiente pudo confirmarse otra sesión en ese horario.
            sesionService.validarHorarioDisponible(sesion.getMentor().getId(), sesion.getFecha(), sesion.getHoraInicio(), sesion.getHoraFin());
        } catch (IllegalArgumentException e) {
            throw new SesionException(HttpStatus.CONFLICT, "Ya tienes otra sesión confirmada en ese horario.");
        }
        sesion.setEstado(Sesion.EstadoSesion.CONFIRMADA);
        Sesion guardada = sesionRepository.save(sesion);
        eventos.publishEvent(new SesionConfirmadaEvent(guardada.getId(), guardada.getEstudiante().getUsuario().getCorreoInstitucional(),
                guardada.getMentor().getUsuario().getCorreoInstitucional(), guardada.getFecha(), guardada.getHoraInicio(),
                guardada.getHoraFin(), guardada.getEnlaceReunion()));
        return guardada;
    }

    @Transactional
    public Sesion rechazar(Integer mentorUsuarioId, Integer sesionId) {
        Sesion sesion = pendienteDelMentor(mentorUsuarioId, sesionId);
        sesion.setEstado(Sesion.EstadoSesion.CANCELADA);
        Sesion guardada = sesionRepository.save(sesion);
        notificaciones.crear(guardada.getEstudiante().getUsuario().getId(), Notificacion.Tipo.SESION, "Solicitud de sesión rechazada",
                SalaMensajeService.nombreDe(guardada.getMentor().getUsuario()) + " no pudo aceptar la sesión del "
                        + guardada.getFecha() + ". Puedes elegir otro horario.", "seccion:sesiones");
        return guardada;
    }

    private Sesion pendienteDelMentor(Integer mentorUsuarioId, Integer sesionId) {
        Sesion sesion = sesionRepository.findById(sesionId)
                .filter(s -> s.getMentor().getUsuario().getId().equals(mentorUsuarioId))
                .orElseThrow(() -> new SesionException(HttpStatus.NOT_FOUND, "La solicitud no existe."));
        if (sesion.getEstado() != Sesion.EstadoSesion.PENDIENTE) {
            throw new SesionException(HttpStatus.CONFLICT, "Esa solicitud ya fue respondida.");
        }
        return sesion;
    }
}
