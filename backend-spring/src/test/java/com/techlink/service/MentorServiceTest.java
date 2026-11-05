package com.techlink.service;

import com.techlink.dto.PerfilMentorRequest;
import com.techlink.dto.PerfilMentorRequest.Horario;
import com.techlink.dto.PerfilMentorResponse;
import com.techlink.exception.PerfilMentorInvalidoException;
import com.techlink.exception.UsuarioNoRegistradoException;
import com.techlink.model.Curso;
import com.techlink.model.Mentor;
import com.techlink.model.Usuario;
import com.techlink.repository.CursoRepository;
import com.techlink.repository.MentorDisponibilidadRepository;
import com.techlink.repository.MentorRepository;
import com.techlink.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// Pruebas unitarias del perfil del mentor (HU-05): no necesitan base de datos, los repositorios son simulados.
class MentorServiceTest {

    private static final Integer ID_USUARIO = 7;

    private MentorRepository mentorRepository;
    private CursoRepository cursoRepository;
    private MentorDisponibilidadRepository disponibilidadRepository;
    private MentorService service;
    private Curso java;
    private Curso bd;

    @BeforeEach
    void preparar() {
        UsuarioRepository usuarioRepository = Mockito.mock(UsuarioRepository.class);
        mentorRepository = Mockito.mock(MentorRepository.class);
        cursoRepository = Mockito.mock(CursoRepository.class);
        disponibilidadRepository = Mockito.mock(MentorDisponibilidadRepository.class);
        service = new MentorService(usuarioRepository, mentorRepository, cursoRepository, disponibilidadRepository);

        Usuario usuario = new Usuario();
        usuario.setId(ID_USUARIO);
        when(usuarioRepository.findById(ID_USUARIO)).thenReturn(Optional.of(usuario));
        when(mentorRepository.findByUsuarioId(ID_USUARIO)).thenReturn(Optional.empty());
        when(mentorRepository.saveAndFlush(any(Mentor.class))).thenAnswer(i -> i.getArgument(0));

        java = curso(1, "Programación Orientada a Objetos", true);
        bd = curso(2, "Base de Datos", true);
        when(cursoRepository.findAllById(any())).thenAnswer(i -> {
            Iterable<Integer> ids = i.getArgument(0);
            return List.of(java, bd, curso(3, "Curso retirado", false)).stream()
                    .filter(c -> {
                        for (Integer id : ids) if (id.equals(c.getId())) return true;
                        return false;
                    }).toList();
        });
    }

    private static Curso curso(int id, String nombre, boolean activo) {
        Curso c = new Curso();
        c.setId(id);
        c.setNombre(nombre);
        c.setActivo(activo);
        return c;
    }

    private static Horario horario(int dia, String inicio, String fin, Boolean activo) {
        return new Horario(dia, LocalTime.parse(inicio), LocalTime.parse(fin), activo);
    }

    private static PerfilMentorRequest request(List<Integer> cursos, List<Horario> horarios) {
        return new PerfilMentorRequest(null, null, List.of("Java"), List.of("Backend"), cursos, horarios);
    }

    @Test
    void sinPerfilGuardadoDevuelveUnPerfilVacio() {
        PerfilMentorResponse perfil = service.obtenerPerfil(ID_USUARIO);

        assertTrue(perfil.fortalezas().isEmpty());
        assertTrue(perfil.cursos().isEmpty());
        assertTrue(perfil.disponibilidad().isEmpty());
        Mockito.verify(mentorRepository, Mockito.never()).saveAndFlush(any());
    }

    @Test
    void guardaFortalezasCursosAreasYDisponibilidad() {
        PerfilMentorResponse perfil = service.actualizarPerfil(ID_USUARIO, request(
                List.of(1, 2), List.of(horario(1, "09:00", "11:00", true), horario(3, "15:00", "17:00", false))));

        assertEquals(List.of("Java"), perfil.fortalezas());
        assertEquals(List.of("Backend"), perfil.areas());
        assertEquals(2, perfil.cursos().size());
        assertEquals(2, perfil.disponibilidad().size());
        assertTrue(perfil.disponibilidad().get(0).activo());
        assertFalse(perfil.disponibilidad().get(1).activo());
    }

