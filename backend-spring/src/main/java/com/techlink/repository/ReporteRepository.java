package com.techlink.repository;

import com.techlink.model.Reporte;
import org.springframework.data.jpa.repository.JpaRepository;

// HU-29: reportes de usuarios y sesiones.
public interface ReporteRepository extends JpaRepository<Reporte, Integer> {

    // Evita que la misma persona repita un reporte que todavía no se revisó (idSesion nulo = reporte de usuario).
    boolean existsByIdReportanteAndIdUsuarioReportadoAndIdSesionAndEstado(
            Integer idReportante, Integer idUsuarioReportado, Integer idSesion, Reporte.Estado estado);

    // Lo mismo para las consultas y las respuestas del foro.
    boolean existsByIdReportanteAndIdConsultaAndEstado(Integer idReportante, Integer idConsulta, Reporte.Estado estado);

    boolean existsByIdReportanteAndIdRespuestaAndEstado(Integer idReportante, Integer idRespuesta, Reporte.Estado estado);
}
