package com.techlink.service;

import com.techlink.dto.MetaActualizadaResponse;
import com.techlink.dto.MetaRequest;
import com.techlink.dto.MetaResponse;
import com.techlink.dto.MetasMentorResponse;
import com.techlink.dto.MetasResponse;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.Curso;
import com.techlink.model.Estudiante;
import com.techlink.model.Mentor;
import com.techlink.model.Meta;
import com.techlink.repository.CursoRepository;
import com.techlink.repository.EstudianteRepository;
import com.techlink.repository.MentorRepository;
import com.techlink.repository.MetaRepository;
import com.techlink.repository.SesionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static com.techlink.service.PruebaSesiones.USUARIO_ESTUDIANTE;
import static com.techlink.service.PruebaSesiones.USUARIO_MENTOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pruebas de HU-11: metas académicas semanales.
class MetaServiceTest {

    // "Hoy": viernes 20/11/2026 a las 12:00. Su semana va del lunes 16 al domingo 22.
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 20, 12, 0);
    private static final LocalDate HOY = AHORA.toLocalDate();
    private static final LocalDate LUNES = LocalDate.of(2026, 11, 16);

    private MetaRepository metaRepository;
    private EstudianteRepository estudianteRepository;
    private MentorRepository mentorRepository;
    private CursoRepository cursoRepository;
    private SesionRepository sesionRepository;
    private MetaService servicio;
    private Estudiante estudiante;
    private Mentor mentor;

    @BeforeEach
    void preparar() {
        metaRepository = Mockito.mock(MetaRepository.class);
        estudianteRepository = Mockito.mock(EstudianteRepository.class);
        mentorRepository = Mockito.mock(MentorRepository.class);
        cursoRepository = Mockito.mock(CursoRepository.class);
        sesionRepository = Mockito.mock(SesionRepository.class);
        ZoneId zona = ZoneId.systemDefault();
        servicio = new MetaService(metaRepository, estudianteRepository, mentorRepository, cursoRepository, sesionRepository,
                Clock.fixed(AHORA.atZone(zona).toInstant(), zona));

        estudiante = new Estudiante();
        estudiante.setId(1);
        estudiante.setUsuario(PruebaSesiones.usuario(USUARIO_ESTUDIANTE, "Ana", "Quispe", "ana@tecsup.edu.pe"));
        when(estudianteRepository.findByUsuarioId(USUARIO_ESTUDIANTE)).thenReturn(Optional.of(estudiante));
        mentor = PruebaSesiones.mentor();
        when(mentorRepository.findByUsuarioId(USUARIO_MENTOR)).thenReturn(Optional.of(mentor));
        when(sesionRepository.estudiantesDeMentor(USUARIO_MENTOR)).thenReturn(List.of(estudiante));
        when(metaRepository.contarPendientes(USUARIO_ESTUDIANTE)).thenReturn(0L);
    }

    private Meta meta(String titulo, LocalDate entrega, boolean completada, Mentor deMentor) {
        Meta m = new Meta();
        m.setEstudiante(estudiante);
        m.setMentor(deMentor);
        m.setTitulo(titulo);
        m.setFechaEntrega(entrega);
        m.setCompletada(completada);
        return m;
    }

    private static MetaRequest pedido(String titulo, String entrega) {
        return new MetaRequest(titulo, null, entrega, null);
    }

    private static ReglaNegocioException error(Runnable accion) {
        return assertThrows(ReglaNegocioException.class, accion::run);
    }

    // ---------------------------------------------------------------- listar y progreso

    @Test
    void listaLasMetasDeLaSemanaConSuProgreso() {
        List<Meta> semana = List.of(meta("Resolver la guía", LUNES.plusDays(1), true, null),
                meta("Repasar JOINs", LUNES.plusDays(3), false, mentor), meta("Entregar el lab", LUNES.plusDays(4), true, null));
        when(metaRepository.delEstudianteEntre(USUARIO_ESTUDIANTE, LUNES, LUNES.plusDays(6))).thenReturn(semana);

        MetasResponse r = servicio.listar(USUARIO_ESTUDIANTE, null);

        assertEquals(LUNES, r.semana().desde());
        assertEquals(LUNES.plusDays(6), r.semana().hasta());
        assertEquals(3, r.semana().total());
        assertEquals(2, r.semana().completadas());
        assertEquals(67, r.semana().porcentaje());
        assertEquals(List.of("Resolver la guía", "Repasar JOINs", "Entregar el lab"), r.metas().stream().map(MetaResponse::titulo).toList());
    }

    @Test
    void cualquierDiaDeLaSemanaPideLaMismaSemana() {
        when(metaRepository.delEstudianteEntre(USUARIO_ESTUDIANTE, LUNES, LUNES.plusDays(6))).thenReturn(List.of());

        MetasResponse r = servicio.listar(USUARIO_ESTUDIANTE, LocalDate.of(2026, 11, 22));

        assertEquals(LUNES, r.semana().desde());
        assertEquals(0, r.semana().porcentaje());
    }

    @Test
    void muestraComoAtrasadasLasPendientesDeSemanasAnteriores() {
        Meta vieja = meta("Pendiente de antes", LUNES.minusDays(3), false, null);
        when(metaRepository.atrasadasDe(USUARIO_ESTUDIANTE, LUNES)).thenReturn(List.of(vieja));

        MetasResponse r = servicio.listar(USUARIO_ESTUDIANTE, null);

        assertEquals(1, r.atrasadas().size());
        assertTrue(r.atrasadas().get(0).atrasada());
    }

    @Test
    void muestraLasPendientesDeLasProximasSemanasParaQueNoPasenDesapercibidas() {
        Meta delMentor = meta("Para la otra semana", LUNES.plusDays(10), false, mentor);
        when(metaRepository.proximasDe(any(), any(), any())).thenReturn(List.of(delMentor));

        MetasResponse r = servicio.listar(USUARIO_ESTUDIANTE, null);

        assertEquals(1, r.proximas().size());
        assertEquals("Para la otra semana", r.proximas().get(0).titulo());
        assertFalse(r.proximas().get(0).atrasada());
        // Se piden las posteriores al domingo de la semana que se mira.
        verify(metaRepository).proximasDe(any(), org.mockito.ArgumentMatchers.eq(LUNES.plusDays(6)), any());
    }

    @Test
    void marcaPropiaYDelMentorSeDistinguen() {
        Meta propia = meta("Mía", HOY, false, null);
        Meta delMentor = meta("De mi mentor", HOY, false, mentor);
        when(metaRepository.delEstudianteEntre(USUARIO_ESTUDIANTE, LUNES, LUNES.plusDays(6))).thenReturn(List.of(propia, delMentor));

        List<MetaResponse> metas = servicio.listar(USUARIO_ESTUDIANTE, null).metas();

        assertTrue(metas.get(0).propia());
        assertNull(metas.get(0).mentor());
        assertFalse(metas.get(1).propia());
        assertEquals("Lucía Ramírez", metas.get(1).mentor());
    }

    // ---------------------------------------------------------------- marcar

    @Test
    void marcarUnaMetaLaCompletaYRecalculaElProgreso() {
        Meta m = meta("Repasar JOINs", LUNES.plusDays(3), false, mentor);
        Meta otra = meta("Otra", LUNES.plusDays(4), false, null);
        when(metaRepository.findById(7)).thenReturn(Optional.of(m));
        when(metaRepository.delEstudianteEntre(USUARIO_ESTUDIANTE, LUNES, LUNES.plusDays(6))).thenReturn(List.of(m, otra));

        MetaActualizadaResponse r = servicio.marcar(USUARIO_ESTUDIANTE, 7, true);

        assertTrue(m.isCompletada());
        assertEquals(AHORA, m.getFechaCompletada());
        assertTrue(r.meta().completada());
        assertEquals(1, r.progreso().completadas());
        assertEquals(2, r.progreso().total());
        assertEquals(50, r.progreso().porcentaje());
        verify(metaRepository).save(m);
    }

    @Test
    void desmarcarUnaMetaLimpiaLaFechaDeCumplimiento() {
        Meta m = meta("Repasar", HOY, true, null);
        m.setFechaCompletada(AHORA.minusDays(1));
        when(metaRepository.findById(7)).thenReturn(Optional.of(m));
        when(metaRepository.delEstudianteEntre(any(), any(), any())).thenReturn(List.of(m));

        MetaActualizadaResponse r = servicio.marcar(USUARIO_ESTUDIANTE, 7, false);

        assertFalse(m.isCompletada());
        assertNull(m.getFechaCompletada());
        assertEquals(0, r.progreso().porcentaje());
    }

    @Test
    void marcarDosVecesSeguidasNoVuelveAGuardar() {
        Meta m = meta("Ya hecha", HOY, true, null);
        when(metaRepository.findById(7)).thenReturn(Optional.of(m));
        when(metaRepository.delEstudianteEntre(any(), any(), any())).thenReturn(List.of(m));

        servicio.marcar(USUARIO_ESTUDIANTE, 7, true);

        verify(metaRepository, never()).save(any());
    }

    @Test
    void unaMetaAjenaOInexistenteNoSePuedeMarcar() {
        Estudiante otro = new Estudiante();
        otro.setUsuario(PruebaSesiones.usuario(99, "Otro", "Alumno", "o@tecsup.edu.pe"));
        Meta ajena = meta("De otro", HOY, false, null);
        ajena.setEstudiante(otro);
        when(metaRepository.findById(7)).thenReturn(Optional.of(ajena));
        when(metaRepository.findById(8)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, error(() -> servicio.marcar(USUARIO_ESTUDIANTE, 7, true)).getEstado());
        assertEquals(HttpStatus.NOT_FOUND, error(() -> servicio.marcar(USUARIO_ESTUDIANTE, 8, true)).getEstado());
        assertEquals(HttpStatus.BAD_REQUEST, error(() -> servicio.marcar(USUARIO_ESTUDIANTE, 7, null)).getEstado());
    }

    // ---------------------------------------------------------------- crear

    @Test
    void elEstudianteCreaUnaMetaParaSi() {
        MetaResponse r = servicio.crear(USUARIO_ESTUDIANTE, "ESTUDIANTE", pedido("  Resolver   la guía 3 ", HOY.plusDays(2).toString()));

        assertEquals("Resolver la guía 3", r.titulo());
        assertTrue(r.propia());
        assertFalse(r.completada());
        assertFalse(r.atrasada());
        verify(metaRepository).save(any(Meta.class));
    }

    @Test
    void laMetaPuedeTenerUnCursoDelCatalogo() {
        Curso bd = new Curso();
        bd.setId(4);
        bd.setNombre("Base de Datos");
        bd.setActivo(true);
        when(cursoRepository.findById(4)).thenReturn(Optional.of(bd));

        MetaResponse r = servicio.crear(USUARIO_ESTUDIANTE, "ESTUDIANTE", new MetaRequest("Repasar JOINs", 4, HOY.toString(), null));

        assertEquals("Base de Datos", r.curso());
        when(cursoRepository.findById(9)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.BAD_REQUEST,
                error(() -> servicio.crear(USUARIO_ESTUDIANTE, "ESTUDIANTE", new MetaRequest("Repasar", 9, HOY.toString(), null))).getEstado());
    }

    @Test
    void rechazaTituloCortoLargoFechasInvalidasYPasadas() {
        assertEquals(HttpStatus.BAD_REQUEST, error(() -> servicio.crear(USUARIO_ESTUDIANTE, "ESTUDIANTE", pedido("ab", HOY.toString()))).getEstado());
        assertEquals(HttpStatus.BAD_REQUEST, error(() -> servicio.crear(USUARIO_ESTUDIANTE, "ESTUDIANTE", pedido(null, HOY.toString()))).getEstado());
        assertEquals(HttpStatus.BAD_REQUEST, error(() -> servicio.crear(USUARIO_ESTUDIANTE, "ESTUDIANTE", pedido("x".repeat(151), HOY.toString()))).getEstado());
        assertEquals(HttpStatus.BAD_REQUEST, error(() -> servicio.crear(USUARIO_ESTUDIANTE, "ESTUDIANTE", pedido("Meta válida", null))).getEstado());
        assertEquals(HttpStatus.BAD_REQUEST, error(() -> servicio.crear(USUARIO_ESTUDIANTE, "ESTUDIANTE", pedido("Meta válida", "mañana"))).getEstado());
        assertEquals(HttpStatus.BAD_REQUEST, error(() -> servicio.crear(USUARIO_ESTUDIANTE, "ESTUDIANTE", pedido("Meta válida", HOY.minusDays(1).toString()))).getEstado());
        assertEquals(HttpStatus.BAD_REQUEST,
                error(() -> servicio.crear(USUARIO_ESTUDIANTE, "ESTUDIANTE", pedido("Meta válida", HOY.plusDays(MetaService.MAX_DIAS_ADELANTE + 1).toString()))).getEstado());
        verify(metaRepository, never()).save(any());
    }

    @Test
    void sePuedeCrearUnaMetaParaHoyYParaElUltimoDiaPermitido() {
        servicio.crear(USUARIO_ESTUDIANTE, "ESTUDIANTE", pedido("Para hoy", HOY.toString()));
        servicio.crear(USUARIO_ESTUDIANTE, "ESTUDIANTE", pedido("Para más adelante", HOY.plusDays(MetaService.MAX_DIAS_ADELANTE).toString()));
        verify(metaRepository, Mockito.times(2)).save(any(Meta.class));
    }

    @Test
    void conDemasiadasPendientesNoDejaCrearOtra() {
        when(metaRepository.contarPendientes(USUARIO_ESTUDIANTE)).thenReturn((long) MetaService.MAX_PENDIENTES);

        ReglaNegocioException e = error(() -> servicio.crear(USUARIO_ESTUDIANTE, "ESTUDIANTE", pedido("Una más", HOY.toString())));

        assertEquals(HttpStatus.CONFLICT, e.getEstado());
    }

    @Test
    void elMentorLeEncargaUnaMetaASuEstudiante() {
        MetaResponse r = servicio.crear(USUARIO_MENTOR, "MENTOR",
                new MetaRequest("Practicar subconsultas", null, HOY.plusDays(5).toString(), USUARIO_ESTUDIANTE));

        assertFalse(r.propia());
        assertEquals("Ana Quispe", r.estudiante());
        assertEquals(USUARIO_ESTUDIANTE, r.idUsuarioEstudiante());
        assertEquals("Lucía Ramírez", r.mentor());
    }

    @Test
    void elMentorSoloPuedePonerleMetasASusEstudiantes() {
        assertEquals(HttpStatus.FORBIDDEN, error(() -> servicio.crear(USUARIO_MENTOR, "MENTOR",
                new MetaRequest("Meta", null, HOY.toString(), 99))).getEstado());
        assertEquals(HttpStatus.BAD_REQUEST, error(() -> servicio.crear(USUARIO_MENTOR, "MENTOR",
                new MetaRequest("Meta", null, HOY.toString(), null))).getEstado());
        verify(metaRepository, never()).save(any());
    }

    @Test
    void unMentorSinPerfilNiUnAdministradorPuedenCrearMetas() {
        when(mentorRepository.findByUsuarioId(USUARIO_MENTOR)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.FORBIDDEN, error(() -> servicio.crear(USUARIO_MENTOR, "MENTOR",
                new MetaRequest("Meta", null, HOY.toString(), USUARIO_ESTUDIANTE))).getEstado());
        assertEquals(HttpStatus.FORBIDDEN, error(() -> servicio.crear(1, "ADMIN", pedido("Meta", HOY.toString()))).getEstado());
    }

    // ---------------------------------------------------------------- eliminar

    @Test
    void elEstudianteBorraSusMetasPropiasPeroNoLasDeSuMentor() {
        Meta propia = meta("Mía", HOY, false, null);
        Meta delMentor = meta("De mi mentor", HOY, false, mentor);
        when(metaRepository.findById(1)).thenReturn(Optional.of(propia));
        when(metaRepository.findById(2)).thenReturn(Optional.of(delMentor));

        servicio.eliminar(USUARIO_ESTUDIANTE, "ESTUDIANTE", 1);
        verify(metaRepository).delete(propia);

        assertEquals(HttpStatus.FORBIDDEN, error(() -> servicio.eliminar(USUARIO_ESTUDIANTE, "ESTUDIANTE", 2)).getEstado());
        verify(metaRepository, never()).delete(delMentor);
    }

    @Test
    void elMentorBorraLasMetasQuePuso() {
        Meta delMentor = meta("De mi mentor", HOY, false, mentor);
        when(metaRepository.findById(2)).thenReturn(Optional.of(delMentor));

        servicio.eliminar(USUARIO_MENTOR, "MENTOR", 2);

        verify(metaRepository).delete(delMentor);
    }

    @Test
    void nadieBorraLaMetaDeOtraPersona() {
        Estudiante otro = new Estudiante();
        otro.setUsuario(PruebaSesiones.usuario(99, "Otro", "Alumno", "o@tecsup.edu.pe"));
        Meta ajena = meta("De otro", HOY, false, null);
        ajena.setEstudiante(otro);
        when(metaRepository.findById(3)).thenReturn(Optional.of(ajena));
        when(metaRepository.findById(4)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, error(() -> servicio.eliminar(USUARIO_ESTUDIANTE, "ESTUDIANTE", 3)).getEstado());
        assertEquals(HttpStatus.NOT_FOUND, error(() -> servicio.eliminar(USUARIO_MENTOR, "MENTOR", 3)).getEstado());
        assertEquals(HttpStatus.NOT_FOUND, error(() -> servicio.eliminar(USUARIO_ESTUDIANTE, "ESTUDIANTE", 4)).getEstado());
        verify(metaRepository, never()).delete(any());
    }

    // ---------------------------------------------------------------- mentor

    @Test
    void elMentorVeSusEstudiantesYLasMetasQuePuso() {
        when(metaRepository.asignadasPorMentor(USUARIO_MENTOR)).thenReturn(List.of(meta("Practicar", HOY.minusDays(2), false, mentor)));

        MetasMentorResponse r = servicio.delMentor(USUARIO_MENTOR);

        assertEquals(1, r.estudiantes().size());
        assertEquals("Ana Quispe", r.estudiantes().get(0).nombre());
        assertEquals(1, r.metas().size());
        assertTrue(r.metas().get(0).atrasada());
        assertNotNull(r.metas().get(0).estudiante());
    }

    @Test
    void lasSemanasEmpiezanElLunes() {
        assertEquals(LUNES, MetaService.lunesDe(LocalDate.of(2026, 11, 16)));
        assertEquals(LUNES, MetaService.lunesDe(LocalDate.of(2026, 11, 22)));
        assertEquals(LocalDate.of(2026, 11, 23), MetaService.lunesDe(LocalDate.of(2026, 11, 23)));
    }
}
