package com.techlink.service;

import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.Charla;
import com.techlink.model.Sesion;
import com.techlink.repository.CharlaInscripcionRepository;
import com.techlink.repository.CharlaRepository;
import com.techlink.repository.SesionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

// HU-08: quién puede entrar a la sala virtual de una sesión de mentoría o de una charla, y cuándo.
//  - SESION: solo el estudiante y el mentor de esa sesión, mientras siga CONFIRMADA (no cancelada).
//  - CHARLA: solo los estudiantes inscritos, mientras la charla esté activa.
// La sala abre unos minutos antes del inicio y cierra un rato después del final (app.sala.minutos-antes y
// app.sala.minutos-despues). Las salas de charla admiten hasta app.sala.max-participantes personas: la videollamada es
// directa entre participantes y con muchos se satura.
@Service
public class SalaAccesoService {

    // Quien da la charla: entra con el enlace secreto, sin cuenta. Su id en la sala es el de la charla en negativo.
    public static final String ROL_INVITADO = "INVITADO";

    public enum Tipo {
        SESION,
        CHARLA;

        // "sesion", "SESION" o "Sesion" -> SESION. 404 si no es un tipo de sala.
        public static Tipo de(String texto) {
            try {
                return valueOf(texto == null ? "" : texto.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new ReglaNegocioException(HttpStatus.NOT_FOUND, "Esa sala no existe.");
            }
        }
    }

    // Una sala a la que el usuario sí pertenece. abre y cierra delimitan cuándo se puede entrar.
    public record Sala(Tipo tipo, Integer id, String titulo, LocalDateTime abre, LocalDateTime cierra, int maxParticipantes) {

        public boolean abiertaEn(LocalDateTime ahora) {
            return !ahora.isBefore(abre) && ahora.isBefore(cierra);
        }

        // Clave de la sala en el registro y en el historial: "SESION-12".
        public String clave() {
            return tipo + "-" + id;
        }

        // Por qué no se puede entrar ahora (null si está abierta).
        public String motivoCerrada(LocalDateTime ahora) {
            if (abiertaEn(ahora)) {
                return null;
            }
            if (ahora.isBefore(abre)) {
                return "La sala se abre el %s a las %s.".formatted(abre.format(FECHA), abre.format(HORA));
            }
            return "La sala ya cerró.";
        }
    }

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final SesionRepository sesionRepository;
    private final CharlaRepository charlaRepository;
    private final CharlaInscripcionRepository inscripcionRepository;
    private final int minutosAntes;
    private final int minutosDespues;
    private final int maxParticipantesCharla;
    private final Clock reloj;

    @Autowired
    public SalaAccesoService(SesionRepository sesionRepository,
                             CharlaRepository charlaRepository,
                             CharlaInscripcionRepository inscripcionRepository,
                             @Value("${app.sala.minutos-antes:30}") int minutosAntes,
                             @Value("${app.sala.minutos-despues:60}") int minutosDespues,
                             @Value("${app.sala.max-participantes:8}") int maxParticipantesCharla) {
        this(sesionRepository, charlaRepository, inscripcionRepository, minutosAntes, minutosDespues,
                maxParticipantesCharla, Clock.systemDefaultZone());
    }

    // El reloj se puede cambiar solo en las pruebas automaticas.
    SalaAccesoService(SesionRepository sesionRepository, CharlaRepository charlaRepository,
                      CharlaInscripcionRepository inscripcionRepository, int minutosAntes, int minutosDespues,
                      int maxParticipantesCharla, Clock reloj) {
        this.sesionRepository = sesionRepository;
        this.charlaRepository = charlaRepository;
        this.inscripcionRepository = inscripcionRepository;
        this.minutosAntes = minutosAntes;
        this.minutosDespues = minutosDespues;
        this.maxParticipantesCharla = maxParticipantesCharla;
        this.reloj = reloj;
    }

    public LocalDateTime ahora() {
        return LocalDateTime.now(reloj);
    }

