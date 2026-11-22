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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

// HU-12: panel de evolución del estudiante. Por semana o por mes junta las horas de asesoría que recibió (sesiones ya
// terminadas), cómo se autoevaluó (de 1 a 5, con una nota opcional de 0 a 20) y cuánto cumplió de sus metas, y compara su
// autoevaluación antes y después de su primera sesión de mentoría.
@Service
public class EvolucionService {

    public static final String SEMANA = "semana";
    public static final String MES = "mes";
    static final int MAX_PERIODOS = 26;
    static final int MAX_AUTOEVALUACIONES_POR_DIA = 5;
    static final int MAX_COMENTARIO = 200;

    private static final String[] MESES = {"ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"};
    private static final LocalDate INICIO_DE_TODO = LocalDate.of(2000, 1, 1);

    private final SesionRepository sesionRepository;
    private final AutoevaluacionRepository autoevaluacionRepository;
    private final MetaRepository metaRepository;
    private final EstudianteRepository estudianteRepository;
    private final CursoRepository cursoRepository;
    private final Clock reloj;

    @Autowired
    public EvolucionService(SesionRepository sesionRepository, AutoevaluacionRepository autoevaluacionRepository,
                            MetaRepository metaRepository, EstudianteRepository estudianteRepository,
                            CursoRepository cursoRepository) {
        this(sesionRepository, autoevaluacionRepository, metaRepository, estudianteRepository, cursoRepository,
                Clock.systemDefaultZone());
    }

    // El reloj se puede cambiar solo en las pruebas automaticas.
    EvolucionService(SesionRepository sesionRepository, AutoevaluacionRepository autoevaluacionRepository,
                     MetaRepository metaRepository, EstudianteRepository estudianteRepository,
                     CursoRepository cursoRepository, Clock reloj) {
        this.sesionRepository = sesionRepository;
        this.autoevaluacionRepository = autoevaluacionRepository;
        this.metaRepository = metaRepository;
        this.estudianteRepository = estudianteRepository;
        this.cursoRepository = cursoRepository;
        this.reloj = reloj;
    }

    // ---------------------------------------------------------------- evolución

