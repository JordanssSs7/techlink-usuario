package com.techlink.service;

import com.techlink.model.Estudiante;
import com.techlink.model.Mentor;
import com.techlink.model.Sesion;
import com.techlink.repository.MentorRepository;
import com.techlink.repository.SesionMentorBloqueoRepository;
import com.techlink.repository.SesionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

// Sesiones de seguimiento: después de la primera reunión, el mentor agenda la siguiente con ese estudiante en el día y la hora que acuerden,
// aunque no estén en su horario semanal. Solo se puede con estudiantes con los que el mentor ya tiene (o tuvo) una sesión.
@Service
public class SesionSeguimientoService {

    // Un estudiante con el que el mentor puede agendar un seguimiento.
    public record EstudianteSeguimiento(Integer idEstudiante, String nombre) {
    }

    private final SesionRepository sesionRepository;
    private final MentorRepository mentorRepository;
    private final SesionMentorBloqueoRepository bloqueoRepository;
    private final SesionService sesionService;
    private final ApplicationEventPublisher eventos;
    private String urlBase = "http://localhost:5173";

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setUrlBase(@org.springframework.beans.factory.annotation.Value("${app.publico.url-base:http://localhost:5173}") String urlBase) {
        this.urlBase = urlBase;
    }

    public SesionSeguimientoService(SesionRepository sesionRepository, MentorRepository mentorRepository,
                                    SesionMentorBloqueoRepository bloqueoRepository, SesionService sesionService,
                                    ApplicationEventPublisher eventos) {
        this.sesionRepository = sesionRepository;
        this.mentorRepository = mentorRepository;
        this.bloqueoRepository = bloqueoRepository;
        this.sesionService = sesionService;
        this.eventos = eventos;
    }

    @Transactional(readOnly = true)
    public List<EstudianteSeguimiento> estudiantes(Integer mentorUsuarioId) {
        return sesionRepository.estudiantesDeMentor(mentorUsuarioId).stream()
                .map(e -> new EstudianteSeguimiento(e.getId(), SalaMensajeService.nombreDe(e.getUsuario())))
                .toList();
    }

    @Transactional
    public Sesion agendar(Integer mentorUsuarioId, Integer estudianteId, LocalDate fecha, LocalTime horaInicio, LocalTime horaFin,
                          String ignorado) {
        sesionService.validarFechaYHorario(fecha, horaInicio, horaFin);
        Mentor mentorBase = mentorRepository.findByUsuarioId(mentorUsuarioId)
                .orElseThrow(() -> new IllegalArgumentException("No tienes perfil de mentor."));
        Mentor mentor = bloqueoRepository.buscarMentorConBloqueo(mentorBase.getId())
                .orElseThrow(() -> new IllegalArgumentException("No tienes perfil de mentor."));
        Estudiante estudiante = sesionRepository.estudiantesDeMentor(mentorUsuarioId).stream()
                .filter(e -> e.getId().equals(estudianteId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Solo puedes agendar una nueva sesión con estudiantes con los que ya tuviste una sesión."));

        // El mentor no puede tener dos sesiones a la vez.
        sesionService.validarHorarioDisponible(mentor.getId(), fecha, horaInicio, horaFin);

        Sesion sesion = new Sesion();
        sesion.setEstudiante(estudiante);
        sesion.setMentor(mentor);
        sesion.setDisponibilidad(null);
        sesion.setFecha(fecha);
        sesion.setHoraInicio(horaInicio);
        sesion.setHoraFin(horaFin);
        sesion.setEstado(Sesion.EstadoSesion.CONFIRMADA);
        sesion.setEnlaceReunion(EnlaceSala.paraSesion(urlBase, 0));
        Sesion guardada = sesionRepository.save(sesion);
        guardada.setEnlaceReunion(EnlaceSala.paraSesion(urlBase, guardada.getId()));

        eventos.publishEvent(new SesionConfirmadaEvent(guardada.getId(), estudiante.getUsuario().getCorreoInstitucional(),
                mentor.getUsuario().getCorreoInstitucional(), guardada.getFecha(), guardada.getHoraInicio(), guardada.getHoraFin(),
                guardada.getEnlaceReunion()));
        return guardada;
    }
}
