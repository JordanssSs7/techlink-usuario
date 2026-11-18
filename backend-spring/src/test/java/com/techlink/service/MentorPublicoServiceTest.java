package com.techlink.service;

import com.techlink.dto.MentorPerfilPublico;
import com.techlink.dto.MentorRankingItem;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.Calificacion;
import com.techlink.model.Curso;
import com.techlink.model.EstadoUsuario;
import com.techlink.model.Insignia;
import com.techlink.model.Mentor;
import com.techlink.model.Sesion;
import com.techlink.repository.CalificacionRepository;
import com.techlink.repository.MentorRepository;
import com.techlink.repository.UsuarioFotoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

// Pruebas de HU-14 (perfil público) y HU-15 (ranking con insignias).
class MentorPublicoServiceTest {

    private MentorRepository mentorRepository;
    private CalificacionRepository calificacionRepository;
    private UsuarioFotoRepository fotoRepository;
    private InsigniaService insigniaService;
    private MentorPublicoService servicio;

    @BeforeEach
    void preparar() {
        mentorRepository = Mockito.mock(MentorRepository.class);
        calificacionRepository = Mockito.mock(CalificacionRepository.class);
        fotoRepository = Mockito.mock(UsuarioFotoRepository.class);
        insigniaService = Mockito.mock(InsigniaService.class);
        servicio = new MentorPublicoService(mentorRepository, calificacionRepository, fotoRepository, insigniaService);
        when(insigniaService.actividad(anyInt())).thenReturn(new InsigniaService.Actividad(0, 0));
    }

    private Mentor mentor(int id, String nombre, Double promedio, int total) {
        Mentor m = new Mentor();
        m.setId(id);
        m.setUsuario(PruebaSesiones.usuario(100 + id, nombre, "Apellido", nombre.toLowerCase() + "@tecsup.edu.pe"));
        m.setEspecialidad("Especialidad " + nombre);
        m.setReputacion(promedio == null ? BigDecimal.ZERO : BigDecimal.valueOf(promedio));
        m.setTotalCalificaciones(total);
        return m;
    }

    @Test
    void elRankingVaDelMejorPromedioAlMenor() {
        Mentor ana = mentor(1, "Ana", 4.2, 5);
        Mentor beto = mentor(2, "Beto", 4.9, 4);
        Mentor carla = mentor(3, "Carla", 3.0, 10);
        when(mentorRepository.findAll()).thenReturn(List.of(ana, beto, carla));

        List<MentorRankingItem> r = servicio.ranking();

        assertEquals(List.of("Beto", "Ana", "Carla"), r.stream().map(MentorRankingItem::nombres).toList());
        assertEquals(List.of(1, 2, 3), r.stream().map(MentorRankingItem::posicion).toList());
    }

    @Test
    void aIgualdadDePromedioGanaQuienTieneMasCalificacionesYLuegoMasHoras() {
        Mentor a = mentor(1, "Ana", 4.5, 3);
        Mentor b = mentor(2, "Beto", 4.5, 8);
        Mentor c = mentor(3, "Carla", 4.5, 8);
        when(mentorRepository.findAll()).thenReturn(List.of(a, b, c));
        when(insigniaService.actividad(3)).thenReturn(new InsigniaService.Actividad(12, 12));

        assertEquals(List.of("Carla", "Beto", "Ana"), servicio.ranking().stream().map(MentorRankingItem::nombres).toList());
    }

    @Test
    void losMentoresSinCalificacionesVanAlFinalYSuPromedioEsNulo() {
        Mentor nuevo = mentor(1, "Ana", null, 0);
        Mentor conNota = mentor(2, "Beto", 2.0, 1);
        when(mentorRepository.findAll()).thenReturn(List.of(nuevo, conNota));

        List<MentorRankingItem> r = servicio.ranking();

        assertEquals("Beto", r.get(0).nombres());
        assertEquals("Ana", r.get(1).nombres());
        assertNull(r.get(1).promedio());
    }

    @Test
    void elRankingNoIncluyeMentoresInactivos() {
        Mentor activo = mentor(1, "Ana", 4.0, 1);
        Mentor inactivo = mentor(2, "Beto", 5.0, 9);
        inactivo.getUsuario().setEstado(EstadoUsuario.INACTIVO);
        when(mentorRepository.findAll()).thenReturn(List.of(activo, inactivo));

        assertEquals(1, servicio.ranking().size());
    }

    @Test
    void elRankingMuestraLasInsigniasLosCursosYLaFoto() {
        Mentor m = mentor(1, "Ana", 4.8, 6);
        Insignia estrella = new Insignia();
        estrella.setId(4);
        estrella.setCodigo("MENTOR_ESTRELLA");
        estrella.setNombre("Mentor estrella");
        estrella.setDescripcion("d");
        m.getInsignias().add(estrella);
        Curso bd = new Curso();
        bd.setNombre("Base de Datos");
        m.getCursos().add(bd);
        when(mentorRepository.findAll()).thenReturn(List.of(m));
        when(fotoRepository.existsById(101)).thenReturn(true);

        MentorRankingItem item = servicio.ranking().get(0);

        assertEquals("MENTOR_ESTRELLA", item.insignias().get(0).codigo());
        assertEquals(List.of("Base de Datos"), item.cursos());
        assertTrue(item.tieneFoto());
    }

