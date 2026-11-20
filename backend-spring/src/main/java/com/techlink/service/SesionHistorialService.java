package com.techlink.service;

import com.techlink.dto.SesionHistorialResponse;
import com.techlink.exception.SesionException;
import com.techlink.model.Calificacion;
import com.techlink.model.Sesion;
import com.techlink.model.SesionNota;
import com.techlink.model.Usuario;
import com.techlink.repository.CalificacionRepository;
import com.techlink.repository.RecursoRepository;
import com.techlink.repository.SesionNotaRepository;
import com.techlink.repository.SesionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// HU-20: historial de sesiones del usuario (estudiante o mentor), con filtro por estado.
// Una sesión "completada" no tiene estado propio: es una confirmada cuya hora de fin ya pasó.
@Service
public class SesionHistorialService {

    public static final String PROGRAMADA = "PROGRAMADA";
    public static final String COMPLETADA = "COMPLETADA";
    public static final String CANCELADA = "CANCELADA";

    private final SesionRepository sesionRepository;
    private final CalificacionRepository calificacionRepository;
    private final SesionNotaRepository notaRepository;
    private final RecursoRepository recursoRepository;
    private final Clock reloj;

    @Autowired
    public SesionHistorialService(SesionRepository sesionRepository,
                                  CalificacionRepository calificacionRepository,
                                  SesionNotaRepository notaRepository,
                                  RecursoRepository recursoRepository) {
        this(sesionRepository, calificacionRepository, notaRepository, recursoRepository, Clock.systemDefaultZone());
    }

    // El reloj se puede cambiar solo en las pruebas automaticas.
    SesionHistorialService(SesionRepository sesionRepository,
                           CalificacionRepository calificacionRepository,
                           SesionNotaRepository notaRepository,
                           RecursoRepository recursoRepository,
                           Clock reloj) {
        this.sesionRepository = sesionRepository;
        this.calificacionRepository = calificacionRepository;
        this.notaRepository = notaRepository;
        this.recursoRepository = recursoRepository;
        this.reloj = reloj;
    }

    // rol: "ESTUDIANTE" o "MENTOR". estado: PROGRAMADA, COMPLETADA, CANCELADA o vacío para todas.
    @Transactional(readOnly = true)
    public List<SesionHistorialResponse> listar(Integer usuarioId, String rol, String estado) {
        String filtro = estado == null || estado.isBlank() ? null : estado.trim().toUpperCase(Locale.ROOT);
        if (filtro != null && !List.of(PROGRAMADA, COMPLETADA, CANCELADA).contains(filtro)) {
            throw new SesionException(HttpStatus.BAD_REQUEST,
                    "El estado debe ser PROGRAMADA, COMPLETADA o CANCELADA.");
        }

        boolean esMentor = "MENTOR".equals(rol);
        List<Sesion> sesiones = esMentor
                ? sesionRepository.historialDeMentor(usuarioId)
                : sesionRepository.historialDeEstudiante(usuarioId);
        if (sesiones.isEmpty()) {
            return List.of();
        }

        List<Integer> ids = sesiones.stream().map(Sesion::getId).toList();
        Map<Integer, Calificacion> calificaciones = calificacionRepository.buscarPorSesiones(ids).stream()
                .collect(Collectors.toMap(c -> c.getSesion().getId(), Function.identity()));
        Map<Integer, SesionNota> notas = notaRepository.buscarPorSesiones(ids).stream()
                .collect(Collectors.toMap(n -> n.getSesion().getId(), Function.identity()));

        Map<Integer, Integer> recursos = new HashMap<>();
        for (Object[] fila : recursoRepository.contarPorSesiones(ids, !esMentor)) {
            recursos.put((Integer) fila[0], ((Number) fila[1]).intValue());
        }

        LocalDateTime ahora = LocalDateTime.now(reloj);
        return sesiones.stream()
                .filter(s -> filtro == null || filtro.equals(estadoVisible(s, ahora)))
                .map(s -> convertir(s, esMentor, ahora, calificaciones.get(s.getId()), notas.get(s.getId()),
                        recursos.getOrDefault(s.getId(), 0)))
                .toList();
    }

    static String estadoVisible(Sesion sesion, LocalDateTime ahora) {
        if (sesion.getEstado() == Sesion.EstadoSesion.CANCELADA) {
            return CANCELADA;
        }
        return LocalDateTime.of(sesion.getFecha(), sesion.getHoraFin()).isAfter(ahora) ? PROGRAMADA : COMPLETADA;
    }

    private SesionHistorialResponse convertir(Sesion s, boolean esMentor, LocalDateTime ahora,
                                              Calificacion calificacion, SesionNota nota, int recursos) {
        String estado = estadoVisible(s, ahora);
        boolean completada = COMPLETADA.equals(estado);
        boolean programada = PROGRAMADA.equals(estado);

        Usuario otro = esMentor ? s.getEstudiante().getUsuario() : s.getMentor().getUsuario();
        String contraparte = (otro.getNombres() + " " + (otro.getApellidos() == null || "-".equals(otro.getApellidos())
                ? "" : otro.getApellidos())).trim();

        // Cancelar o reprogramar: solo el estudiante, con 2 horas de anticipación como mínimo.
        boolean puedeModificar = !esMentor && programada && !ahora.isAfter(
                LocalDateTime.of(s.getFecha(), s.getHoraInicio()).minus(SesionCicloService.ANTICIPACION_MINIMA));
        // Calificar: solo el estudiante, cuando la sesión terminó y aún no la calificó.
        boolean puedeCalificar = !esMentor && completada && calificacion == null;
        // Notas: solo el mentor, desde que empieza la sesión hasta 24 horas después de que termina.
        boolean puedeEditarNota = esMentor && !CANCELADA.equals(estado)
                && !ahora.isBefore(LocalDateTime.of(s.getFecha(), s.getHoraInicio()))
                && SesionNotaService.puedeEditar(s, ahora);

        return new SesionHistorialResponse(
                s.getId(), s.getFecha(), s.getHoraInicio(), s.getHoraFin(), estado,
                s.getMentor().getId(),
                s.getCurso() == null ? null : s.getCurso().getNombre(),
                contraparte,
                CANCELADA.equals(estado) ? null : s.getEnlaceReunion(),
                calificacion == null ? null : calificacion.getEstrellas(),
                calificacion == null ? null : calificacion.getComentario(),
                nota == null ? null : nota.getContenido(),
                puedeCalificar, puedeModificar, puedeEditarNota, recursos);
    }
}
