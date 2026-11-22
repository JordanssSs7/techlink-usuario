package com.techlink.service;

import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.Charla;
import com.techlink.model.Sesion;
import com.techlink.repository.CharlaInscripcionRepository;
import com.techlink.repository.CharlaRepository;
import com.techlink.repository.SesionRepository;
import com.techlink.service.SalaAccesoService.Sala;
import com.techlink.service.SalaAccesoService.Tipo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static com.techlink.service.PruebaSesiones.USUARIO_ESTUDIANTE;
import static com.techlink.service.PruebaSesiones.USUARIO_MENTOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

// Pruebas de HU-08: quién puede entrar a la sala virtual de una sesión o una charla, y cuándo.
class SalaAccesoServiceTest {

    // "Ahora" para las pruebas: 20/11/2026 a las 12:00. La sala abre 30 minutos antes y cierra 60 después.
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 20, 12, 0);

    private SesionRepository sesionRepository;
    private CharlaRepository charlaRepository;
    private CharlaInscripcionRepository inscripcionRepository;
    private SalaAccesoService servicio;

    @BeforeEach
    void preparar() {
        sesionRepository = Mockito.mock(SesionRepository.class);
        charlaRepository = Mockito.mock(CharlaRepository.class);
        inscripcionRepository = Mockito.mock(CharlaInscripcionRepository.class);
        servicio = new SalaAccesoService(sesionRepository, charlaRepository, inscripcionRepository, 30, 60, 8,
                Clock.fixed(AHORA.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));
    }

    private void sesion(LocalDateTime inicio, int minutos, Sesion.EstadoSesion estado) {
        when(sesionRepository.findById(7)).thenReturn(Optional.of(PruebaSesiones.sesion(7, inicio, minutos, estado)));
    }

    private void charla(LocalDateTime inicio, int minutos, boolean activa) {
        Charla c = new Charla();
        c.setId(3);
        c.setNombre("Bases de datos en la industria");
        c.setFecha(inicio.toLocalDate());
        c.setHoraInicio(inicio.toLocalTime());
        c.setHoraFin(inicio.plusMinutes(minutos).toLocalTime());
        c.setActivo(activa);
        when(charlaRepository.findById(3)).thenReturn(Optional.of(c));
    }

    // ---- Sesiones ----

    @Test
    void elEstudianteYElMentorDeLaSesionPuedenEntrar() {
        sesion(AHORA.plusMinutes(10), 60, Sesion.EstadoSesion.CONFIRMADA);

        Sala delEstudiante = servicio.autorizar(USUARIO_ESTUDIANTE, Tipo.SESION, 7);
        Sala delMentor = servicio.autorizar(USUARIO_MENTOR, Tipo.SESION, 7);

        assertEquals("SESION-7", delEstudiante.clave());
        assertEquals(2, delEstudiante.maxParticipantes());
        assertEquals("Sesión con Lucía Ramírez", delEstudiante.titulo());
        assertEquals("Sesión con Ana Quispe", delMentor.titulo());
    }

    @Test
    void unTerceroNoPuedeEntrar() {
        sesion(AHORA.plusMinutes(10), 60, Sesion.EstadoSesion.CONFIRMADA);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class, () -> servicio.autorizar(999, Tipo.SESION, 7));

        assertEquals(HttpStatus.FORBIDDEN, error.getEstado());
    }

    @Test
    void unaSesionCanceladaNoTieneSala() {
        sesion(AHORA.plusMinutes(10), 60, Sesion.EstadoSesion.CANCELADA);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> servicio.autorizar(USUARIO_ESTUDIANTE, Tipo.SESION, 7));

        assertEquals(HttpStatus.CONFLICT, error.getEstado());
    }

    @Test
    void unaSesionInexistenteEsNoEncontrada() {
        when(sesionRepository.findById(7)).thenReturn(Optional.empty());

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> servicio.autorizar(USUARIO_ESTUDIANTE, Tipo.SESION, 7));

        assertEquals(HttpStatus.NOT_FOUND, error.getEstado());
    }

    @Test
    void laSalaAbre30MinutosAntesYCierra60MinutosDespues() {
        // La sesión es de 12:30 a 13:30: abre a las 12:00 en punto y cierra a las 14:30.
        sesion(AHORA.plusMinutes(30), 60, Sesion.EstadoSesion.CONFIRMADA);
        Sala sala = servicio.autorizar(USUARIO_ESTUDIANTE, Tipo.SESION, 7);

        assertEquals(AHORA, sala.abre());
        assertEquals(LocalDateTime.of(2026, 11, 20, 14, 30), sala.cierra());
        assertTrue(sala.abiertaEn(AHORA));
        assertTrue(sala.abiertaEn(LocalDateTime.of(2026, 11, 20, 14, 29)));
        assertFalse(sala.abiertaEn(LocalDateTime.of(2026, 11, 20, 14, 30)));
        assertFalse(sala.abiertaEn(AHORA.minusMinutes(1)));
    }

    @Test
    void antesDeAbrirDiceCuandoSeAbre() {
        sesion(AHORA.plusHours(5), 60, Sesion.EstadoSesion.CONFIRMADA);
        Sala sala = servicio.autorizar(USUARIO_ESTUDIANTE, Tipo.SESION, 7);

        assertFalse(sala.abiertaEn(AHORA));
        assertEquals("La sala se abre el 20/11/2026 a las 16:30.", sala.motivoCerrada(AHORA));
    }

    @Test
    void despuesDeCerrarDiceQueYaCerro() {
        sesion(AHORA.minusHours(5), 60, Sesion.EstadoSesion.CONFIRMADA);
        Sala sala = servicio.autorizar(USUARIO_ESTUDIANTE, Tipo.SESION, 7);

        assertEquals("La sala ya cerró.", sala.motivoCerrada(AHORA));
    }

    @Test
    void conLaSalaAbiertaNoHayMotivoDeCierre() {
        sesion(AHORA.minusMinutes(10), 60, Sesion.EstadoSesion.CONFIRMADA);

        assertNull(servicio.autorizar(USUARIO_ESTUDIANTE, Tipo.SESION, 7).motivoCerrada(AHORA));
    }

    // ---- Charlas ----

    @Test
    void unEstudianteInscritoEntraALaSalaDeLaCharla() {
        charla(AHORA.plusMinutes(10), 90, true);
        when(inscripcionRepository.existsByIdCharlaAndIdUsuario(3, USUARIO_ESTUDIANTE)).thenReturn(true);

        Sala sala = servicio.autorizar(USUARIO_ESTUDIANTE, Tipo.CHARLA, 3);

        assertEquals("CHARLA-3", sala.clave());
        assertEquals("Bases de datos en la industria", sala.titulo());
        assertEquals(8, sala.maxParticipantes());
    }

    @Test
    void sinInscripcionNoSeEntraALaCharla() {
        charla(AHORA.plusMinutes(10), 90, true);
        when(inscripcionRepository.existsByIdCharlaAndIdUsuario(3, USUARIO_ESTUDIANTE)).thenReturn(false);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> servicio.autorizar(USUARIO_ESTUDIANTE, Tipo.CHARLA, 3));

        assertEquals(HttpStatus.FORBIDDEN, error.getEstado());
    }

    @Test
    void unaCharlaDesactivadaNoTieneSala() {
        charla(AHORA.plusMinutes(10), 90, false);
        when(inscripcionRepository.existsByIdCharlaAndIdUsuario(3, USUARIO_ESTUDIANTE)).thenReturn(true);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> servicio.autorizar(USUARIO_ESTUDIANTE, Tipo.CHARLA, 3));

        assertEquals(HttpStatus.CONFLICT, error.getEstado());
    }

    @Test
    void unaCharlaInexistenteEsNoEncontrada() {
        when(charlaRepository.findById(3)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ReglaNegocioException.class,
                () -> servicio.autorizar(USUARIO_ESTUDIANTE, Tipo.CHARLA, 3)).getEstado());
    }

    // ---- Tipo ----

    @Test
    void elTipoSeAceptaEnMinusculas() {
        assertEquals(Tipo.SESION, Tipo.de("sesion"));
        assertEquals(Tipo.CHARLA, Tipo.de("Charla"));
    }

    @Test
    void unTipoDesconocidoEsNoEncontrado() {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ReglaNegocioException.class, () -> Tipo.de("reunion")).getEstado());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ReglaNegocioException.class, () -> Tipo.de(null)).getEstado());
    }
}