    // periodos = cuántas semanas o meses mostrar, terminando en el actual (por defecto 8 semanas o 6 meses).
    @Transactional(readOnly = true)
    public EvolucionResponse evolucion(Integer usuarioId, String agrupar, Integer periodos) {
        estudiante(usuarioId);
        String modo = agrupar == null || agrupar.isBlank() ? SEMANA : agrupar.trim().toLowerCase();
        if (!SEMANA.equals(modo) && !MES.equals(modo)) {
            throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "Agrupa por \"semana\" o por \"mes\".");
        }
        int cantidad = periodos == null ? (SEMANA.equals(modo) ? 8 : 6) : periodos;
        if (cantidad < 1 || cantidad > MAX_PERIODOS) {
            throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "Pide entre 1 y " + MAX_PERIODOS + " periodos.");
        }

        LocalDateTime ahora = LocalDateTime.now(reloj);
        LocalDate hoy = ahora.toLocalDate();
        List<LocalDate[]> rangos = rangos(modo, cantidad, hoy);
        LocalDate desde = rangos.get(0)[0];
        LocalDate hasta = rangos.get(rangos.size() - 1)[1];

        List<Sesion> terminadas = sesionRepository.historialDeEstudiante(usuarioId).stream()
                .filter(s -> s.getEstado() == Sesion.EstadoSesion.CONFIRMADA
                        && !LocalDateTime.of(s.getFecha(), s.getHoraFin()).isAfter(ahora))
                .toList();
        List<Autoevaluacion> autoevaluaciones = autoevaluacionRepository.delEstudianteEntre(usuarioId, desde, hasta);
        List<Meta> metas = metaRepository.delEstudianteEntre(usuarioId, desde, hasta);

        List<PeriodoEvolucionResponse> lista = new ArrayList<>();
        for (LocalDate[] r : rangos) {
            List<Sesion> sesiones = terminadas.stream().filter(s -> dentro(s.getFecha(), r)).toList();
            double horas = redondear(sesiones.stream().mapToDouble(EvolucionService::horasDe).sum());
            List<Autoevaluacion> auto = autoevaluaciones.stream().filter(a -> dentro(a.getFecha(), r)).toList();
            List<Meta> deMetas = metas.stream().filter(m -> dentro(m.getFechaEntrega(), r)).toList();
            lista.add(new PeriodoEvolucionResponse(etiqueta(modo, r[0]), r[0], r[1], horas, sesiones.size(),
                    promedio(auto.stream().mapToDouble(a -> a.getPuntaje()).toArray()),
                    promedio(auto.stream().filter(a -> a.getNota() != null).mapToDouble(a -> a.getNota().doubleValue()).toArray()),
                    deMetas.size(), (int) deMetas.stream().filter(Meta::isCompletada).count()));
        }

        double totalHoras = redondear(lista.stream().mapToDouble(PeriodoEvolucionResponse::horas).sum());
        List<Double> conDatos = lista.stream().map(PeriodoEvolucionResponse::autoevaluacion).filter(p -> p != null).toList();
        Double variacion = conDatos.size() < 2 ? null : redondear(conDatos.get(conDatos.size() - 1) - conDatos.get(0));
        EvolucionResponse.Resumen resumen = new EvolucionResponse.Resumen(totalHoras,
                lista.stream().mapToInt(PeriodoEvolucionResponse::sesiones).sum(),
                promedio(autoevaluaciones.stream().mapToDouble(a -> a.getPuntaje()).toArray()), variacion,
                metas.size(), (int) metas.stream().filter(Meta::isCompletada).count());
        return new EvolucionResponse(modo, lista, resumen, antesDespues(usuarioId, terminadas, hoy));
    }

    // Promedio de las autoevaluaciones anteriores a la primera sesión terminada y de las posteriores (la del mismo día cuenta como posterior).
    private EvolucionResponse.AntesDespues antesDespues(Integer usuarioId, List<Sesion> terminadas, LocalDate hoy) {
        LocalDate primera = terminadas.stream().map(Sesion::getFecha).min(LocalDate::compareTo).orElse(null);
        if (primera == null) {
            return new EvolucionResponse.AntesDespues(null, null, null);
        }
        List<Autoevaluacion> todas = autoevaluacionRepository.delEstudianteEntre(usuarioId, INICIO_DE_TODO, hoy);
        Double antes = promedio(todas.stream().filter(a -> a.getFecha().isBefore(primera)).mapToDouble(a -> a.getPuntaje()).toArray());
        Double despues = promedio(todas.stream().filter(a -> !a.getFecha().isBefore(primera)).mapToDouble(a -> a.getPuntaje()).toArray());
        return new EvolucionResponse.AntesDespues(primera, antes, despues);
    }

    // ---------------------------------------------------------------- autoevaluaciones

    @Transactional
    public AutoevaluacionResponse registrar(Integer usuarioId, AutoevaluacionRequest solicitud) {
        Estudiante estudiante = estudiante(usuarioId);
        Integer puntaje = solicitud.puntaje();
        if (puntaje == null || puntaje < 1 || puntaje > 5) {
            throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "Elige de 1 a 5 cómo te sientes con el curso.");
        }
        BigDecimal nota = solicitud.nota();
        if (nota != null) {
            if (nota.compareTo(BigDecimal.ZERO) < 0 || nota.compareTo(BigDecimal.valueOf(20)) > 0) {
                throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "La nota va de 0 a 20.");
            }
            nota = nota.setScale(2, RoundingMode.HALF_UP);
        }
        String comentario = solicitud.comentario() == null ? "" : solicitud.comentario().trim();
        if (comentario.length() > MAX_COMENTARIO) {
            throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "El comentario no puede superar los " + MAX_COMENTARIO + " caracteres.");
        }
        Curso curso = null;
        if (solicitud.idCurso() != null) {
            curso = cursoRepository.findById(solicitud.idCurso())
                    .orElseThrow(() -> new ReglaNegocioException(HttpStatus.BAD_REQUEST, "El curso no existe en el catálogo."));
        }
        LocalDate hoy = LocalDate.now(reloj);
        if (autoevaluacionRepository.contarDelDia(usuarioId, hoy) >= MAX_AUTOEVALUACIONES_POR_DIA) {
            throw new ReglaNegocioException(HttpStatus.CONFLICT,
                    "Ya registraste " + MAX_AUTOEVALUACIONES_POR_DIA + " autoevaluaciones hoy. Sigue mañana.");
        }

        Autoevaluacion a = new Autoevaluacion();
        a.setEstudiante(estudiante);
        a.setCurso(curso);
        a.setPuntaje(puntaje);
        a.setNota(nota);
        a.setComentario(comentario.isEmpty() ? null : comentario);
        a.setFecha(hoy);
        autoevaluacionRepository.save(a);
        return aRespuesta(a);
    }

    // Las 10 más recientes, para poder corregir una que se registró mal.
    @Transactional(readOnly = true)
    public List<AutoevaluacionResponse> recientes(Integer usuarioId) {
        estudiante(usuarioId);
        return autoevaluacionRepository.recientes(usuarioId, PageRequest.of(0, 10)).stream().map(EvolucionService::aRespuesta).toList();
    }

    @Transactional
    public void eliminar(Integer usuarioId, Integer id) {
        estudiante(usuarioId);
        Autoevaluacion a = autoevaluacionRepository.findById(id)
                .filter(x -> x.getEstudiante().getUsuario().getId().equals(usuarioId))
                .orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "La autoevaluación no existe."));
        autoevaluacionRepository.delete(a);
    }

    // ---------------------------------------------------------------- apoyo

    private Estudiante estudiante(Integer usuarioId) {
        return estudianteRepository.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new ReglaNegocioException(HttpStatus.FORBIDDEN, "Solo los estudiantes tienen panel de evolución."));
    }

    // Los rangos [inicio, fin] de los últimos "cantidad" periodos, del más antiguo al actual.
    static List<LocalDate[]> rangos(String modo, int cantidad, LocalDate hoy) {
        List<LocalDate[]> rangos = new ArrayList<>();
        for (int i = cantidad - 1; i >= 0; i--) {
            if (SEMANA.equals(modo)) {
                LocalDate lunes = MetaService.lunesDe(hoy).minusWeeks(i);
                rangos.add(new LocalDate[]{lunes, lunes.plusDays(6)});
            } else {
                LocalDate primero = hoy.withDayOfMonth(1).minusMonths(i);
                rangos.add(new LocalDate[]{primero, primero.withDayOfMonth(primero.lengthOfMonth())});
            }
        }
        return rangos;
    }

    private static boolean dentro(LocalDate fecha, LocalDate[] rango) {
        return !fecha.isBefore(rango[0]) && !fecha.isAfter(rango[1]);
    }

    private static String etiqueta(String modo, LocalDate inicio) {
        return SEMANA.equals(modo)
                ? inicio.getDayOfMonth() + " " + MESES[inicio.getMonthValue() - 1]
                : MESES[inicio.getMonthValue() - 1] + " " + inicio.getYear();
    }

    private static double horasDe(Sesion s) {
        return Duration.between(s.getHoraInicio(), s.getHoraFin()).toMinutes() / 60.0;
    }

    private static Double promedio(double[] valores) {
        OptionalDouble media = java.util.Arrays.stream(valores).average();
        return media.isPresent() ? redondear(media.getAsDouble()) : null;
    }

    private static double redondear(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }

    private static AutoevaluacionResponse aRespuesta(Autoevaluacion a) {
        return new AutoevaluacionResponse(a.getId(), a.getCurso() == null ? null : a.getCurso().getId(),
                a.getCurso() == null ? null : a.getCurso().getNombre(), a.getPuntaje(), a.getNota(), a.getComentario(), a.getFecha());
    }
}
