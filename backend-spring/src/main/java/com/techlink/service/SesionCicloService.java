package com.techlink.service;

import com.techlink.exception.SesionException;
import com.techlink.model.Curso;
import com.techlink.model.Mentor;
import com.techlink.model.MentorDisponibilidad;
import com.techlink.model.Sesion;
import com.techlink.repository.CursoRepository;
import com.techlink.repository.MentorDisponibilidadRepository;
import com.techlink.repository.SesionMentorBloqueoRepository;
import com.techlink.repository.SesionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

// HU-19: el estudiante cancela o reprograma su sesión, con al menos 2 horas de anticipación.
// Cancelar libera el horario del mentor (solo las sesiones CONFIRMADAS lo bloquean) y se avisa por correo.
@Service
public class SesionCicloService {

    // Plazo mínimo, antes de que empiece la sesión, para poder cancelarla o reprogramarla.
    static final Duration ANTICIPACION_MINIMA = Duration.ofHours(2);

    private final SesionRepository sesionRepository;
    private final SesionMentorBloqueoRepository bloqueoRepository;
    private final MentorDisponibilidadRepository disponibilidadRepository;
    private final CursoRepository cursoRepository;
    private final SesionService sesionService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock reloj;

    @Autowired
    public SesionCicloService(SesionRepository sesionRepository,
                              SesionMentorBloqueoRepository bloqueoRepository,
                              MentorDisponibilidadRepository disponibilidadRepository,
                              CursoRepository cursoRepository,
                              SesionService sesionService,
                              ApplicationEventPublisher eventPublisher) {
        this(sesionRepository, bloqueoRepository, disponibilidadRepository, cursoRepository, sesionService,
                eventPublisher, Clock.systemDefaultZone());
    }

    // El reloj se puede cambiar solo en las pruebas automaticas.
    SesionCicloService(SesionRepository sesionRepository,
                       SesionMentorBloqueoRepository bloqueoRepository,
                       MentorDisponibilidadRepository disponibilidadRepository,
                       CursoRepository cursoRepository,
                       SesionService sesionService,
                       ApplicationEventPublisher eventPublisher,
                       Clock reloj) {
        this.sesionRepository = sesionRepository;
        this.bloqueoRepository = bloqueoRepository;
        this.disponibilidadRepository = disponibilidadRepository;
        this.cursoRepository = cursoRepository;
        this.sesionService = sesionService;
        this.eventPublisher = eventPublisher;
        this.reloj = reloj;
    }

    // HU-20: guarda el curso que el estudiante quiere reforzar en la sesión recién agendada (es opcional).
    @Transactional
    public void asignarCurso(Integer sesionId, Integer cursoId) {
        if (cursoId == null) {
            return;
        }
        Curso curso = cursoRepository.findById(cursoId)
                .filter(c -> Boolean.TRUE.equals(c.getActivo()))
                .orElseThrow(() -> new SesionException(HttpStatus.BAD_REQUEST, "El curso elegido no existe."));
        Sesion sesion = sesionRepository.findById(sesionId)
                .orElseThrow(() -> new SesionException(HttpStatus.NOT_FOUND, "La sesión no existe."));
        sesion.setCurso(curso);
        sesionRepository.save(sesion);
    }

    @Transactional
    public Sesion cancelar(Integer usuarioId, Integer sesionId) {
        Sesion sesion = sesionDelEstudiante(usuarioId, sesionId);
        // Una solicitud que el mentor aún no responde se puede retirar en cualquier momento.
        if (sesion.getEstado() != Sesion.EstadoSesion.PENDIENTE) {
            validarModificable(sesion);
        }

        sesion.setEstado(Sesion.EstadoSesion.CANCELADA);
        Sesion guardada = sesionRepository.save(sesion);

        eventPublisher.publishEvent(evento(SesionModificadaEvent.Tipo.CANCELADA, guardada,
                guardada.getFecha(), guardada.getHoraInicio()));
        return guardada;
    }