    // ---------- perfil público ----------

    private void resumen(Mentor m, Double promedio, long total) {
        CalificacionRepository.Resumen r = Mockito.mock(CalificacionRepository.Resumen.class);
        when(r.getPromedio()).thenReturn(promedio);
        when(r.getTotal()).thenReturn(total);
        when(calificacionRepository.resumenDeMentor(m.getId())).thenReturn(r);
    }

    private Calificacion calificacion(int estrellas, String comentario, String estudiante) {
        Sesion s = PruebaSesiones.sesion(1, LocalDateTime.now().minusDays(1), 60, Sesion.EstadoSesion.CONFIRMADA);
        s.getEstudiante().getUsuario().setNombres(estudiante);
        Calificacion c = new Calificacion();
        c.setSesion(s);
        c.setEstrellas(estrellas);
        c.setComentario(comentario);
        return c;
    }

    @Test
    void elPerfilPublicoMuestraElPromedioCalculadoConLasCalificaciones() {
        Mentor m = mentor(1, "Ana", 1.0, 99); // la copia guardada no manda: se usan las calificaciones
        when(mentorRepository.findById(1)).thenReturn(Optional.of(m));
        resumen(m, 4.3333333, 3);
        when(calificacionRepository.ultimosComentarios(any(Integer.class), any(Pageable.class))).thenReturn(List.of());

        MentorPerfilPublico p = servicio.perfil(1);

        assertEquals(new BigDecimal("4.33"), p.promedio());
        assertEquals(3, p.totalCalificaciones());
    }

    @Test
    void sinCalificacionesElPromedioEsNuloYNoHayComentarios() {
        Mentor m = mentor(1, "Ana", null, 0);
        when(mentorRepository.findById(1)).thenReturn(Optional.of(m));
        resumen(m, null, 0);
        when(calificacionRepository.ultimosComentarios(any(Integer.class), any(Pageable.class))).thenReturn(List.of());

        MentorPerfilPublico p = servicio.perfil(1);

        assertNull(p.promedio());
        assertTrue(p.comentarios().isEmpty());
    }

    @Test
    void losComentariosMuestranSoloElPrimerNombreDelEstudiante() {
        Mentor m = mentor(1, "Ana", 5.0, 1);
        when(mentorRepository.findById(1)).thenReturn(Optional.of(m));
        resumen(m, 5.0, 1);
        when(calificacionRepository.ultimosComentarios(any(Integer.class), any(Pageable.class)))
                .thenReturn(List.of(calificacion(5, "Excelente", "María José")));

        MentorPerfilPublico.Comentario c = servicio.perfil(1).comentarios().get(0);

        assertEquals("María", c.estudiante());
        assertEquals("Excelente", c.comentario());
        assertEquals(5, c.estrellas());
    }

    @Test
    void elPerfilIncluyeCursosFortalezasHorasEInsignias() {
        Mentor m = mentor(1, "Ana", 4.8, 3);
        m.getFortalezas().add("Java");
        m.getAreas().add("Backend");
        Curso bd = new Curso();
        bd.setNombre("Base de Datos");
        m.getCursos().add(bd);
        m.getUsuario().setDescripcion("Me gusta enseñar");
        when(mentorRepository.findById(1)).thenReturn(Optional.of(m));
        resumen(m, 4.8, 3);
        when(calificacionRepository.ultimosComentarios(any(Integer.class), any(Pageable.class))).thenReturn(List.of());
        when(insigniaService.actividad(1)).thenReturn(new InsigniaService.Actividad(12.5, 11));

        MentorPerfilPublico p = servicio.perfil(1);

        assertEquals(List.of("Base de Datos"), p.cursos());
        assertEquals(List.of("Java"), p.fortalezas());
        assertEquals(List.of("Backend"), p.areas());
        assertEquals("Me gusta enseñar", p.descripcion());
        assertEquals(12.5, p.horasDictadas());
        assertEquals(11, p.sesionesCompletadas());
    }

    @Test
    void unMentorQueNoExisteOEstaInactivoDaNotFound() {
        when(mentorRepository.findById(9)).thenReturn(Optional.empty());
        Mentor inactivo = mentor(2, "Beto", 4.0, 1);
        inactivo.getUsuario().setEstado(EstadoUsuario.INACTIVO);
        when(mentorRepository.findById(2)).thenReturn(Optional.of(inactivo));

        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ReglaNegocioException.class, () -> servicio.perfil(9)).getEstado());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ReglaNegocioException.class, () -> servicio.perfil(2)).getEstado());
    }
}
