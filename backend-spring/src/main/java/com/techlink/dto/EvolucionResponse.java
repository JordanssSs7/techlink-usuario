package com.techlink.dto;

import java.time.LocalDate;
import java.util.List;

// HU-12: la evolución del estudiante por semana o por mes: horas de asesoría recibidas, autoevaluaciones y metas.
// antesDespues compara el promedio de sus autoevaluaciones antes y después de su primera sesión de mentoría.
public record EvolucionResponse(
        String agrupar,
        List<PeriodoEvolucionResponse> periodos,
        Resumen resumen,
        AntesDespues antesDespues
) {

    // variacion: cuánto cambió la autoevaluación entre el primer y el último periodo con datos (null si hay menos de dos).
    public record Resumen(double totalHoras, int totalSesiones, Double promedioAutoevaluacion, Double variacion,
                          int totalMetas, int metasCompletadas) {
    }

    // primeraSesion es null si todavía no tuvo ninguna sesión completada; antes y despues son null si no hay datos en ese lado.
    public record AntesDespues(LocalDate primeraSesion, Double antes, Double despues) {
    }
}
