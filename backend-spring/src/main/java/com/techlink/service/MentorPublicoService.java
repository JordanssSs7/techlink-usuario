package com.techlink.service;

import com.techlink.dto.BusquedaMentoresResponse;
import com.techlink.dto.InsigniaResponse;
import com.techlink.dto.MentorPerfilPublico;
import com.techlink.dto.MentorRankingItem;
import com.techlink.exception.DatosInvalidosException;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.Calificacion;
import com.techlink.model.Curso;
import com.techlink.model.EstadoUsuario;
import com.techlink.model.Insignia;
import com.techlink.model.Mentor;
import com.techlink.model.MentorDisponibilidad;
import com.techlink.model.Usuario;
import com.techlink.repository.CalificacionRepository;
import com.techlink.repository.MentorRepository;
import com.techlink.repository.UsuarioFotoRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.IntStream;

// HU-14: perfil público de un mentor (cursos, promedio de estrellas, últimos comentarios) para decidir con quién agendar.
// HU-15: ranking de mentores con sus insignias.
// HU-16: búsqueda de mentores por curso, horario y calificación mínima (los filtros se combinan).
// El promedio sale de las calificaciones registradas tras cada sesión, así que siempre coincide con ellas.
@Service
public class MentorPublicoService {

    static final int MAX_COMENTARIOS = 5;

    private final MentorRepository mentorRepository;
    private final CalificacionRepository calificacionRepository;
    private final UsuarioFotoRepository fotoRepository;
    private final InsigniaService insigniaService;

    public MentorPublicoService(MentorRepository mentorRepository,
                                CalificacionRepository calificacionRepository,
                                UsuarioFotoRepository fotoRepository,
                                InsigniaService insigniaService) {
        this.mentorRepository = mentorRepository;
        this.calificacionRepository = calificacionRepository;
        this.fotoRepository = fotoRepository;
        this.insigniaService = insigniaService;
    }

    // Todos los mentores activos, del mejor al menos calificado (y a igualdad, con más horas dictadas).
    @Transactional(readOnly = true)
    public List<MentorRankingItem> ranking() {
        return rankear(activos());
    }

    // HU-16: mentores que cumplen TODOS los filtros enviados (los que vengan null no se aplican), con el mismo orden del
    // ranking. dia: 1 (lunes) a 7 (domingo). desde y hasta: horas "HH:mm"; el mentor debe tener un horario activo que se
    // cruce con ese rango (en ese día, si se indicó). calificacionMinima: de 0 a 5; los mentores sin calificaciones no
    // cumplen una mínima mayor que 0. Si no hay resultados, la respuesta explica qué filtro conviene ampliar.
    @Transactional(readOnly = true)
    public BusquedaMentoresResponse buscar(Integer cursoId, Integer dia, String desde, String hasta, Double calificacionMinima) {
        Filtros filtros = validar(cursoId, dia, desde, hasta, calificacionMinima);
        List<Mentor> todos = activos();
        List<Mentor> coinciden = todos.stream().filter(filtros.predicado()).toList();
        List<MentorRankingItem> items = rankear(coinciden);
        if (!items.isEmpty()) {
            return new BusquedaMentoresResponse(items, items.size(), null, List.of());
        }
        return new BusquedaMentoresResponse(items, 0, todos.isEmpty()
                ? "Todavía no hay mentores registrados."
                : "No encontramos mentores con esos filtros.", sugerencias(filtros, todos));
    }

    // Filtros ya validados. Cada uno puede ser null (no se aplica).
    private record Filtros(Integer cursoId, Integer dia, LocalTime desde, LocalTime hasta, Double calificacionMinima) {

        boolean hayCurso() {
            return cursoId != null;
        }

        boolean hayHorario() {
            return dia != null || desde != null || hasta != null;
        }

        boolean hayCalificacion() {
            return calificacionMinima != null && calificacionMinima > 0;
        }

        Predicate<Mentor> predicado() {
            return m -> (!hayCurso() || enCurso(m)) && (!hayHorario() || enHorario(m)) && (!hayCalificacion() || conNota(m));
        }

        private boolean enCurso(Mentor m) {
            return m.getCursos().stream().anyMatch(c -> cursoId.equals(c.getId()));
        }

