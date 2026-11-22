package com.techlink.service;

import com.techlink.dto.MetaActualizadaResponse;
import com.techlink.dto.MetaRequest;
import com.techlink.dto.MetaResponse;
import com.techlink.dto.MetasMentorResponse;
import com.techlink.dto.MetasResponse;
import com.techlink.dto.ProgresoSemanaResponse;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;

// HU-11: metas académicas semanales. Las acuerda el estudiante con su mentor (el mentor se las pone a sus alumnos) o se las
// pone él mismo, y las marca al cumplirlas. El progreso de la semana sale de las metas cuya fecha de entrega cae en ella.
@Service
public class MetaService {

    static final int MIN_TITULO = 3;
    static final int MAX_TITULO = 150;
    // Un tope razonable de metas sin cumplir, para que la lista no se vuelva inmanejable ni se use para llenar la base.
    static final int MAX_PENDIENTES = 30;
    static final int MAX_DIAS_ADELANTE = 120;
    static final int MAX_PROXIMAS = 10;

    private final MetaRepository metaRepository;
    private final EstudianteRepository estudianteRepository;
    private final MentorRepository mentorRepository;
    private final CursoRepository cursoRepository;
    private final SesionRepository sesionRepository;
    private final Clock reloj;

    @Autowired
    public MetaService(MetaRepository metaRepository, EstudianteRepository estudianteRepository,
                       MentorRepository mentorRepository, CursoRepository cursoRepository,
                       SesionRepository sesionRepository) {
        this(metaRepository, estudianteRepository, mentorRepository, cursoRepository, sesionRepository,
                Clock.systemDefaultZone());
    }

    // El reloj se puede cambiar solo en las pruebas automaticas.
    MetaService(MetaRepository metaRepository, EstudianteRepository estudianteRepository,
                MentorRepository mentorRepository, CursoRepository cursoRepository,
                SesionRepository sesionRepository, Clock reloj) {
        this.metaRepository = metaRepository;
        this.estudianteRepository = estudianteRepository;
        this.mentorRepository = mentorRepository;
        this.cursoRepository = cursoRepository;
        this.sesionRepository = sesionRepository;
        this.reloj = reloj;
    }

