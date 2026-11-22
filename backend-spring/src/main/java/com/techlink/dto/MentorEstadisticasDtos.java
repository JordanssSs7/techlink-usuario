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

    // HU-27: la constancia de mentoría. Al llegar a horasMinimas horas dictadas el mentor es "elegible" y su constancia queda
    // PENDIENTE; un administrador la emite desde el panel (EMITIDA) y solo entonces se puede descargar (puedeDescargar).
    // estado: NO_ELEGIBLE, PENDIENTE o EMITIDA. mentor es el nombre de su perfil (el de su cuenta institucional).
    // cursoPrincipal es el curso en el que más horas dictó (o su especialidad); cursos es el detalle por curso, que la
    // constancia no imprime.
    public record Constancia(String mentor, String correo, double horasTotales, int sesionesCompletadas, LocalDate desde,
                             LocalDate hasta, List<CursoAtendido> cursos, LocalDateTime emitidaEn, String folio,
                             boolean elegible, String estado, boolean puedeDescargar, double horasMinimas, String cursoPrincipal,
                             String codigoVerificacion) {
    }

    // Lo que muestra la página pública de verificación: si el código corresponde a una constancia emitida por TechLink y sus
    // datos (nombre, curso, horas reconocidas y fecha de emisión). Si no es válido, solo valida = false.
    public record Verificacion(boolean valida, String mentor, String curso, double horas, String fechaEmision) {
    }

    public record CursoAtendido(String curso, int sesiones, double horas) {
    }
}
