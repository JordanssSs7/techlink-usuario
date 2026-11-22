package com.techlink.service;

import com.techlink.dto.MentorEstadisticasDtos.Estadisticas;
import com.techlink.dto.MentorEstadisticasDtos.Mes;
import com.techlink.dto.MentorEstadisticasDtos.Resumen;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.Mentor;
import com.techlink.model.Sesion;
import com.techlink.repository.CalificacionRepository;
import com.techlink.repository.MentorRepository;
import com.techlink.repository.SesionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// HU-28: panel de estadísticas del mentor: sesiones, horas y promedio de estrellas mes a mes (para el gráfico de evolución).
// Usa el mismo cálculo que las insignias y el perfil público (HU-15): solo cuentan las sesiones CONFIRMADAS cuya hora de fin
// ya pasó, y las calificaciones que el administrador ocultó (HU-53) no entran en el promedio.
@Service
public class EstadisticasMentorService {

    static final int MESES_PREDETERMINADO = 12;
    static final int MESES_MAXIMO = 24;

    private final MentorRepository mentorRepository;
    private final SesionRepository sesionRepository;
    private final CalificacionRepository calificacionRepository;
    private final InsigniaService insigniaService;
    private final Clock reloj;

    @Autowired
    public EstadisticasMentorService(MentorRepository mentorRepository, SesionRepository sesionRepository,
                                     CalificacionRepository calificacionRepository, InsigniaService insigniaService) {
        this(mentorRepository, sesionRepository, calificacionRepository, insigniaService, Clock.systemDefaultZone());
    }

    // El reloj se puede cambiar solo en las pruebas automaticas.
    EstadisticasMentorService(MentorRepository mentorRepository, SesionRepository sesionRepository,
                              CalificacionRepository calificacionRepository, InsigniaService insigniaService, Clock reloj) {
        this.mentorRepository = mentorRepository;
        this.sesionRepository = sesionRepository;
        this.calificacionRepository = calificacionRepository;
        this.insigniaService = insigniaService;
        this.reloj = reloj;
    }

    // Estadísticas del mentor dueño del token: totales y los últimos "meses" meses (1 a 24; 12 si no se indica).
    // 404 si todavía no creó su perfil de mentor.
    @Transactional(readOnly = true)
    public Estadisticas estadisticas(Integer usuarioId, Integer meses) {
        Mentor mentor = mentor(usuarioId);
        LocalDateTime ahora = LocalDateTime.now(reloj);
        int cantidad = meses == null ? MESES_PREDETERMINADO : Math.max(1, Math.min(meses, MESES_MAXIMO));
        YearMonth ultimo = YearMonth.from(ahora);

        // Todos los meses de la ventana, en cero, de más antiguo a más reciente.
        Map<YearMonth, long[]> minutosYSesiones = new HashMap<>();
        Map<YearMonth, int[]> estrellas = new HashMap<>(); // {suma, cantidad}
        List<YearMonth> ventana = new ArrayList<>();
        for (int i = cantidad - 1; i >= 0; i--) {
            YearMonth m = ultimo.minusMonths(i);
            ventana.add(m);
            minutosYSesiones.put(m, new long[2]);
            estrellas.put(m, new int[2]);
        }

        int proximas = 0;
        for (Sesion s : sesionRepository.historialDeMentor(usuarioId)) {
            if (s.getEstado() != Sesion.EstadoSesion.CONFIRMADA) {
                continue;
            }
            if (!completada(s, ahora)) {
                proximas++;
                continue;
            }
            long[] mes = minutosYSesiones.get(YearMonth.from(s.getFecha()));
            if (mes != null) {
                mes[0] += minutos(s);
                mes[1]++;
            }
        }
        for (Object[] fila : calificacionRepository.fechasYEstrellasDeMentor(mentor.getId())) {
            int[] mes = estrellas.get(YearMonth.from((LocalDate) fila[0]));
            if (mes != null) {
                mes[0] += ((Number) fila[1]).intValue();
                mes[1]++;
            }
        }

        List<Mes> detalle = new ArrayList<>();
        for (YearMonth m : ventana) {
            long[] ms = minutosYSesiones.get(m);
            int[] es = estrellas.get(m);
            BigDecimal promedio = es[1] == 0 ? null
                    : BigDecimal.valueOf(es[0]).divide(BigDecimal.valueOf(es[1]), 2, RoundingMode.HALF_UP);
            detalle.add(new Mes(m.toString(), (int) ms[1], horas(ms[0]), promedio, es[1]));
        }

        // Totales de siempre: mismas fuentes que el perfil público y las insignias.
        InsigniaService.Actividad actividad = insigniaService.actividad(mentor.getId());
        CalificacionRepository.Resumen resumen = calificacionRepository.resumenDeMentor(mentor.getId());
        int total = resumen.getTotal() == null ? 0 : resumen.getTotal().intValue();
        BigDecimal promedio = total == 0 || resumen.getPromedio() == null ? null
                : BigDecimal.valueOf(resumen.getPromedio()).setScale(2, RoundingMode.HALF_UP);
        return new Estadisticas(new Resumen(actividad.horas(), actividad.sesiones(), promedio, total, proximas), detalle);
    }

    private Mentor mentor(Integer usuarioId) {
        return mentorRepository.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "Todavía no tienes un perfil de mentor."));
    }

    // Una sesión completada es la que está CONFIRMADA y cuya hora de fin ya pasó (igual que las insignias, HU-15).
    static boolean completada(Sesion sesion, LocalDateTime ahora) {
        return sesion.getEstado() == Sesion.EstadoSesion.CONFIRMADA
                && !LocalDateTime.of(sesion.getFecha(), sesion.getHoraFin()).isAfter(ahora);
    }

    static long minutos(Sesion sesion) {
        return Duration.between(sesion.getHoraInicio(), sesion.getHoraFin()).toMinutes();
    }

    // Minutos a horas con dos decimales, como en las insignias.
    static double horas(long minutos) {
        return Math.round(minutos / 60.0 * 100) / 100.0;
    }
}