    @Transactional
    public Sesion reprogramar(Integer usuarioId, Integer sesionId, Integer disponibilidadId,
                              LocalDate fecha, LocalTime horaInicio, LocalTime horaFin) {
        if (disponibilidadId == null) {
            throw new SesionException(HttpStatus.BAD_REQUEST, "Elige el nuevo horario del mentor.");
        }

        Sesion sesion = sesionDelEstudiante(usuarioId, sesionId);
        validarModificable(sesion);

        LocalDate fechaAnterior = sesion.getFecha();
        LocalTime inicioAnterior = sesion.getHoraInicio();

        Mentor mentor = bloqueoRepository.buscarMentorConBloqueo(sesion.getMentor().getId())
                .orElseThrow(() -> new SesionException(HttpStatus.NOT_FOUND, "El mentor ya no existe."));
        MentorDisponibilidad disponibilidad = disponibilidadRepository.findById(disponibilidadId)
                .orElseThrow(() -> new SesionException(HttpStatus.BAD_REQUEST,
                        "La disponibilidad seleccionada no existe."));

        try {
            sesionService.validarFechaYHorario(fecha, horaInicio, horaFin);
            sesionService.validarDisponibilidadMentor(disponibilidad, mentor.getId(), fecha, horaInicio, horaFin);
        } catch (IllegalArgumentException e) {
            throw new SesionException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        if (sesionRepository.contarSuperpuestasExcluyendo(mentor.getId(), fecha, horaInicio, horaFin, sesion.getId()) > 0) {
            throw new SesionException(HttpStatus.CONFLICT, "El mentor ya tiene una sesión reservada en ese horario.");
        }

        sesion.setDisponibilidad(disponibilidad);
        sesion.setFecha(fecha);
        sesion.setHoraInicio(horaInicio);
        sesion.setHoraFin(horaFin);
        Sesion guardada = sesionRepository.save(sesion);

        eventPublisher.publishEvent(evento(SesionModificadaEvent.Tipo.REPROGRAMADA, guardada, fechaAnterior, inicioAnterior));
        return guardada;
    }

    // La sesión debe ser del estudiante que consulta; si no, se responde como si no existiera.
    private Sesion sesionDelEstudiante(Integer usuarioId, Integer sesionId) {
        return sesionRepository.findById(sesionId)
                .filter(s -> s.getEstudiante().getUsuario().getId().equals(usuarioId))
                .orElseThrow(() -> new SesionException(HttpStatus.NOT_FOUND, "La sesión no existe."));
    }

    // Solo se puede cancelar o reprogramar una sesión confirmada que empiece dentro de 2 horas o más.
    private void validarModificable(Sesion sesion) {
        if (sesion.getEstado() != Sesion.EstadoSesion.CONFIRMADA) {
            throw new SesionException(HttpStatus.CONFLICT, "Esta sesión ya fue cancelada.");
        }
        LocalDateTime limite = LocalDateTime.of(sesion.getFecha(), sesion.getHoraInicio()).minus(ANTICIPACION_MINIMA);
        if (LocalDateTime.now(reloj).isAfter(limite)) {
            throw new SesionException(HttpStatus.CONFLICT,
                    "Solo puedes cancelar o reprogramar con al menos 2 horas de anticipación.");
        }
    }

    private SesionModificadaEvent evento(SesionModificadaEvent.Tipo tipo, Sesion sesion,
                                         LocalDate fechaAnterior, LocalTime inicioAnterior) {
        return new SesionModificadaEvent(
                tipo,
                sesion.getId(),
                sesion.getEstudiante().getUsuario().getCorreoInstitucional(),
                sesion.getMentor().getUsuario().getCorreoInstitucional(),
                fechaAnterior,
                inicioAnterior,
                sesion.getFecha(),
                sesion.getHoraInicio(),
                sesion.getHoraFin(),
                sesion.getEnlaceReunion());
    }
}