    @Test
    void sinCampoActivoElHorarioQuedaActivo() {
        PerfilMentorResponse perfil = service.actualizarPerfil(ID_USUARIO,
                request(List.of(), List.of(horario(2, "08:00", "10:00", null))));

        assertTrue(perfil.disponibilidad().get(0).activo());
    }

    @Test
    void quitaEspaciosYRepetidosEnFortalezasYAreas() {
        PerfilMentorRequest req = new PerfilMentorRequest(null, null,
                List.of(" Java ", "java", "React"), List.of("Backend", "BACKEND"), List.of(), List.of());

        PerfilMentorResponse perfil = service.actualizarPerfil(ID_USUARIO, req);

        assertEquals(List.of("Java", "React"), perfil.fortalezas());
        assertEquals(List.of("Backend"), perfil.areas());
    }

    @Test
    void conservaLaEspecialidadSiNoSeEnvia() {
        Mentor existente = new Mentor();
        existente.setEspecialidad("Redes");
        when(mentorRepository.findByUsuarioId(ID_USUARIO)).thenReturn(Optional.of(existente));

        PerfilMentorResponse perfil = service.actualizarPerfil(ID_USUARIO, request(List.of(), List.of()));

        assertEquals("Redes", perfil.especialidad());
    }

    @Test
    void rechazaUnCursoQueNoExisteOEstaInactivo() {
        PerfilMentorInvalidoException ex = assertThrows(PerfilMentorInvalidoException.class,
                () -> service.actualizarPerfil(ID_USUARIO, request(List.of(1, 99), List.of())));
        assertTrue(ex.getCampos().containsKey("cursoIds"));

        assertThrows(PerfilMentorInvalidoException.class,
                () -> service.actualizarPerfil(ID_USUARIO, request(List.of(3), List.of())));
    }

    @Test
    void rechazaUnHorarioCuyoFinNoEsPosteriorAlInicio() {
        PerfilMentorInvalidoException ex = assertThrows(PerfilMentorInvalidoException.class,
                () -> service.actualizarPerfil(ID_USUARIO,
                        request(List.of(), List.of(horario(1, "11:00", "09:00", true)))));

        assertTrue(ex.getCampos().containsKey("disponibilidad[0].horaFin"));
    }

    @Test
    void rechazaHorariosActivosQueSeCruzanElMismoDia() {
        PerfilMentorInvalidoException ex = assertThrows(PerfilMentorInvalidoException.class,
                () -> service.actualizarPerfil(ID_USUARIO, request(List.of(),
                        List.of(horario(1, "09:00", "11:00", true), horario(1, "10:00", "12:00", true)))));

        assertTrue(ex.getCampos().containsKey("disponibilidad"));
    }

    @Test
    void permiteHorariosContiguosYCruzadosSiUnoEstaInactivo() {
        assertDoesNotThrow(() -> service.actualizarPerfil(ID_USUARIO, request(List.of(), List.of(
                horario(1, "09:00", "11:00", true),
                horario(1, "11:00", "12:00", true),
                horario(1, "10:00", "11:30", false),
                horario(2, "09:00", "11:00", true)))));
    }

    @Test
    void usuarioInexistenteDaNoEncontrado() {
        assertThrows(UsuarioNoRegistradoException.class, () -> service.obtenerPerfil(999));
        assertThrows(UsuarioNoRegistradoException.class,
                () -> service.actualizarPerfil(999, request(List.of(), List.of())));
    }

    @Test
    void horariosActivosDeUnMentorQueNoExisteDaNoEncontrado() {
        when(mentorRepository.existsById(50)).thenReturn(false);

        assertThrows(UsuarioNoRegistradoException.class, () -> service.horariosActivos(50));
    }

    @Test
    void horariosActivosSoloDevuelveLosDelRepositorioActivo() {
        when(mentorRepository.existsById(5)).thenReturn(true);
        when(disponibilidadRepository.findByMentorIdAndActivoTrueOrderByDiaSemanaAscHoraInicioAsc(5))
                .thenReturn(List.of());

        assertTrue(service.horariosActivos(5).isEmpty());
        Mockito.verify(disponibilidadRepository).findByMentorIdAndActivoTrueOrderByDiaSemanaAscHoraInicioAsc(5);
    }
}
