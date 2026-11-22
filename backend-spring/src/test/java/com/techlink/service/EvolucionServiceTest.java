package com.techlink.service;

import com.techlink.dto.AutoevaluacionRequest;
import com.techlink.dto.AutoevaluacionResponse;
import com.techlink.dto.EvolucionResponse;
import com.techlink.dto.PeriodoEvolucionResponse;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.Autoevaluacion;
import com.techlink.model.Curso;
import com.techlink.model.Estudiante;
import com.techlink.model.Meta;
import com.techlink.model.Sesion;
import com.techlink.repository.AutoevaluacionRepository;
import com.techlink.repository.CursoRepository;
import com.techlink.repository.EstudianteRepository;
import com.techlink.repository.MetaRepository;
import com.techlink.repository.SesionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.techlink.service.PruebaSesiones.USUARIO_ESTUDIANTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pruebas de HU-12: panel de evolución y autoevaluaciones.
class EvolucionServiceTest {

    // "Hoy": viernes 20/11/2026 a las 12:00. La semana actual va del lunes 16 al domingo 22.
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 20, 12, 0);
    private static final LocalDate HOY = AHORA.toLocalDate();

    private SesionRepository sesionRepository;
    private AutoevaluacionRepository autoevaluacionRepository;
    private MetaRepository metaRepository;
    private EstudianteRepository estudianteRepository;
    private CursoRepository cursoRepository;
    private EvolucionService servicio;
    private Estudiante estudiante;
    private final List<Autoevaluacion> autoevaluaciones = new ArrayList<>();
    private final List<Meta> metas = new ArrayList<>();
    private final List<Sesion> sesiones = new ArrayList<>();

    @BeforeEach
    void preparar() {
        sesionRepository = Mockito.mock(SesionRepository.class);
        autoevaluacionRepository = Mockito.mock(AutoevaluacionRepository.class);
        metaRepository = Mockito.mock(MetaRepository.class);
        estudianteRepository = Mockito.mock(EstudianteRepository.class);
        cursoRepository = Mockito.mock(CursoRepository.class);
        ZoneId zona = ZoneId.systemDefault();
        servicio = new EvolucionService(sesionRepository, autoevaluacionRepository, metaRepository, estudianteRepository,
                cursoRepository, Clock.fixed(AHORA.atZone(zona).toInstant(), zona));

        estudiante = new Estudiante();
        estudiante.setId(1);
        estudiante.setUsuario(PruebaSesiones.usuario(USUARIO_ESTUDIANTE, "Ana", "Quispe", "ana@tecsup.edu.pe"));
        when(estudianteRepository.findByUsuarioId(USUARIO_ESTUDIANTE)).thenReturn(Optional.of(estudiante));
        when(sesionRepository.historialDeEstudiante(USUARIO_ESTUDIANTE)).thenAnswer(i -> sesiones);
        when(autoevaluacionRepository.delEstudianteEntre(any(), any(), any())).thenAnswer(i -> {
            LocalDate desde = i.getArgument(1);
            LocalDate hasta = i.getArgument(2);
            return autoevaluaciones.stream().filter(a -> !a.getFecha().isBefore(desde) && !a.getFecha().isAfter(hasta)).toList();
        });
        when(metaRepository.delEstudianteEntre(any(), any(), any())).thenAnswer(i -> {
            LocalDate desde = i.getArgument(1);
            LocalDate hasta = i.getArgument(2);
            return metas.stream().filter(m -> !m.getFechaEntrega().isBefore(desde) && !m.getFechaEntrega().isAfter(hasta)).toList();
        });
        when(autoevaluacionRepository.contarDelDia(any(), any())).thenReturn(0L);
    }

    private void sesion(LocalDateTime inicio, int minutos, Sesion.EstadoSesion estado) {
        sesiones.add(PruebaSesiones.sesion(sesiones.size() + 1, inicio, minutos, estado));
    }

    private void auto(LocalDate fecha, int puntaje, String nota) {
        Autoevaluacion a = new Autoevaluacion();
        a.setEstudiante(estudiante);
        a.setFecha(fecha);
        a.setPuntaje(puntaje);
        a.setNota(nota == null ? null : new BigDecimal(nota));
        autoevaluaciones.add(a);
    }

    private void meta(LocalDate entrega, boolean completada) {
        Meta m = new Meta();
        m.setEstudiante(estudiante);
        m.setTitulo("Meta");
        m.setFechaEntrega(entrega);
        m.setCompletada(completada);
        metas.add(m);
    }

    private static ReglaNegocioException error(Runnable accion) {
        return assertThrows(ReglaNegocioException.class, accion::run);
    }

    // ---------------------------------------------------------------- evolución

    @Test
    void sumaLasHorasDeLasSesionesTerminadasPorSemana() {
        sesion(LocalDateTime.of(2026, 11, 18, 10, 0), 90, Sesion.EstadoSesion.CONFIRMADA);   // esta semana, terminada
        sesion(LocalDateTime.of(2026, 11, 19, 9, 0), 60, Sesion.EstadoSesion.CONFIRMADA);    // esta semana, terminada
        sesion(LocalDateTime.of(2026, 11, 11, 10, 0), 60, Sesion.EstadoSesion.CONFIRMADA);   // semana anterior
        sesion(LocalDateTime.of(2026, 11, 17, 10, 0), 60, Sesion.EstadoSesion.CANCELADA);    // cancelada: no cuenta
        sesion(LocalDateTime.of(2026, 11, 20, 13, 0), 60, Sesion.EstadoSesion.CONFIRMADA);   // hoy pero todavía no empieza
        sesion(LocalDateTime.of(2026, 11, 25, 10, 0), 60, Sesion.EstadoSesion.CONFIRMADA);   // futura

        EvolucionResponse r = servicio.evolucion(USUARIO_ESTUDIANTE, "semana", 4);

        assertEquals(4, r.periodos().size());
        PeriodoEvolucionResponse actual = r.periodos().get(3);
        PeriodoEvolucionResponse anterior = r.periodos().get(2);
        assertEquals(LocalDate.of(2026, 11, 16), actual.desde());
        assertEquals(2.5, actual.horas(), 0.001);
        assertEquals(2, actual.sesiones());
        assertEquals(1.0, anterior.horas(), 0.001);
        assertEquals(3.5, r.resumen().totalHoras(), 0.001);
        assertEquals(3, r.resumen().totalSesiones());
        assertEquals("16 nov", actual.etiqueta());
    }

    @Test
    void losPeriodosVanDelMasAntiguoAlActualYSePidenPorDefecto8Semanas() {
        EvolucionResponse r = servicio.evolucion(USUARIO_ESTUDIANTE, null, null);

        assertEquals("semana", r.agrupar());
        assertEquals(8, r.periodos().size());
        assertEquals(LocalDate.of(2026, 9, 28), r.periodos().get(0).desde());
        assertEquals(LocalDate.of(2026, 11, 16), r.periodos().get(7).desde());
    }

    @Test
    void agrupaPorMesConEtiquetasDeMes() {
        sesion(LocalDateTime.of(2026, 10, 10, 10, 0), 120, Sesion.EstadoSesion.CONFIRMADA);
        sesion(LocalDateTime.of(2026, 11, 5, 10, 0), 60, Sesion.EstadoSesion.CONFIRMADA);

        EvolucionResponse r = servicio.evolucion(USUARIO_ESTUDIANTE, "mes", 3);

        assertEquals(List.of("sep 2026", "oct 2026", "nov 2026"), r.periodos().stream().map(PeriodoEvolucionResponse::etiqueta).toList());
        assertEquals(List.of(0.0, 2.0, 1.0), r.periodos().stream().map(PeriodoEvolucionResponse::horas).toList());
        assertEquals(LocalDate.of(2026, 11, 30), r.periodos().get(2).hasta());
    }

    @Test
    void promediaLasAutoevaluacionesYLasNotasDeCadaPeriodo() {
        auto(LocalDate.of(2026, 11, 17), 2, "10.50");
        auto(LocalDate.of(2026, 11, 19), 4, null);
        auto(LocalDate.of(2026, 11, 10), 3, "12");

        EvolucionResponse r = servicio.evolucion(USUARIO_ESTUDIANTE, "semana", 2);

        PeriodoEvolucionResponse actual = r.periodos().get(1);
        assertEquals(3.0, actual.autoevaluacion(), 0.001);
        assertEquals(10.5, actual.nota(), 0.001);
        assertEquals(3.0, r.periodos().get(0).autoevaluacion(), 0.001);
        assertEquals(3.0, r.resumen().promedioAutoevaluacion(), 0.001);
    }

    @Test
    void unPeriodoSinAutoevaluacionesTieneNulosEnVezDeCeros() {
        EvolucionResponse r = servicio.evolucion(USUARIO_ESTUDIANTE, "semana", 2);

        assertNull(r.periodos().get(0).autoevaluacion());
        assertNull(r.periodos().get(0).nota());
        assertNull(r.resumen().promedioAutoevaluacion());
        assertNull(r.resumen().variacion());
    }

    @Test
    void laVariacionEsLaDiferenciaEntreElPrimerYElUltimoPeriodoConDatos() {
        auto(LocalDate.of(2026, 11, 3), 2, null);
        auto(LocalDate.of(2026, 11, 18), 4, null);

        EvolucionResponse r = servicio.evolucion(USUARIO_ESTUDIANTE, "semana", 4);

        assertEquals(2.0, r.resumen().variacion(), 0.001);
    }

    @Test
    void cuentaLasMetasDeCadaPeriodoYSuCumplimiento() {
        meta(LocalDate.of(2026, 11, 18), true);
        meta(LocalDate.of(2026, 11, 19), false);
        meta(LocalDate.of(2026, 11, 10), true);

        EvolucionResponse r = servicio.evolucion(USUARIO_ESTUDIANTE, "semana", 2);

        assertEquals(2, r.periodos().get(1).metasTotal());
        assertEquals(1, r.periodos().get(1).metasCompletadas());
        assertEquals(3, r.resumen().totalMetas());
        assertEquals(2, r.resumen().metasCompletadas());
    }

    @Test
    void comparaLaAutoevaluacionAntesYDespuesDeLaPrimeraSesion() {
        sesion(LocalDateTime.of(2026, 11, 10, 10, 0), 60, Sesion.EstadoSesion.CONFIRMADA);
        sesion(LocalDateTime.of(2026, 11, 17, 10, 0), 60, Sesion.EstadoSesion.CONFIRMADA);
        auto(LocalDate.of(2026, 11, 2), 2, null);
        auto(LocalDate.of(2026, 11, 5), 3, null);
        auto(LocalDate.of(2026, 11, 10), 4, null);   // el mismo día de la sesión cuenta como después
        auto(LocalDate.of(2026, 11, 18), 5, null);

        EvolucionResponse.AntesDespues ad = servicio.evolucion(USUARIO_ESTUDIANTE, "semana", 4).antesDespues();

        assertEquals(LocalDate.of(2026, 11, 10), ad.primeraSesion());
        assertEquals(2.5, ad.antes(), 0.001);
        assertEquals(4.5, ad.despues(), 0.001);
    }

    @Test
    void sinSesionesTerminadasNoHayComparacionAntesDespues() {
        sesion(LocalDateTime.of(2026, 11, 30, 10, 0), 60, Sesion.EstadoSesion.CONFIRMADA);
        auto(LocalDate.of(2026, 11, 18), 3, null);

        EvolucionResponse.AntesDespues ad = servicio.evolucion(USUARIO_ESTUDIANTE, "semana", 4).antesDespues();

        assertNull(ad.primeraSesion());
        assertNull(ad.antes());
        assertNull(ad.despues());
    }

    @Test
    void rechazaAgruparPorOtraCosaOPedirDemasiadosPeriodos() {
        assertEquals(HttpStatus.BAD_REQUEST, error(() -> servicio.evolucion(USUARIO_ESTUDIANTE, "año", 4)).getEstado());
        assertEquals(HttpStatus.BAD_REQUEST, error(() -> servicio.evolucion(USUARIO_ESTUDIANTE, "semana", 0)).getEstado());
        assertEquals(HttpStatus.BAD_REQUEST, error(() -> servicio.evolucion(USUARIO_ESTUDIANTE, "semana", EvolucionService.MAX_PERIODOS + 1)).getEstado());
    }

    @Test
    void soloLosEstudiantesTienenPanel() {
        when(estudianteRepository.findByUsuarioId(99)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.FORBIDDEN, error(() -> servicio.evolucion(99, "semana", 4)).getEstado());
    }

    // ---------------------------------------------------------------- autoevaluaciones

    @Test
    void registraUnaAutoevaluacionConFechaDeHoy() {
        AutoevaluacionResponse r = servicio.registrar(USUARIO_ESTUDIANTE, new AutoevaluacionRequest(null, 4, new BigDecimal("15.5"), "  Mejor  "));

        assertEquals(4, r.puntaje());
        assertEquals(new BigDecimal("15.50"), r.nota());
        assertEquals("Mejor", r.comentario());
        assertEquals(HOY, r.fecha());
        verify(autoevaluacionRepository).save(any(Autoevaluacion.class));
    }

    @Test
    void laAutoevaluacionPuedeIrConUnCurso() {
        Curso bd = new Curso();
        bd.setId(4);
        bd.setNombre("Base de Datos");
        when(cursoRepository.findById(4)).thenReturn(Optional.of(bd));
        when(cursoRepository.findById(9)).thenReturn(Optional.empty());

        assertEquals("Base de Datos", servicio.registrar(USUARIO_ESTUDIANTE, new AutoevaluacionRequest(4, 3, null, null)).curso());
        assertEquals(HttpStatus.BAD_REQUEST, error(() -> servicio.registrar(USUARIO_ESTUDIANTE, new AutoevaluacionRequest(9, 3, null, null))).getEstado());
    }

    @Test
    void rechazaPuntajesNotasYComentariosFueraDeRango() {
        assertEquals(HttpStatus.BAD_REQUEST, error(() -> servicio.registrar(USUARIO_ESTUDIANTE, new AutoevaluacionRequest(null, 0, null, null))).getEstado());
        assertEquals(HttpStatus.BAD_REQUEST, error(() -> servicio.registrar(USUARIO_ESTUDIANTE, new AutoevaluacionRequest(null, 6, null, null))).getEstado());
        assertEquals(HttpStatus.BAD_REQUEST, error(() -> servicio.registrar(USUARIO_ESTUDIANTE, new AutoevaluacionRequest(null, null, null, null))).getEstado());
        assertEquals(HttpStatus.BAD_REQUEST, error(() -> servicio.registrar(USUARIO_ESTUDIANTE, new AutoevaluacionRequest(null, 3, new BigDecimal("20.5"), null))).getEstado());
        assertEquals(HttpStatus.BAD_REQUEST, error(() -> servicio.registrar(USUARIO_ESTUDIANTE, new AutoevaluacionRequest(null, 3, new BigDecimal("-1"), null))).getEstado());
        assertEquals(HttpStatus.BAD_REQUEST, error(() -> servicio.registrar(USUARIO_ESTUDIANTE, new AutoevaluacionRequest(null, 3, null, "x".repeat(201)))).getEstado());
        verify(autoevaluacionRepository, never()).save(any());
    }

    @Test
    void aceptaLosExtremosDeLaNota() {
        assertEquals(new BigDecimal("0.00"), servicio.registrar(USUARIO_ESTUDIANTE, new AutoevaluacionRequest(null, 1, BigDecimal.ZERO, null)).nota());
        assertEquals(new BigDecimal("20.00"), servicio.registrar(USUARIO_ESTUDIANTE, new AutoevaluacionRequest(null, 5, new BigDecimal("20"), null)).nota());
    }

    @Test
    void limitaLasAutoevaluacionesPorDia() {
        when(autoevaluacionRepository.contarDelDia(USUARIO_ESTUDIANTE, HOY)).thenReturn((long) EvolucionService.MAX_AUTOEVALUACIONES_POR_DIA);

        ReglaNegocioException e = error(() -> servicio.registrar(USUARIO_ESTUDIANTE, new AutoevaluacionRequest(null, 3, null, null)));

        assertEquals(HttpStatus.CONFLICT, e.getEstado());
    }

    @Test
    void borraSoloSusPropiasAutoevaluaciones() {
        Autoevaluacion propia = new Autoevaluacion();
        propia.setEstudiante(estudiante);
        Estudiante otro = new Estudiante();
        otro.setUsuario(PruebaSesiones.usuario(99, "Otro", "Alumno", "o@tecsup.edu.pe"));
        Autoevaluacion ajena = new Autoevaluacion();
        ajena.setEstudiante(otro);
        when(autoevaluacionRepository.findById(1)).thenReturn(Optional.of(propia));
        when(autoevaluacionRepository.findById(2)).thenReturn(Optional.of(ajena));

        servicio.eliminar(USUARIO_ESTUDIANTE, 1);
        verify(autoevaluacionRepository).delete(propia);

        assertEquals(HttpStatus.NOT_FOUND, error(() -> servicio.eliminar(USUARIO_ESTUDIANTE, 2)).getEstado());
        verify(autoevaluacionRepository, never()).delete(ajena);
    }

    @Test
    void lasRangosDeSemanaYMesTerminanEnElPeriodoActual() {
        List<LocalDate[]> semanas = EvolucionService.rangos("semana", 3, HOY);
        assertEquals(LocalDate.of(2026, 11, 2), semanas.get(0)[0]);
        assertEquals(LocalDate.of(2026, 11, 22), semanas.get(2)[1]);
        List<LocalDate[]> meses = EvolucionService.rangos("mes", 2, HOY);
        assertEquals(LocalDate.of(2026, 10, 1), meses.get(0)[0]);
        assertEquals(LocalDate.of(2026, 10, 31), meses.get(0)[1]);
        assertTrue(meses.get(1)[1].isAfter(HOY));
    }
}
