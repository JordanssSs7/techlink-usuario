package com.techlink.service;

import com.techlink.model.Estudiante;
import com.techlink.model.Mentor;
import com.techlink.model.Sesion;
import com.techlink.model.Usuario;
import com.techlink.repository.MentorRepository;
import com.techlink.repository.SesionMentorBloqueoRepository;
import com.techlink.repository.SesionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Seguimiento: el mentor agenda otra sesión (aunque no esté en su horario semanal) solo con estudiantes con los que ya tuvo una.
class SesionSeguimientoServiceTest {

    private static final int MENTOR_USUARIO = 10;
    private static final LocalDate MANANA = LocalDate.now().plusDays(1);

    private SesionRepository sesiones;
    private SesionService sesionService;
    private ApplicationEventPublisher eventos;
    private SesionSeguimientoService servicio;
    private Estudiante estudiante;

    @BeforeEach
    void preparar() {
        sesiones = Mockito.mock(SesionRepository.class);
        MentorRepository mentores = Mockito.mock(MentorRepository.class);
        SesionMentorBloqueoRepository bloqueo = Mockito.mock(SesionMentorBloqueoRepository.class);
        sesionService = Mockito.mock(SesionService.class);
        eventos = Mockito.mock(ApplicationEventPublisher.class);

        Usuario usuarioMentor = PruebaSesiones.usuario(MENTOR_USUARIO, "Jordan", "Reyes", "jordan@tecsup.edu.pe");
        Mentor mentor = new Mentor();
        mentor.setId(4);
        mentor.setUsuario(usuarioMentor);
        when(mentores.findByUsuarioId(MENTOR_USUARIO)).thenReturn(Optional.of(mentor));
        when(bloqueo.buscarMentorConBloqueo(4)).thenReturn(Optional.of(mentor));

        estudiante = new Estudiante();
        estudiante.setId(7);
        estudiante.setUsuario(PruebaSesiones.usuario(20, "Yoselin", "Flores", "yoselin@tecsup.edu.pe"));
        when(sesiones.estudiantesDeMentor(MENTOR_USUARIO)).thenReturn(List.of(estudiante));
        when(sesiones.save(any(Sesion.class))).thenAnswer(i -> {
            Sesion s = i.getArgument(0);
            s.setId(99);
            return s;
        });
        servicio = new SesionSeguimientoService(sesiones, mentores, bloqueo, sesionService, eventos);
    }

    @Test
    void agendaSinDisponibilidadConUnEstudianteConElQueYaTuvoSesion() {
        Sesion s = servicio.agendar(MENTOR_USUARIO, 7, MANANA, LocalTime.of(15, 0), LocalTime.of(16, 30), "https://meet/x");

        assertNull(s.getDisponibilidad());
        assertEquals(LocalTime.of(16, 30), s.getHoraFin());
        assertEquals(Sesion.EstadoSesion.CONFIRMADA, s.getEstado());
        verify(eventos).publishEvent(any(SesionConfirmadaEvent.class));
    }

    @Test
    void conUnEstudianteConElQueNuncaTuvoSesionSeRechaza() {
        assertThrows(IllegalArgumentException.class,
                () -> servicio.agendar(MENTOR_USUARIO, 55, MANANA, LocalTime.of(15, 0), LocalTime.of(16, 0), "https://meet/x"));
        verify(sesiones, never()).save(any());
    }

    @Test
    void elEnlaceApuntaALaSalaDeTechLink() {
        Sesion s = servicio.agendar(MENTOR_USUARIO, 7, MANANA, LocalTime.of(15, 0), LocalTime.of(16, 0), null);

        assertEquals("http://localhost:5173/sala/sesion/99", s.getEnlaceReunion());
    }

    @Test
    void listaLosEstudiantesDisponiblesParaSeguimiento() {
        assertEquals("Yoselin Flores", servicio.estudiantes(MENTOR_USUARIO).get(0).nombre());
    }
}
