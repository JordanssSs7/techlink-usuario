
package com.techlink.service;

import com.techlink.model.MentorDisponibilidad;
import com.techlink.model.Sesion;

import com.techlink.repository.EstudianteRepository;
import com.techlink.repository.MentorRepository;
import com.techlink.repository.MentorDisponibilidadRepository;
import com.techlink.repository.SesionRepository;
import com.techlink.repository.SesionMentorBloqueoRepository;
import com.techlink.model.Estudiante;
import com.techlink.model.Mentor;
import com.techlink.dto.SesionResponse;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class SesionService {

    // Repositorios necesarios para HU-07
    private final SesionRepository sesionRepository;
    private final EstudianteRepository estudianteRepository;
    private final MentorRepository mentorRepository;
    private final MentorDisponibilidadRepository disponibilidadRepository;
    private final SesionMentorBloqueoRepository bloqueoRepository;
    private final SesionNotificacionService notificacionService;
    private final ApplicationEventPublisher eventPublisher;

    // Inyección de dependencias por constructor
    public SesionService(
            SesionRepository sesionRepository,
            EstudianteRepository estudianteRepository,
            MentorRepository mentorRepository,
            MentorDisponibilidadRepository disponibilidadRepository,
            SesionMentorBloqueoRepository bloqueoRepository,
            SesionNotificacionService notificacionService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.sesionRepository = sesionRepository;
        this.estudianteRepository = estudianteRepository;
        this.mentorRepository = mentorRepository;
        this.disponibilidadRepository = disponibilidadRepository;
        this.bloqueoRepository = bloqueoRepository;
        this.notificacionService = notificacionService;
        this.eventPublisher = eventPublisher;
    }

    // Listar todas las sesiones registradas

    // HU-07: listar únicamente las sesiones del estudiante autenticado.
    @Transactional(readOnly = true)
    public List<Sesion> listarSesiones(Integer usuarioId) {

        if (usuarioId == null) {
            throw new IllegalArgumentException(
                    "El usuario autenticado es obligatorio."
            );
        }

        // Buscar el perfil de estudiante asociado al usuario.
        estudianteRepository.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "El usuario autenticado no tiene un perfil de estudiante."
                ));

        // Obtener únicamente las sesiones de ese estudiante.
        return sesionRepository.findByEstudianteUsuarioId(usuarioId);
    }


    // Validar que la fecha y las horas sean correctas
    private void validarFechaYHorario(
            LocalDate fecha,
            LocalTime horaInicio,
            LocalTime horaFin
    ) {
        if (fecha == null || horaInicio == null || horaFin == null) {
            throw new IllegalArgumentException(
                    "La fecha y las horas son obligatorias."
            );
        }

        if (!horaFin.isAfter(horaInicio)) {
            throw new IllegalArgumentException(
                    "La hora de finalización debe ser posterior a la hora de inicio."
            );
        }

        LocalDateTime inicioSesion = LocalDateTime.of(fecha, horaInicio);

        if (!inicioSesion.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException(
                    "No se pueden reservar sesiones en fechas u horas pasadas."
            );
        }
    }


    // HU-07: convertir una sesión en una respuesta para la API
    public SesionResponse convertirARespuesta(Sesion sesion) {

        if (sesion == null) {
            throw new IllegalArgumentException(
                    "La sesión no puede ser nula."
            );
        }

        SesionResponse respuesta = new SesionResponse();

        respuesta.setId(sesion.getId());
        respuesta.setEstudianteId(
                sesion.getEstudiante().getId()
        );
        respuesta.setMentorId(
                sesion.getMentor().getId()
        );
        respuesta.setDisponibilidadId(
                sesion.getDisponibilidad().getId()
        );

        respuesta.setFecha(sesion.getFecha());
        respuesta.setHoraInicio(sesion.getHoraInicio());
        respuesta.setHoraFin(sesion.getHoraFin());

        respuesta.setEstado(sesion.getEstado().name());
        respuesta.setEnlaceReunion(sesion.getEnlaceReunion());

        return respuesta;
    }


    // HU-07: registrar una nueva sesión de mentoría
    @Transactional
    public Sesion agendarSesion(
            Integer usuarioId,
            Integer mentorId,
            Integer disponibilidadId,
            LocalDate fecha,
            LocalTime horaInicio,
            LocalTime horaFin,
            String enlaceReunion
    ) {

        // 1. Validar fecha y horario
        validarFechaYHorario(fecha, horaInicio, horaFin);

        if (usuarioId == null || mentorId == null ||
                disponibilidadId == null) {
            throw new IllegalArgumentException(
                    "El usuario, mentor y disponibilidad son obligatorios."
            );
        }

        if (enlaceReunion == null || enlaceReunion.isBlank()) {
            throw new IllegalArgumentException(
                    "El enlace de reunión es obligatorio."
            );
        }

        // 2. Verificar que el estudiante exista

        Estudiante estudiante = estudianteRepository.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "El usuario autenticado no tiene un perfil de estudiante."
                ));

        // 3. Obtener el mentor con bloqueo de escritura
        Mentor mentor = bloqueoRepository.buscarMentorConBloqueo(mentorId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "El mentor seleccionado no existe."
                ));

        // 4. Verificar que la disponibilidad exista
        MentorDisponibilidad disponibilidad =
                disponibilidadRepository.findById(disponibilidadId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "La disponibilidad seleccionada no existe."
                        ));

        // 5. Validar el horario del mentor
        validarDisponibilidadMentor(
                disponibilidad,
                mentorId,
                fecha,
                horaInicio,
                horaFin
        );

        // 6. Verificar que el horario no esté reservado
        validarHorarioDisponible(
                mentorId,
                fecha,
                horaInicio,
                horaFin
        );

        // 7. Crear la sesión
        Sesion sesion = new Sesion();

        sesion.setEstudiante(estudiante);
        sesion.setMentor(mentor);
        sesion.setDisponibilidad(disponibilidad);
        sesion.setFecha(fecha);
        sesion.setHoraInicio(horaInicio);
        sesion.setHoraFin(horaFin);
        sesion.setEstado(Sesion.EstadoSesion.CONFIRMADA);
        sesion.setEnlaceReunion(enlaceReunion.trim());

        // 8. Guardar la sesión en MariaDB

        Sesion sesionGuardada = sesionRepository.save(sesion);

        String correoEstudiante = estudiante.getUsuario()
                .getCorreoInstitucional();

        String correoMentor = mentor.getUsuario()
                .getCorreoInstitucional();

        SesionConfirmadaEvent evento = new SesionConfirmadaEvent(
                sesionGuardada.getId(),
                correoEstudiante,
                correoMentor,
                sesionGuardada.getFecha(),
                sesionGuardada.getHoraInicio(),
                sesionGuardada.getHoraFin(),
                sesionGuardada.getEnlaceReunion()
        );

        eventPublisher.publishEvent(evento);

        return sesionGuardada;
    }

    // Validar que el mentor esté disponible
    private void validarDisponibilidadMentor(
            MentorDisponibilidad disponibilidad,
            Integer mentorId,
            LocalDate fecha,
            LocalTime horaInicio,
            LocalTime horaFin
    ) {
        if (disponibilidad == null) {
            throw new IllegalArgumentException(
                    "La disponibilidad seleccionada no existe."
            );
        }

        if (!Boolean.TRUE.equals(disponibilidad.getActivo())) {
            throw new IllegalArgumentException(
                    "El horario seleccionado no está activo."
            );
        }

        if (disponibilidad.getMentor() == null ||
                !disponibilidad.getMentor().getId().equals(mentorId)) {
            throw new IllegalArgumentException(
                    "El horario no pertenece al mentor seleccionado."
            );
        }

        int diaSemana = fecha.getDayOfWeek().getValue();

        if (!disponibilidad.getDiaSemana().equals(diaSemana)) {
            throw new IllegalArgumentException(
                    "El mentor no está disponible el día seleccionado."
            );
        }

        if (horaInicio.isBefore(disponibilidad.getHoraInicio()) ||
                horaFin.isAfter(disponibilidad.getHoraFin())) {
            throw new IllegalArgumentException(
                    "La sesión debe estar dentro del horario disponible del mentor."
            );
        }
    }

    // Validar que no existan sesiones superpuestas
    private void validarHorarioDisponible(
            Integer mentorId,
            LocalDate fecha,
            LocalTime horaInicio,
            LocalTime horaFin
    ) {
        long sesionesOcupadas = sesionRepository.contarSesionesSuperpuestas(
                mentorId,
                fecha,
                horaInicio,
                horaFin
        );

        if (sesionesOcupadas > 0) {
            throw new IllegalArgumentException(
                    "El mentor ya tiene una sesión reservada en ese horario."
            );
        }
    }
}