        // Tiene un horario activo que se cruza con el rango pedido (en el día indicado, si se indicó uno).
        private boolean enHorario(Mentor m) {
            LocalTime inicio = desde == null ? LocalTime.MIN : desde;
            LocalTime fin = hasta == null ? LocalTime.MAX : hasta;
            for (MentorDisponibilidad d : m.getDisponibilidad()) {
                boolean dentro = Boolean.TRUE.equals(d.getActivo())
                        && (dia == null || dia.equals(d.getDiaSemana()))
                        && d.getHoraInicio().isBefore(fin) && d.getHoraFin().isAfter(inicio);
                if (dentro) {
                    return true;
                }
            }
            return false;
        }

        private boolean conNota(Mentor m) {
            BigDecimal promedio = promedioDe(m);
            return promedio != null && promedio.doubleValue() >= calificacionMinima;
        }

        Filtros sin(String filtro) {
            return switch (filtro) {
                case "curso" -> new Filtros(null, dia, desde, hasta, calificacionMinima);
                case "horario" -> new Filtros(cursoId, null, null, null, calificacionMinima);
                default -> new Filtros(cursoId, dia, desde, hasta, null);
            };
        }
    }

    private static Filtros validar(Integer cursoId, Integer dia, String desde, String hasta, Double calificacionMinima) {
        if (dia != null && (dia < 1 || dia > 7)) {
            throw new DatosInvalidosException("dia", "El día debe estar entre 1 (lunes) y 7 (domingo).");
        }
        if (calificacionMinima != null && (calificacionMinima < 0 || calificacionMinima > 5)) {
            throw new DatosInvalidosException("calificacionMinima", "La calificación mínima debe estar entre 0 y 5.");
        }
        LocalTime horaDesde = hora("desde", desde);
        LocalTime horaHasta = hora("hasta", hasta);
        if (horaDesde != null && horaHasta != null && !horaHasta.isAfter(horaDesde)) {
            throw new DatosInvalidosException("hasta", "La hora final debe ser posterior a la inicial.");
        }
        return new Filtros(cursoId, dia, horaDesde, horaHasta, calificacionMinima);
    }

