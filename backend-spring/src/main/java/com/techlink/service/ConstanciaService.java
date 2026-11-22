package com.techlink.service;

import com.techlink.dto.MentorEstadisticasDtos.Constancia;
import com.techlink.dto.MentorEstadisticasDtos.CursoAtendido;
import com.techlink.dto.MentorEstadisticasDtos.Verificacion;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.ConstanciaMentor;
import com.techlink.model.Mentor;
import com.techlink.model.Sesion;
import com.techlink.model.Usuario;
import com.techlink.repository.ConstanciaMentorRepository;
import com.techlink.repository.MentorRepository;
import com.techlink.repository.SesionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// HU-27: constancia de mentoría en PDF. Suma las horas de las sesiones completadas del mentor (el mismo cálculo que las
// insignias y el panel de estadísticas, HU-15 y HU-28). Al llegar a las horas mínimas (50) la constancia queda PENDIENTE; un
// administrador la emite desde el panel y solo entonces el mentor puede descargarla.
@Service
public class ConstanciaService {

    static final String SIN_CURSO = "Sin curso indicado";

    private final MentorRepository mentorRepository;
    private final SesionRepository sesionRepository;
    private final InsigniaService insigniaService;
    private final ConstanciaMentorRepository constanciaRepository;
    private final double horasMinimas;
    private final String urlBase;
    private final Clock reloj;

    // La constancia está disponible al llegar a app.constancia.horas-minimas horas dictadas (50 por defecto).
    @Autowired
    public ConstanciaService(MentorRepository mentorRepository, SesionRepository sesionRepository,
                             InsigniaService insigniaService, ConstanciaMentorRepository constanciaRepository,
                             @org.springframework.beans.factory.annotation.Value("${app.constancia.horas-minimas:50}") double horasMinimas,
                             @org.springframework.beans.factory.annotation.Value("${app.publico.url-base:http://localhost:5173}") String urlBase) {
        this(mentorRepository, sesionRepository, insigniaService, constanciaRepository, horasMinimas, urlBase, Clock.systemDefaultZone());
    }

    // El reloj se puede cambiar solo en las pruebas automaticas.
    ConstanciaService(MentorRepository mentorRepository, SesionRepository sesionRepository,
                      InsigniaService insigniaService, ConstanciaMentorRepository constanciaRepository,
                      double horasMinimas, String urlBase, Clock reloj) {
        this.mentorRepository = mentorRepository;
        this.sesionRepository = sesionRepository;
        this.insigniaService = insigniaService;
        this.constanciaRepository = constanciaRepository;
        this.horasMinimas = horasMinimas;
        this.urlBase = urlBase;
        this.reloj = reloj;
    }

    // El estado de su constancia y lo que dirá. 404 si todavía no creó su perfil de mentor. Si ya llegó a las horas mínimas y
    // todavía no tiene constancia registrada, se registra como PENDIENTE para que el administrador la vea y la emita.
    @Transactional
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
        boolean elegible = actividad.horas() >= horasMinimas;
        ConstanciaMentor fila = constanciaRepository.findByIdMentor(mentor.getId()).orElse(null);
        if (fila == null && elegible) {
            fila = constanciaRepository.save(new ConstanciaMentor(mentor.getId(), BigDecimal.valueOf(actividad.horas())));
        }
        String estado = fila == null ? "NO_ELEGIBLE" : fila.getEstado().name();
        boolean emitida = fila != null && fila.getEstado() == ConstanciaMentor.Estado.EMITIDA;
        // Una constancia emitida siempre tiene código de verificación (el panel lo genera al emitirla; esto cubre las que se
        // emitieron antes de que existiera).
        if (emitida && (fila.getCodigoVerificacion() == null || fila.getCodigoVerificacion().isBlank())) {
            fila.setCodigoVerificacion(nuevoCodigo());
            fila = constanciaRepository.save(fila);
        }
        LocalDateTime fechaEmision = emitida && fila.getFechaEmision() != null ? fila.getFechaEmision() : ahora;
        String folio = fila != null && fila.getId() != null
                ? "TL-C%05d".formatted(fila.getId())
                : "TL-%04d-%s".formatted(mentor.getId(), ahora.format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        // El curso en el que más horas dictó (el primero de la lista, que ya viene por horas); si no hay, su especialidad.
        String principal = cursos.stream().map(CursoAtendido::curso).filter(n -> !SIN_CURSO.equals(n)).findFirst()
                .orElse(mentor.getEspecialidad() == null || mentor.getEspecialidad().isBlank() ? null : mentor.getEspecialidad());
        return new Constancia(nombre, u.getCorreoInstitucional(), actividad.horas(), actividad.sesiones(), desde, hasta,
                cursos, fechaEmision, folio, elegible, estado, emitida, horasMinimas, principal,
                emitida ? fila.getCodigoVerificacion() : null);
    }

