package com.techlink.service;

import com.techlink.dto.MentorEstadisticasDtos.Constancia;
import com.techlink.dto.MentorEstadisticasDtos.CursoAtendido;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.Mentor;
import com.techlink.model.Sesion;
import com.techlink.model.Usuario;
import com.techlink.repository.MentorRepository;
import com.techlink.repository.SesionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// HU-27: constancia de horas de mentoría en PDF. Suma las horas de las sesiones completadas del mentor (el mismo cálculo
// que las insignias y el panel de estadísticas, HU-15 y HU-28) y las agrupa por curso atendido.
@Service
public class ConstanciaService {

    static final String SIN_CURSO = "Sin curso indicado";

    private final MentorRepository mentorRepository;
    private final SesionRepository sesionRepository;
    private final InsigniaService insigniaService;
    private final Clock reloj;

    @Autowired
    public ConstanciaService(MentorRepository mentorRepository, SesionRepository sesionRepository,
                             InsigniaService insigniaService) {
        this(mentorRepository, sesionRepository, insigniaService, Clock.systemDefaultZone());
    }

    // El reloj se puede cambiar solo en las pruebas automaticas.
    ConstanciaService(MentorRepository mentorRepository, SesionRepository sesionRepository,
                      InsigniaService insigniaService, Clock reloj) {
        this.mentorRepository = mentorRepository;
        this.sesionRepository = sesionRepository;
        this.insigniaService = insigniaService;
        this.reloj = reloj;
    }

    // Lo que dirá la constancia (para la vista previa). 404 si todavía no creó su perfil de mentor.
    // puedeEmitir es false mientras no tenga ninguna sesión completada.
    @Transactional(readOnly = true)
    public Constancia datos(Integer usuarioId) {
        Mentor mentor = mentorRepository.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "Todavía no tienes un perfil de mentor."));
        LocalDateTime ahora = LocalDateTime.now(reloj);

        // Curso -> {minutos, sesiones}; las sesiones sin curso indicado van aparte, al final.
        Map<String, long[]> porCurso = new LinkedHashMap<>();
        LocalDate desde = null;
        LocalDate hasta = null;
        for (Sesion s : sesionRepository.historialDeMentor(usuarioId)) {
            if (!EstadisticasMentorService.completada(s, ahora)) {
                continue;
            }
            String curso = s.getCurso() == null ? SIN_CURSO : s.getCurso().getNombre();
            long[] acumulado = porCurso.computeIfAbsent(curso, k -> new long[2]);
            acumulado[0] += EstadisticasMentorService.minutos(s);
            acumulado[1]++;
            desde = desde == null || s.getFecha().isBefore(desde) ? s.getFecha() : desde;
            hasta = hasta == null || s.getFecha().isAfter(hasta) ? s.getFecha() : hasta;
        }

        List<CursoAtendido> cursos = new ArrayList<>();
        porCurso.forEach((curso, v) -> cursos.add(new CursoAtendido(curso, (int) v[1], EstadisticasMentorService.horas(v[0]))));
        // Más horas primero; "Sin curso indicado" siempre al final.
        cursos.sort(Comparator.comparing((CursoAtendido c) -> SIN_CURSO.equals(c.curso()))
                .thenComparing(Comparator.comparingDouble(CursoAtendido::horas).reversed())
                .thenComparing(CursoAtendido::curso));

        InsigniaService.Actividad actividad = insigniaService.actividad(mentor.getId());
        Usuario u = mentor.getUsuario();
        String nombre = (u.getNombres() + " " + (u.getApellidos() == null || "-".equals(u.getApellidos()) ? "" : u.getApellidos())).trim();
        String folio = "TL-%04d-%s".formatted(mentor.getId(), ahora.format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        return new Constancia(nombre, u.getCorreoInstitucional(), actividad.horas(), actividad.sesiones(), desde, hasta,
                cursos, ahora, folio, actividad.sesiones() > 0);
    }

    // El PDF de la constancia. 409 si todavía no tiene ninguna sesión completada.
    @Transactional(readOnly = true)
    public byte[] pdf(Integer usuarioId) {
        Constancia constancia = datos(usuarioId);
        if (!constancia.puedeEmitir()) {
            throw new ReglaNegocioException(HttpStatus.CONFLICT,
                    "Todavía no tienes sesiones completadas para emitir una constancia.");
        }
        return ConstanciaPdf.generar(constancia);
    }
}
