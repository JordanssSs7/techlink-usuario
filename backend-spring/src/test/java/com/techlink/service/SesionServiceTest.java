
package com.techlink.service;

import com.techlink.model.Estudiante;
import com.techlink.model.Mentor;
import com.techlink.model.MentorDisponibilidad;
import com.techlink.model.Sesion;
import com.techlink.model.Usuario;
import com.techlink.repository.EstudianteRepository;
import com.techlink.repository.MentorRepository;
import com.techlink.repository.MentorDisponibilidadRepository;
import com.techlink.repository.SesionRepository;
import com.techlink.repository.SesionMentorBloqueoRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SesionServiceTest {

    private SesionRepository sesionRepository;
    private EstudianteRepository estudianteRepository;
    private MentorDisponibilidadRepository disponibilidadRepository;
    private SesionMentorBloqueoRepository bloqueoRepository;
    private ApplicationEventPublisher eventPublisher;

    private SesionService service;

    private static final Integer USUARIO_ID = 1;
    private static final Integer MENTOR_ID = 1;
    private static final Integer DISPONIBILIDAD_ID = 1;

    private static final LocalTime INICIO = LocalTime.of(9, 0);
    private static final LocalTime FIN = LocalTime.of(10, 0);

    private LocalDate fechaFutura;

    @BeforeEach
    void preparar() {

        sesionRepository = Mockito.mock(SesionRepository.class);
        estudianteRepository = Mockito.mock(EstudianteRepository.class);
        disponibilidadRepository = Mockito.mock(MentorDisponibilidadRepository.class);
        bloqueoRepository = Mockito.mock(SesionMentorBloqueoRepository.class);
        eventPublisher = Mockito.mock(ApplicationEventPublisher.class);

        MentorRepository mentorRepository = Mockito.mock(MentorRepository.class);
        SesionNotificacionService notificacionService =
                Mockito.mock(SesionNotificacionService.class);

        service = new SesionService(
                sesionRepository,
                estudianteRepository,
                mentorRepository,
                disponibilidadRepository,
                bloqueoRepository,
                notificacionService,
                eventPublisher
        );

        // Próximo lunes, evitando fechas pasadas.
        fechaFutura = LocalDate.now().plusWeeks(2);
        while (fechaFutura.getDayOfWeek().getValue() != 1) {
            fechaFutura = fechaFutura.plusDays(1);
        }

        Usuario usuarioEstudiante = new Usuario();
        usuarioEstudiante.setCorreoInstitucional("estudiante@tecsup.edu.pe");

        Estudiante estudiante = new Estudiante();
        estudiante.setUsuario(usuarioEstudiante);

        Usuario usuarioMentor = new Usuario();
        usuarioMentor.setCorreoInstitucional("mentor@tecsup.edu.pe");

        Mentor mentor = new Mentor();
        mentor.setId(MENTOR_ID);
        mentor.setUsuario(usuarioMentor);

        MentorDisponibilidad disponibilidad = new MentorDisponibilidad();
        disponibilidad.setMentor(mentor);
        disponibilidad.setActivo(true);
        disponibilidad.setDiaSemana(1);
        disponibilidad.setHoraInicio(LocalTime.of(9, 0));
        disponibilidad.setHoraFin(LocalTime.of(11, 0));

        when(estudianteRepository.findByUsuarioId(USUARIO_ID))
                .thenReturn(Optional.of(estudiante));

        when(bloqueoRepository.buscarMentorConBloqueo(MENTOR_ID))
                .thenReturn(Optional.of(mentor));

        when(disponibilidadRepository.findById(DISPONIBILIDAD_ID))
                .thenReturn(Optional.of(disponibilidad));

        when(sesionRepository.contarSesionesSuperpuestas(
                eq(MENTOR_ID), any(LocalDate.class),
                any(LocalTime.class), any(LocalTime.class)
        )).thenReturn(0L);

        when(sesionRepository.save(any(Sesion.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    private Sesion agendar(LocalDate fecha, LocalTime inicio, LocalTime fin) {
        return service.agendarSesion(
                USUARIO_ID,
                MENTOR_ID,
                DISPONIBILIDAD_ID,
                fecha,
                inicio,
                fin,
                "https://meet.google.com/reunion-prueba"
        );
    }

    @Test
    void registraSesionCorrectamente() {
        Sesion sesion = agendar(fechaFutura, INICIO, FIN);

        assertNotNull(sesion);
        assertEquals(Sesion.EstadoSesion.CONFIRMADA, sesion.getEstado());
        assertEquals(fechaFutura, sesion.getFecha());
        assertEquals(INICIO, sesion.getHoraInicio());
        assertEquals(FIN, sesion.getHoraFin());

        verify(sesionRepository).save(any(Sesion.class));
        verify(eventPublisher).publishEvent(any(SesionConfirmadaEvent.class));
    }

    @Test
    void rechazaReservaDuplicada() {
        when(sesionRepository.contarSesionesSuperpuestas(
                eq(MENTOR_ID), any(LocalDate.class),
                any(LocalTime.class), any(LocalTime.class)
        )).thenReturn(1L);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> agendar(fechaFutura, INICIO, FIN)
        );

        assertTrue(error.getMessage().contains("ya tiene una sesión"));
        verify(sesionRepository, never()).save(any(Sesion.class));
    }

    @Test
    void rechazaFechaPasada() {
        LocalDate fechaPasada = LocalDate.now().minusDays(1);

        assertThrows(IllegalArgumentException.class,
                () -> agendar(fechaPasada, INICIO, FIN));

        verify(sesionRepository, never()).save(any(Sesion.class));
    }

    @Test
    void rechazaHorarioFueraDeDisponibilidad() {
        assertThrows(IllegalArgumentException.class,
                () -> agendar(
                        fechaFutura,
                        LocalTime.of(12, 0),
                        LocalTime.of(13, 0)
                ));

        verify(sesionRepository, never()).save(any(Sesion.class));
    }

    @Test
    void rechazaHoraFinAnteriorAlInicio() {
        assertThrows(IllegalArgumentException.class,
                () -> agendar(
                        fechaFutura,
                        LocalTime.of(11, 0),
                        LocalTime.of(9, 0)
                ));

        verify(sesionRepository, never()).save(any(Sesion.class));
    }

    @Test
    void permiteHorariosConsecutivos() {
        Sesion sesion = agendar(
                fechaFutura,
                LocalTime.of(10, 0),
                LocalTime.of(11, 0)
        );

        assertNotNull(sesion);
        assertEquals(LocalTime.of(10, 0), sesion.getHoraInicio());
        assertEquals(LocalTime.of(11, 0), sesion.getHoraFin());

        verify(sesionRepository).save(any(Sesion.class));
    }
}