    // HU-27: comprueba un código de verificación (el que va impreso en la constancia y en su QR). Es público: no pide sesión. Solo
    // dice que es válida si existe y ya fue emitida; los datos son los mismos que lleva la constancia.
    @Transactional(readOnly = true)
    public Verificacion verificar(String codigo) {
        String limpio = codigo == null ? "" : codigo.trim().toUpperCase();
        ConstanciaMentor fila = limpio.isEmpty() ? null : constanciaRepository.findByCodigoVerificacion(limpio).orElse(null);
        if (fila == null || fila.getEstado() != ConstanciaMentor.Estado.EMITIDA) {
            return new Verificacion(false, null, null, 0, null);
        }
        Mentor mentor = mentorRepository.findById(fila.getIdMentor()).orElse(null);
        if (mentor == null) {
            return new Verificacion(false, null, null, 0, null);
        }
        Constancia datos = datos(mentor.getUsuario().getId());
        return new Verificacion(true, datos.mentor(), datos.cursoPrincipal(), fila.getHorasAlCumplir().doubleValue(),
                fila.getFechaEmision() == null ? null : fila.getFechaEmision().toLocalDate().toString());
    }

    // 10 caracteres hexadecimales en mayúsculas (40 bits al azar): no se pueden adivinar ni confundir.
    private static String nuevoCodigo() {
        byte[] bytes = new byte[5];
        new java.security.SecureRandom().nextBytes(bytes);
        return java.util.HexFormat.of().withUpperCase().formatHex(bytes);
    }

    // La dirección de la página pública que comprueba esta constancia (la del código QR).
    String urlDeVerificacion(String codigo) {
        return urlBase.replaceAll("/+$", "") + "/verificar/" + codigo;
    }

    // Registra como PENDIENTE a todos los mentores que ya llegaron a las horas mínimas y todavía no tienen constancia. Lo usa el
    // proceso programado para que el administrador los vea en el panel aunque el mentor no haya entrado a su pantalla.
    // Devuelve cuántos nuevos registró.
    @Transactional
    public int registrarElegibles() {
        int nuevos = 0;
        for (Mentor mentor : mentorRepository.findAll()) {
            if (constanciaRepository.findByIdMentor(mentor.getId()).isPresent()) {
                continue;
            }
            double horas = insigniaService.actividad(mentor.getId()).horas();
            if (horas >= horasMinimas) {
                constanciaRepository.save(new ConstanciaMentor(mentor.getId(), BigDecimal.valueOf(horas)));
                nuevos++;
            }
        }
        return nuevos;
    }

    // El PDF de la constancia. 409 mientras no esté emitida: no llega a las horas mínimas, o ya llegó y falta que un
    // administrador la emita.
    @Transactional
    public byte[] pdf(Integer usuarioId) {
        Constancia constancia = datos(usuarioId);
        if (!constancia.puedeDescargar()) {
            throw new ReglaNegocioException(HttpStatus.CONFLICT, constancia.elegible()
                    ? "Ya cumpliste las horas: un administrador debe emitir tu constancia. Te avisaremos cuando esté lista."
                    : "Tu constancia se entrega al completar %s horas de mentoría (llevas %s)."
                            .formatted(ConstanciaPdf.horas(horasMinimas), ConstanciaPdf.horas(constancia.horasTotales())));
        }
        return ConstanciaPdf.generar(constancia, urlDeVerificacion(constancia.codigoVerificacion() == null ? "" : constancia.codigoVerificacion()));
    }
}
