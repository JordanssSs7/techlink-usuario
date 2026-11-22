package com.techlink.service;

import com.techlink.dto.MentorEstadisticasDtos.Constancia;
import com.techlink.dto.MentorEstadisticasDtos.CursoAtendido;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.ConstanciaMentor;
import com.techlink.model.Curso;
import com.techlink.model.Mentor;
import com.techlink.model.Sesion;
import com.techlink.repository.ConstanciaMentorRepository;
import com.techlink.repository.MentorRepository;
import com.techlink.repository.SesionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.techlink.service.PruebaSesiones.ID_MENTOR;
import static com.techlink.service.PruebaSesiones.USUARIO_MENTOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pruebas de HU-27: constancia de mentoría (estado, emisión por el administrador y PDF).
class ConstanciaServiceTest {

    // "Ahora" para las pruebas: 20/11/2026 a las 12:00.
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 20, 12, 0);
    // En las pruebas el certificado se entrega a las 2 horas (en la aplicación son 50, configurables).
    private static final double HORAS_MINIMAS = 2;

    private InsigniaService insigniaService;
    private MentorRepository mentorRepository;
    private ConstanciaMentorRepository constanciaRepository;
    private ConstanciaService servicio;
    private final List<Sesion> sesiones = new ArrayList<>();

    @BeforeEach
    void preparar() {
        mentorRepository = Mockito.mock(MentorRepository.class);
        SesionRepository sesionRepository = Mockito.mock(SesionRepository.class);
        constanciaRepository = Mockito.mock(ConstanciaMentorRepository.class);
        when(constanciaRepository.save(any(ConstanciaMentor.class))).thenAnswer(i -> i.getArgument(0));
        insigniaService = Mockito.mock(InsigniaService.class);
        Clock reloj = Clock.fixed(AHORA.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        servicio = new ConstanciaService(mentorRepository, sesionRepository, insigniaService, constanciaRepository, HORAS_MINIMAS, "http://localhost:5173", reloj);

        Mentor mentor = PruebaSesiones.mentor();
        when(mentorRepository.findByUsuarioId(USUARIO_MENTOR)).thenReturn(Optional.of(mentor));
        when(sesionRepository.historialDeMentor(USUARIO_MENTOR)).thenReturn(sesiones);
        when(insigniaService.actividad(ID_MENTOR)).thenReturn(new InsigniaService.Actividad(0, 0));
    }

    private void sesion(int id, LocalDateTime inicio, int minutos, String curso, Sesion.EstadoSesion estado) {
        Sesion s = PruebaSesiones.sesion(id, inicio, minutos, estado);
        if (curso != null) {
            Curso c = new Curso();
            c.setNombre(curso);
            s.setCurso(c);
        }
        sesiones.add(s);
    }

    // La constancia ya la emitió un administrador (el 18/11/2026 a las 09:30).
    private void emitida() {
        ConstanciaMentor fila = new ConstanciaMentor(ID_MENTOR, BigDecimal.valueOf(60));
        fila.setId(9);
        fila.setEstado(ConstanciaMentor.Estado.EMITIDA);
        fila.setFechaEmision(LocalDateTime.of(2026, 11, 18, 9, 30));
        fila.setCodigoVerificacion("9F2A4C1B7E");
        when(constanciaRepository.findByCodigoVerificacion("9F2A4C1B7E")).thenReturn(Optional.of(fila));
        when(mentorRepository.findById(ID_MENTOR)).thenReturn(Optional.of(PruebaSesiones.mentor()));
        when(constanciaRepository.findByIdMentor(ID_MENTOR)).thenReturn(Optional.of(fila));
    }

    private void actividad(double horas, int total) {
        when(insigniaService.actividad(ID_MENTOR)).thenReturn(new InsigniaService.Actividad(horas, total));
    }

    @Test
    void agrupaLasHorasPorCursoAtendido() {
        sesion(1, LocalDateTime.of(2026, 11, 3, 10, 0), 60, "Bases de Datos", Sesion.EstadoSesion.CONFIRMADA);
        sesion(2, LocalDateTime.of(2026, 11, 4, 10, 0), 90, "Bases de Datos", Sesion.EstadoSesion.CONFIRMADA);
        sesion(3, LocalDateTime.of(2026, 10, 15, 10, 0), 60, "Programación", Sesion.EstadoSesion.CONFIRMADA);
        actividad(3.5, 3);

        Constancia c = servicio.datos(USUARIO_MENTOR);

        assertEquals(2, c.cursos().size());
        assertEquals(new CursoAtendido("Bases de Datos", 2, 2.5), c.cursos().get(0));
        assertEquals(new CursoAtendido("Programación", 1, 1.0), c.cursos().get(1));
        assertEquals(3.5, c.horasTotales());
        assertEquals(3, c.sesionesCompletadas());
        assertTrue(c.elegible());
    }

    @Test
    void elPeriodoVaDeLaPrimeraALaUltimaSesion() {
        sesion(1, LocalDateTime.of(2026, 11, 3, 10, 0), 60, "Bases de Datos", Sesion.EstadoSesion.CONFIRMADA);
        sesion(2, LocalDateTime.of(2026, 9, 2, 10, 0), 60, "Bases de Datos", Sesion.EstadoSesion.CONFIRMADA);
        sesion(3, LocalDateTime.of(2026, 10, 15, 10, 0), 60, "Bases de Datos", Sesion.EstadoSesion.CONFIRMADA);
        actividad(3, 3);

        Constancia c = servicio.datos(USUARIO_MENTOR);

        assertEquals(LocalDate.of(2026, 9, 2), c.desde());
        assertEquals(LocalDate.of(2026, 11, 3), c.hasta());
    }

    @Test
    void lasSesionesSinCursoVanJuntasAlFinal() {
        sesion(1, LocalDateTime.of(2026, 11, 3, 10, 0), 180, null, Sesion.EstadoSesion.CONFIRMADA);
        sesion(2, LocalDateTime.of(2026, 11, 4, 10, 0), 60, "Programación", Sesion.EstadoSesion.CONFIRMADA);
        actividad(4, 2);

        Constancia c = servicio.datos(USUARIO_MENTOR);

        assertEquals("Programación", c.cursos().get(0).curso());
        assertEquals(ConstanciaService.SIN_CURSO, c.cursos().get(1).curso());
        assertEquals(3.0, c.cursos().get(1).horas());
    }

    @Test
    void noCuentanLasCanceladasNiLasQueTodaviaNoTerminan() {
        sesion(1, LocalDateTime.of(2026, 11, 3, 10, 0), 60, "Bases de Datos", Sesion.EstadoSesion.CANCELADA);
        sesion(2, LocalDateTime.of(2026, 11, 20, 11, 30), 60, "Bases de Datos", Sesion.EstadoSesion.CONFIRMADA);
        sesion(3, LocalDateTime.of(2026, 11, 25, 10, 0), 60, "Bases de Datos", Sesion.EstadoSesion.CONFIRMADA);

        Constancia c = servicio.datos(USUARIO_MENTOR);

        assertTrue(c.cursos().isEmpty());
        assertFalse(c.elegible());
    }

    @Test
    void elFolioYLaFechaDeEmisionSeArmanConElMomentoDeLaEmision() {
        Constancia c = servicio.datos(USUARIO_MENTOR);

        assertEquals(AHORA, c.emitidaEn());
        assertEquals("TL-0005-20261120", c.folio());
        assertEquals("Lucía Ramírez", c.mentor());
        assertEquals("lucia@tecsup.edu.pe", c.correo());
    }

    @Test
    void sinHorasSuficientesNoSePuedeDescargarElPdf() {
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class, () -> servicio.pdf(USUARIO_MENTOR));

        assertEquals(HttpStatus.CONFLICT, error.getEstado());
    }

    @Test
    void sinPerfilDeMentorEsNoEncontrado() {
        when(mentorRepository.findByUsuarioId(anyInt())).thenReturn(Optional.empty());

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class, () -> servicio.pdf(99));

        assertEquals(HttpStatus.NOT_FOUND, error.getEstado());
    }

    @Test
    void alLlegarALasHorasMinimasLaConstanciaQuedaPendienteDeEmision() {
        actividad(2.0, 2);

        Constancia c = servicio.datos(USUARIO_MENTOR);

        assertTrue(c.elegible());
        assertEquals("PENDIENTE", c.estado());
        assertFalse(c.puedeDescargar(), "hasta que el administrador la emita no se puede descargar");
        assertEquals(2.0, c.horasMinimas());
        verify(constanciaRepository).save(any(ConstanciaMentor.class));
    }

    @Test
    void conUnaHoraMenosDeLasMinimasNoSeRegistraNada() {
        actividad(1.9, 2);

        Constancia c = servicio.datos(USUARIO_MENTOR);

        assertFalse(c.elegible());
        assertEquals("NO_ELEGIBLE", c.estado());
        verify(constanciaRepository, never()).save(any());
    }

    @Test
    void unaConstanciaYaRegistradaNoSeDuplica() {
        actividad(5, 3);
        emitida();

        servicio.datos(USUARIO_MENTOR);

        verify(constanciaRepository, never()).save(any());
    }

    @Test
    void unaConstanciaEmitidaSePuedeDescargarYLlevaSuFolioYFechaDeEmision() {
        actividad(60, 30);
        emitida();

        Constancia c = servicio.datos(USUARIO_MENTOR);

        assertEquals("EMITIDA", c.estado());
        assertTrue(c.puedeDescargar());
        assertEquals("TL-C00009", c.folio());
        assertEquals(LocalDateTime.of(2026, 11, 18, 9, 30), c.emitidaEn());
    }

    @Test
    void unaConstanciaEmitidaSinCodigoRecibeUnoAlConsultarla() {
        actividad(60, 30);
        emitida();
        ConstanciaMentor fila = constanciaRepository.findByIdMentor(ID_MENTOR).orElseThrow();
        fila.setCodigoVerificacion(null);

        Constancia c = servicio.datos(USUARIO_MENTOR);

        assertTrue(c.codigoVerificacion().matches("[0-9A-F]{10}"), c.codigoVerificacion());
        verify(constanciaRepository).save(fila);
    }

    @Test
    void unaConstanciaPendienteNoTieneCodigo() {
        actividad(2.5, 2);

        assertEquals(null, servicio.datos(USUARIO_MENTOR).codigoVerificacion());
    }

    @Test
    void verificarUnCodigoValidoDevuelveLosDatosDeLaConstancia() {
        sesion(1, LocalDateTime.of(2026, 11, 4, 10, 0), 120, "Bases de Datos", Sesion.EstadoSesion.CONFIRMADA);
        actividad(60, 30);
        emitida();

        var v = servicio.verificar("9f2a4c1b7e");

        assertTrue(v.valida(), "no distingue mayúsculas");
        assertEquals("Lucía Ramírez", v.mentor());
        assertEquals("Bases de Datos", v.curso());
        assertEquals(60.0, v.horas());
        assertEquals("2026-11-18", v.fechaEmision());
    }

    @Test
    void verificarUnCodigoInventadoNoEsValido() {
        actividad(60, 30);
        emitida();

        assertFalse(servicio.verificar("0000000000").valida());
        assertFalse(servicio.verificar("").valida());
        assertFalse(servicio.verificar(null).valida());
    }

    @Test
    void verificarNoRevelaNadaDeUnaConstanciaPendiente() {
        ConstanciaMentor fila = new ConstanciaMentor(ID_MENTOR, BigDecimal.valueOf(60));
        fila.setCodigoVerificacion("AAAAAAAAAA");
        when(constanciaRepository.findByCodigoVerificacion("AAAAAAAAAA")).thenReturn(Optional.of(fila));

        var v = servicio.verificar("AAAAAAAAAA");

        assertFalse(v.valida());
        assertEquals(null, v.mentor());
    }

    @Test
    void laDireccionDeVerificacionNoDuplicaLaBarra() {
        assertEquals("http://localhost:5173/verificar/ABC", servicio.urlDeVerificacion("ABC"));
    }

    @Test
    void siEstaPendienteElPdfSeRechazaYSeExplicaQueFaltaElAdministrador() {
        actividad(2.5, 2);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class, () -> servicio.pdf(USUARIO_MENTOR));

        assertEquals(HttpStatus.CONFLICT, error.getEstado());
        assertTrue(error.getMessage().contains("administrador"), error.getMessage());
    }

    @Test
    void elProcesoProgramadoRegistraALosMentoresQueYaCumplieron() {
        Mentor cumple = PruebaSesiones.mentor();
        Mentor otro = PruebaSesiones.mentor();
        otro.setId(6);
        Mentor yaTiene = PruebaSesiones.mentor();
        yaTiene.setId(7);
        when(mentorRepository.findAll()).thenReturn(List.of(cumple, otro, yaTiene));
        when(insigniaService.actividad(ID_MENTOR)).thenReturn(new InsigniaService.Actividad(3, 2));
        when(insigniaService.actividad(6)).thenReturn(new InsigniaService.Actividad(1, 1));
        when(constanciaRepository.findByIdMentor(7)).thenReturn(Optional.of(new ConstanciaMentor(7, BigDecimal.TEN)));

        assertEquals(1, servicio.registrarElegibles());

        verify(constanciaRepository).save(any(ConstanciaMentor.class));
    }

    @Test
    void sinLasHorasMinimasElPdfSeRechazaDiciendoCuantoFalta() {
        actividad(1.5, 2);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class, () -> servicio.pdf(USUARIO_MENTOR));

        assertEquals(HttpStatus.CONFLICT, error.getEstado());
        assertTrue(error.getMessage().contains("2 horas") && error.getMessage().contains("1,5"), error.getMessage());
    }

    @Test
    void elCursoPrincipalEsElDeMasHoras() {
        sesion(1, LocalDateTime.of(2026, 11, 3, 10, 0), 60, "Programación", Sesion.EstadoSesion.CONFIRMADA);
        sesion(2, LocalDateTime.of(2026, 11, 4, 10, 0), 120, "Bases de Datos", Sesion.EstadoSesion.CONFIRMADA);
        sesion(3, LocalDateTime.of(2026, 11, 5, 10, 0), 300, null, Sesion.EstadoSesion.CONFIRMADA);
        actividad(8, 3);

        assertEquals("Bases de Datos", servicio.datos(USUARIO_MENTOR).cursoPrincipal());
    }

    @Test
    void sinCursosIndicadosElCursoPrincipalEsLaEspecialidad() {
        sesion(1, LocalDateTime.of(2026, 11, 3, 10, 0), 120, null, Sesion.EstadoSesion.CONFIRMADA);
        actividad(2, 1);
        Mentor mentor = PruebaSesiones.mentor();
        mentor.setEspecialidad("Redes y comunicaciones");
        when(mentorRepository.findByUsuarioId(USUARIO_MENTOR)).thenReturn(Optional.of(mentor));

        assertEquals("Redes y comunicaciones", servicio.datos(USUARIO_MENTOR).cursoPrincipal());
    }

    @Test
    void elPdfEsUnaConstanciaHorizontalConElNombreDelPerfilYElCursoPrincipal() {
        sesion(1, LocalDateTime.of(2026, 11, 3, 10, 0), 90, "Bases de Datos", Sesion.EstadoSesion.CONFIRMADA);
        sesion(2, LocalDateTime.of(2026, 11, 4, 10, 0), 60, "Programación (nivel 1)", Sesion.EstadoSesion.CONFIRMADA);
        actividad(2.5, 2);
        emitida();

        String texto = new String(servicio.pdf(USUARIO_MENTOR), StandardCharsets.ISO_8859_1);

        assertTrue(texto.startsWith("%PDF-1.4"));
        assertTrue(texto.trim().endsWith("%%EOF"));
        assertTrue(texto.contains("/MediaBox [0 0 842 595]"), "hoja A4 horizontal");
        assertTrue(texto.contains("/Count 1"));
        assertTrue(texto.contains("CONSTANCIA"));
        assertTrue(texto.contains("DE PARTICIPACI\u00d3N COMO MENTOR"));
        assertTrue(texto.contains("Lucía Ramírez"), "lleva el nombre del perfil con sus tildes");
        assertTrue(texto.contains("mentor de Bases de Datos"), "lleva el curso principal");
        assertTrue(texto.contains("m\u00e1s de 2 horas"), "lleva las horas mínimas");
        assertTrue(texto.contains("9F2A4C1B7E"), "lleva el código de verificación");
        assertTrue(texto.contains("C\u00f3digo de verificaci\u00f3n"));
        assertTrue(texto.contains("localhost:5173/verificar/9F2A4C1B7E"), "lleva la dirección de la página de verificación");
        assertTrue(texto.contains("No es una certificaci\u00f3n oficial de TECSUP"), "aclara que la emite TechLink y no TECSUP");
        assertTrue(texto.split("re f").length > 150, "dibuja el código QR");
        assertTrue(texto.contains("18 de noviembre de 2026"), "lleva la fecha en que la emitió el administrador");
        // Las letras del sello van una por una, giradas sobre el arco (operador Tm).
        assertTrue(texto.contains("Tm (T) Tj") && texto.contains("Tm (K) Tj"), "lleva el sello de TechLink");
    }

    @Test
    void laConstanciaNoListaCursosNiSesiones() {
        for (int i = 1; i <= 40; i++) {
            sesion(i, LocalDateTime.of(2026, 10, 1, 8, 0).plusDays(i % 20), 60, "Curso numero " + i, Sesion.EstadoSesion.CONFIRMADA);
        }
        actividad(40, 40);
        emitida();

        String texto = new String(servicio.pdf(USUARIO_MENTOR), StandardCharsets.ISO_8859_1);

        assertTrue(texto.contains("/Count 1"), "siempre una sola página");
        assertFalse(texto.contains("Curso numero 2"), "el detalle de cursos no se imprime");
        assertFalse(texto.contains("Sesiones completadas"));
    }

    @Test
    void unNombreMuyLargoSigueEntrandoEnLaHoja() {
        Mentor mentor = PruebaSesiones.mentor();
        mentor.getUsuario().setNombres("María de los Ángeles Guadalupe del Carmen");
        mentor.getUsuario().setApellidos("Fernández de Córdoba y Rodríguez de la Vega");
        when(mentorRepository.findByUsuarioId(USUARIO_MENTOR)).thenReturn(Optional.of(mentor));
        actividad(10, 5);
        emitida();

        String texto = new String(servicio.pdf(USUARIO_MENTOR), StandardCharsets.ISO_8859_1);

        assertTrue(texto.contains("Fernández de Córdoba"));
    }

    @Test
    void lasHorasSeEscribenConComaYSinCerosSobrantes() {
        assertEquals("12", ConstanciaPdf.horas(12.0));
        assertEquals("12,5", ConstanciaPdf.horas(12.5));
        assertEquals("12,25", ConstanciaPdf.horas(12.25));
        assertEquals("0", ConstanciaPdf.horas(0));
    }
}