    // El lunes de la semana a la que pertenece una fecha (las semanas van de lunes a domingo).
    public static LocalDate lunesDe(LocalDate fecha) {
        return fecha.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    // ---------------------------------------------------------------- estudiante

    // Metas de una semana (la actual si no se indica) ordenadas por fecha de entrega, con el progreso de esa semana
    // y las metas atrasadas de semanas anteriores.
    @Transactional(readOnly = true)
    public MetasResponse listar(Integer usuarioId, LocalDate semana) {
        estudiante(usuarioId);
        LocalDate hoy = hoy();
        LocalDate lunes = lunesDe(semana == null ? hoy : semana);
        LocalDate domingo = lunes.plusDays(6);
        List<Meta> deLaSemana = metaRepository.delEstudianteEntre(usuarioId, lunes, domingo);
        List<MetaResponse> metas = deLaSemana.stream().map(m -> aRespuesta(m, hoy, false)).toList();
        List<MetaResponse> atrasadas = metaRepository.atrasadasDe(usuarioId, lunes).stream()
                .map(m -> aRespuesta(m, hoy, false)).toList();
        List<MetaResponse> proximas = metaRepository.proximasDe(usuarioId, domingo, PageRequest.of(0, MAX_PROXIMAS)).stream()
                .map(m -> aRespuesta(m, hoy, false)).toList();
        return new MetasResponse(progreso(deLaSemana, lunes), metas, atrasadas, proximas);
    }

    // Marca o desmarca una meta. Solo el estudiante dueño puede hacerlo. Devuelve el progreso de su semana ya recalculado.
    @Transactional
    public MetaActualizadaResponse marcar(Integer usuarioId, Integer metaId, Boolean completada) {
        if (completada == null) {
            throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "Indica si la meta está cumplida (true) o pendiente (false).");
        }
        estudiante(usuarioId);
        Meta meta = metaRepository.findById(metaId)
                .filter(m -> m.getEstudiante().getUsuario().getId().equals(usuarioId))
                .orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "La meta no existe."));
        if (meta.isCompletada() != completada) {
            meta.setCompletada(completada);
            meta.setFechaCompletada(completada ? LocalDateTime.now(reloj) : null);
            metaRepository.save(meta);
        }
        LocalDate lunes = lunesDe(meta.getFechaEntrega());
        List<Meta> semana = metaRepository.delEstudianteEntre(usuarioId, lunes, lunes.plusDays(6));
        return new MetaActualizadaResponse(aRespuesta(meta, hoy(), false), progreso(semana, lunes));
    }

    // ---------------------------------------------------------------- estudiante y mentor

    // Crea una meta. El estudiante la crea para sí; el mentor, para uno de sus alumnos (alguien con quien tiene sesiones).
    @Transactional
    public MetaResponse crear(Integer usuarioId, String rol, MetaRequest solicitud) {
        String titulo = solicitud.titulo() == null ? "" : solicitud.titulo().trim().replaceAll("\\s+", " ");
        if (titulo.length() < MIN_TITULO) {
            throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "Escribe la meta con al menos " + MIN_TITULO + " caracteres.");
        }
        if (titulo.length() > MAX_TITULO) {
            throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "La meta no puede superar los " + MAX_TITULO + " caracteres.");
        }
        LocalDate hoy = hoy();
        LocalDate entrega = fecha(solicitud.fechaEntrega());
        if (entrega.isBefore(hoy)) {
            throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "La fecha de entrega no puede ser anterior a hoy.");
        }
        if (entrega.isAfter(hoy.plusDays(MAX_DIAS_ADELANTE))) {
            throw new ReglaNegocioException(HttpStatus.BAD_REQUEST,
                    "La fecha de entrega no puede pasar de " + MAX_DIAS_ADELANTE + " días desde hoy.");
        }
        Curso curso = null;
        if (solicitud.idCurso() != null) {
            curso = cursoRepository.findById(solicitud.idCurso()).filter(c -> Boolean.TRUE.equals(c.getActivo()))
                    .orElseThrow(() -> new ReglaNegocioException(HttpStatus.BAD_REQUEST, "El curso no existe en el catálogo."));
        }

        Estudiante alumno;
        Mentor mentor = null;
        if ("MENTOR".equals(rol)) {
            mentor = mentorRepository.findByUsuarioId(usuarioId)
                    .orElseThrow(() -> new ReglaNegocioException(HttpStatus.FORBIDDEN, "Completa tu perfil de mentor para poner metas."));
            if (solicitud.idUsuarioEstudiante() == null) {
                throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "Elige el estudiante al que le pones la meta.");
            }
            alumno = sesionRepository.estudiantesDeMentor(usuarioId).stream()
                    .filter(e -> e.getUsuario().getId().equals(solicitud.idUsuarioEstudiante()))
                    .findFirst()
                    .orElseThrow(() -> new ReglaNegocioException(HttpStatus.FORBIDDEN, "Solo puedes ponerles metas a tus estudiantes."));
        } else if ("ESTUDIANTE".equals(rol)) {
            alumno = estudiante(usuarioId);
        } else {
            throw new ReglaNegocioException(HttpStatus.FORBIDDEN, "Solo estudiantes y mentores pueden crear metas.");
        }
        if (metaRepository.contarPendientes(alumno.getUsuario().getId()) >= MAX_PENDIENTES) {
            throw new ReglaNegocioException(HttpStatus.CONFLICT,
                    "Ya hay " + MAX_PENDIENTES + " metas pendientes: cumple o elimina alguna antes de agregar otra.");
        }

        Meta meta = new Meta();
        meta.setEstudiante(alumno);
        meta.setMentor(mentor);
        meta.setCurso(curso);
        meta.setTitulo(titulo);
        meta.setFechaEntrega(entrega);
        metaRepository.save(meta);
        return aRespuesta(meta, hoy, "MENTOR".equals(rol));
    }

    // Elimina una meta. El estudiante borra las que puso él; el mentor, las que puso a sus alumnos.
    @Transactional
    public void eliminar(Integer usuarioId, String rol, Integer metaId) {
        Meta meta = metaRepository.findById(metaId).orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "La meta no existe."));
        boolean esMentorDeLaMeta = "MENTOR".equals(rol) && meta.getMentor() != null
                && meta.getMentor().getUsuario().getId().equals(usuarioId);
        boolean esEstudianteDeLaMeta = "ESTUDIANTE".equals(rol) && meta.getMentor() == null
                && meta.getEstudiante().getUsuario().getId().equals(usuarioId);
        if (!esMentorDeLaMeta && !esEstudianteDeLaMeta) {
            // Una meta acordada con el mentor no la borra el estudiante, y la de otra persona ni se revela que existe.
            boolean suya = meta.getEstudiante().getUsuario().getId().equals(usuarioId);
            throw suya
                    ? new ReglaNegocioException(HttpStatus.FORBIDDEN, "Esta meta la puso tu mentor: pídele que la quite.")
                    : new ReglaNegocioException(HttpStatus.NOT_FOUND, "La meta no existe.");
        }
        metaRepository.delete(meta);
    }

    // ---------------------------------------------------------------- mentor

    // Los alumnos del mentor (con sesiones confirmadas) y las metas que les puso.
    @Transactional(readOnly = true)
    public MetasMentorResponse delMentor(Integer usuarioId) {
        LocalDate hoy = hoy();
        List<MetasMentorResponse.EstudianteResumen> estudiantes = sesionRepository.estudiantesDeMentor(usuarioId).stream()
                .map(e -> new MetasMentorResponse.EstudianteResumen(e.getUsuario().getId(), nombre(e)))
                .sorted(Comparator.comparing(MetasMentorResponse.EstudianteResumen::nombre))
                .toList();
        List<MetaResponse> metas = metaRepository.asignadasPorMentor(usuarioId).stream().map(m -> aRespuesta(m, hoy, true)).toList();
        return new MetasMentorResponse(estudiantes, metas);
    }

    // ---------------------------------------------------------------- apoyo

    private Estudiante estudiante(Integer usuarioId) {
        return estudianteRepository.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new ReglaNegocioException(HttpStatus.FORBIDDEN, "Solo los estudiantes tienen metas."));
    }

    private LocalDate hoy() {
        return LocalDate.now(reloj);
    }

    private static LocalDate fecha(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "Indica la fecha de entrega (AAAA-MM-DD).");
        }
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException e) {
            throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "Fecha inválida: usa el formato AAAA-MM-DD.");
        }
    }

    static ProgresoSemanaResponse progreso(List<Meta> deLaSemana, LocalDate lunes) {
        int total = deLaSemana.size();
        int hechas = (int) deLaSemana.stream().filter(Meta::isCompletada).count();
        int porcentaje = total == 0 ? 0 : Math.round(100f * hechas / total);
        return new ProgresoSemanaResponse(lunes, lunes.plusDays(6), total, hechas, porcentaje);
    }

    private static String nombre(Estudiante e) {
        return e.getUsuario().getNombres() + " " + e.getUsuario().getApellidos();
    }

    private static MetaResponse aRespuesta(Meta m, LocalDate hoy, boolean paraMentor) {
        Mentor mentor = m.getMentor();
        return new MetaResponse(m.getId(), m.getTitulo(),
                m.getCurso() == null ? null : m.getCurso().getId(),
                m.getCurso() == null ? null : m.getCurso().getNombre(),
                mentor == null ? null : mentor.getId(),
                mentor == null ? null : mentor.getUsuario().getNombres() + " " + mentor.getUsuario().getApellidos(),
                paraMentor ? nombre(m.getEstudiante()) : null,
                paraMentor ? m.getEstudiante().getUsuario().getId() : null,
                m.getFechaEntrega(), m.isCompletada(), m.getFechaCompletada(),
                !m.isCompletada() && m.getFechaEntrega().isBefore(hoy),
                mentor == null);
    }
}
