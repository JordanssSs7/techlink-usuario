package com.techlink.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// HU-28 y HU-27: lo que ve el mentor en su panel de estadísticas y en su constancia de horas.
public final class MentorEstadisticasDtos {

    private MentorEstadisticasDtos() {
    }

    // HU-28: totales de siempre y el detalle mes a mes (de más antiguo a más reciente, con los meses sin actividad en cero).
    public record Estadisticas(Resumen resumen, List<Mes> meses) {
    }

    // horasTotales y sesionesCompletadas salen del mismo cálculo que las insignias y el perfil público (HU-15).
    // promedio es null si todavía no tiene calificaciones. proximas: sesiones confirmadas que aún no terminan.
    public record Resumen(double horasTotales, int sesionesCompletadas, BigDecimal promedio, int totalCalificaciones,
                          int sesionesProximas) {
    }

    // mes: "2026-11". promedio es null si ese mes no recibió calificaciones (se cuenta por el mes en que fue la sesión).
    public record Mes(String mes, int sesiones, double horas, BigDecimal promedio, int calificaciones) {
    }

    // HU-27: los datos de la constancia. Son los mismos que salen impresos en el PDF.
    // cursos: horas por curso atendido; las sesiones sin curso indicado van juntas al final.
    public record Constancia(String mentor, String correo, double horasTotales, int sesionesCompletadas, LocalDate desde,
                             LocalDate hasta, List<CursoAtendido> cursos, LocalDateTime emitidaEn, String folio,
                             boolean puedeEmitir) {
    }

    public record CursoAtendido(String curso, int sesiones, double horas) {
    }
}