    // La sala, si el usuario pertenece a ella (403 si no; 404 si no existe; 409 si la sesión se canceló o la charla se
    // desactivó). No revisa el horario: eso lo dice Sala.abiertaEn().
    @Transactional(readOnly = true)
    public Sala autorizar(Integer usuarioId, Tipo tipo, Integer id) {
        if (usuarioId != null && usuarioId < 0) {
            return invitado(usuarioId, tipo, id);
        }
        return tipo == Tipo.SESION ? sesion(usuarioId, id) : charla(usuarioId, id);
    }

    // La sala de la charla del invitado que trae ese enlace secreto. 404 si el enlace no corresponde a ninguna charla.
    @Transactional(readOnly = true)
    public Sala autorizarInvitado(String token) {
        Charla c = token == null || token.isBlank() ? null : charlaRepository.findByTokenInvitado(token.trim()).orElse(null);
        if (c == null) {
            throw new ReglaNegocioException(HttpStatus.NOT_FOUND, "Este enlace de invitado no es válido.");
        }
        return invitado(-c.getId(), Tipo.CHARLA, c.getId());
    }

    private Sala invitado(Integer usuarioId, Tipo tipo, Integer id) {
        if (tipo != Tipo.CHARLA || !id.equals(-usuarioId)) {
            throw new ReglaNegocioException(HttpStatus.FORBIDDEN, "No participas en esta sala.");
        }
        Charla c = charlaRepository.findById(id)
                .orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "La charla no existe."));
        if (!c.isActivo()) {
            throw new ReglaNegocioException(HttpStatus.CONFLICT, "La charla fue desactivada.");
        }
        return salaDe(c);
    }

    private Sala sesion(Integer usuarioId, Integer id) {
        Sesion s = sesionRepository.findById(id)
                .orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "La sesión no existe."));
        var estudiante = s.getEstudiante().getUsuario();
        var mentor = s.getMentor().getUsuario();
        boolean esEstudiante = estudiante.getId().equals(usuarioId);
        boolean esMentor = mentor.getId().equals(usuarioId);
        if (!esEstudiante && !esMentor) {
            throw new ReglaNegocioException(HttpStatus.FORBIDDEN, "No participas en esta sesión.");
        }
        if (s.getEstado() != Sesion.EstadoSesion.CONFIRMADA) {
            throw new ReglaNegocioException(HttpStatus.CONFLICT, "La sesión fue cancelada.");
        }
        var otro = esEstudiante ? mentor : estudiante;
        String titulo = "Sesión con " + (otro.getNombres() + " " + (otro.getApellidos() == null || "-".equals(otro.getApellidos()) ? "" : otro.getApellidos())).trim();
        if (s.getCurso() != null) {
            titulo += " · " + s.getCurso().getNombre();
        }
        LocalDateTime inicio = LocalDateTime.of(s.getFecha(), s.getHoraInicio());
        LocalDateTime fin = LocalDateTime.of(s.getFecha(), s.getHoraFin());
        return new Sala(Tipo.SESION, id, titulo, inicio.minusMinutes(minutosAntes), fin.plusMinutes(minutosDespues), 2);
    }

    private Sala charla(Integer usuarioId, Integer id) {
        Charla c = charlaRepository.findById(id)
                .orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "La charla no existe."));
        if (!inscripcionRepository.existsByIdCharlaAndIdUsuario(id, usuarioId)) {
            throw new ReglaNegocioException(HttpStatus.FORBIDDEN, "Inscríbete a la charla para entrar a su sala.");
        }
        if (!c.isActivo()) {
            throw new ReglaNegocioException(HttpStatus.CONFLICT, "La charla fue desactivada.");
        }
        return salaDe(c);
    }

    private Sala salaDe(Charla c) {
        Integer id = c.getId();
        LocalDateTime inicio = LocalDateTime.of(c.getFecha(), c.getHoraInicio());
        LocalDateTime fin = LocalDateTime.of(c.getFecha(), c.getHoraFin());
        return new Sala(Tipo.CHARLA, id, c.getNombre(), inicio.minusMinutes(minutosAntes), fin.plusMinutes(minutosDespues),
                maxParticipantesCharla);
    }
}