    private static LocalTime hora(String campo, String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(texto.trim());
        } catch (DateTimeParseException e) {
            throw new DatosInvalidosException(campo, "La hora debe tener el formato HH:mm, por ejemplo 18:30.");
        }
    }

    // Qué pasaría si se quita cada filtro: solo se sugieren los que devuelven mentores, del que más devuelve al que menos.
    private static List<BusquedaMentoresResponse.Sugerencia> sugerencias(Filtros filtros, List<Mentor> todos) {
        List<BusquedaMentoresResponse.Sugerencia> sugerencias = new ArrayList<>();
        int activos = (filtros.hayCurso() ? 1 : 0) + (filtros.hayHorario() ? 1 : 0) + (filtros.hayCalificacion() ? 1 : 0);
        if (filtros.hayCurso()) {
            agregar(sugerencias, "curso", "Quita el curso", filtros, todos);
        }
        if (filtros.hayHorario()) {
            agregar(sugerencias, "horario", "Prueba con otro horario o quita el horario", filtros, todos);
        }
        if (filtros.hayCalificacion()) {
            agregar(sugerencias, "calificacion", "Baja la calificación mínima o quítala", filtros, todos);
        }
        sugerencias.sort(Comparator.comparingInt(BusquedaMentoresResponse.Sugerencia::resultados).reversed());
        // Con varios filtros y ninguna salida individual, lo último que se puede ofrecer es quitarlos todos.
        if (sugerencias.isEmpty() && activos > 1) {
            sugerencias.add(new BusquedaMentoresResponse.Sugerencia("todos",
                    "Quita todos los filtros para ver a los " + todos.size() + " mentores disponibles", todos.size()));
        }
        return sugerencias;
    }

    private static void agregar(List<BusquedaMentoresResponse.Sugerencia> lista, String filtro, String texto,
                                Filtros filtros, List<Mentor> todos) {
        long n = todos.stream().filter(filtros.sin(filtro).predicado()).count();
        if (n > 0) {
            lista.add(new BusquedaMentoresResponse.Sugerencia(filtro,
                    "%s: aparecen %d %s.".formatted(texto, n, n == 1 ? "mentor" : "mentores"), (int) n));
        }
    }

    private List<Mentor> activos() {
        return mentorRepository.findAll().stream()
                .filter(m -> m.getUsuario().getEstado() == EstadoUsuario.ACTIVO)
                .toList();
    }

    private List<MentorRankingItem> rankear(List<Mentor> mentores) {
        record Fila(Mentor mentor, BigDecimal promedio, int total, double horas) {
        }

        List<Fila> filas = mentores.stream()
                .map(m -> new Fila(m, promedioDe(m), totalDe(m), insigniaService.actividad(m.getId()).horas()))
                .sorted(Comparator
                        .comparing((Fila f) -> f.promedio() == null ? BigDecimal.valueOf(-1) : f.promedio()).reversed()
                        .thenComparing(Comparator.comparingInt(Fila::total).reversed())
                        .thenComparing(Comparator.comparingDouble(Fila::horas).reversed())
                        .thenComparing(f -> f.mentor().getUsuario().getNombres()))
                .toList();

        return IntStream.range(0, filas.size()).mapToObj(i -> {
            Fila f = filas.get(i);
            Mentor m = f.mentor();
            Usuario u = m.getUsuario();
            return new MentorRankingItem(i + 1, m.getId(), u.getId(), u.getNombres(), apellidos(u), m.getEspecialidad(),
                    f.promedio(), f.total(), f.horas(), nombresDeCursos(m), insignias(m), fotoRepository.existsById(u.getId()));
        }).toList();
    }

    @Transactional(readOnly = true)
    public MentorPerfilPublico perfil(Integer idMentor) {
        Mentor m = mentorRepository.findById(idMentor)
                .filter(x -> x.getUsuario().getEstado() == EstadoUsuario.ACTIVO)
                .orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "El mentor no existe."));
        Usuario u = m.getUsuario();

        // El promedio se calcula con las calificaciones guardadas, no con una copia.
        CalificacionRepository.Resumen resumen = calificacionRepository.resumenDeMentor(m.getId());
        int total = resumen.getTotal() == null ? 0 : resumen.getTotal().intValue();
        BigDecimal promedio = total == 0 || resumen.getPromedio() == null ? null
                : BigDecimal.valueOf(resumen.getPromedio()).setScale(2, RoundingMode.HALF_UP);

        List<MentorPerfilPublico.Comentario> comentarios = calificacionRepository
                .ultimosComentarios(m.getId(), PageRequest.of(0, MAX_COMENTARIOS)).stream()
                .map(this::aComentario)
                .toList();
        InsigniaService.Actividad actividad = insigniaService.actividad(m.getId());

        return new MentorPerfilPublico(m.getId(), u.getId(), u.getNombres(), apellidos(u), m.getEspecialidad(),
                u.getDescripcion(), m.getBiografia(), nombresDeCursos(m), m.getFortalezas().stream().sorted().toList(),
                m.getAreas().stream().sorted().toList(), promedio, total, actividad.horas(), actividad.sesiones(),
                insignias(m), comentarios, fotoRepository.existsById(u.getId()), u.getFrase());
    }

    private MentorPerfilPublico.Comentario aComentario(Calificacion c) {
        String nombres = c.getSesion().getEstudiante().getUsuario().getNombres();
        String primerNombre = nombres == null ? "Estudiante" : nombres.trim().split("\\s+")[0];
        return new MentorPerfilPublico.Comentario(c.getEstrellas(), c.getComentario(), primerNombre,
                c.getFechaCreacion() == null ? null : c.getFechaCreacion().toLocalDate());
    }

    private static BigDecimal promedioDe(Mentor m) {
        return totalDe(m) == 0 || m.getReputacion() == null ? null : m.getReputacion();
    }

    private static int totalDe(Mentor m) {
        return m.getTotalCalificaciones() == null ? 0 : m.getTotalCalificaciones();
    }

    private static String apellidos(Usuario u) {
        return u.getApellidos() == null || "-".equals(u.getApellidos()) ? "" : u.getApellidos();
    }

    private static List<String> nombresDeCursos(Mentor m) {
        return m.getCursos().stream().map(Curso::getNombre).sorted().toList();
    }

    private static List<InsigniaResponse> insignias(Mentor m) {
        return m.getInsignias().stream()
                .sorted(Comparator.comparing(Insignia::getId))
                .map(i -> new InsigniaResponse(i.getCodigo(), i.getNombre(), i.getDescripcion()))
                .toList();
    }
}
