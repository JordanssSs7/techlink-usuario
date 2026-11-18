package com.techlink.service;

import com.techlink.dto.InsigniaResponse;
import com.techlink.dto.MentorPerfilPublico;
import com.techlink.dto.MentorRankingItem;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.Calificacion;
import com.techlink.model.Curso;
import com.techlink.model.EstadoUsuario;
import com.techlink.model.Insignia;
import com.techlink.model.Mentor;
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
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

// HU-14: perfil público de un mentor (cursos, promedio de estrellas, últimos comentarios) para decidir con quién agendar.
// HU-15: ranking de mentores con sus insignias.
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
        record Fila(Mentor mentor, BigDecimal promedio, int total, double horas) {
        }

        List<Fila> filas = mentorRepository.findAll().stream()
                .filter(m -> m.getUsuario().getEstado() == EstadoUsuario.ACTIVO)
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
                insignias(m), comentarios, fotoRepository.existsById(u.getId()));
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
