package com.techlink.service;

import com.techlink.dto.ReporteRequest;
import com.techlink.dto.ReporteResponse;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.Reporte;
import com.techlink.model.Sesion;
import com.techlink.repository.ReporteRepository;
import com.techlink.repository.SesionRepository;
import com.techlink.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// HU-29: un estudiante o mentor reporta a otro usuario o una sesión por conducta inadecuada. El reporte queda
// PENDIENTE hasta que un administrador lo revise (HU-52).
@Service
public class ReporteService {

    static final int MIN_DESCRIPCION = 10;
    static final int MAX_DESCRIPCION = 500;

    private final ReporteRepository reporteRepository;
    private final SesionRepository sesionRepository;
    private final UsuarioRepository usuarioRepository;

    public ReporteService(ReporteRepository reporteRepository,
                          SesionRepository sesionRepository,
                          UsuarioRepository usuarioRepository) {
        this.reporteRepository = reporteRepository;
        this.sesionRepository = sesionRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public ReporteResponse reportar(Integer usuarioId, ReporteRequest solicitud) {
        Reporte.Tipo tipo = tipo(solicitud.tipo());
        Reporte.Motivo motivo = motivo(solicitud.motivo());
        String descripcion = solicitud.descripcion() == null ? "" : solicitud.descripcion().trim();
        if (descripcion.length() < MIN_DESCRIPCION) {
            throw new ReglaNegocioException(HttpStatus.BAD_REQUEST,
                    "Cuéntanos qué pasó con al menos " + MIN_DESCRIPCION + " caracteres.");
        }
        if (descripcion.length() > MAX_DESCRIPCION) {
            throw new ReglaNegocioException(HttpStatus.BAD_REQUEST,
                    "La descripción no puede superar los " + MAX_DESCRIPCION + " caracteres.");
        }

        Integer reportado;
        Integer idSesion = null;
        if (tipo == Reporte.Tipo.SESION) {
            if (solicitud.idSesion() == null) {
                throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "Indica la sesión que quieres reportar.");
            }
            Sesion sesion = sesionRepository.findById(solicitud.idSesion())
                    .filter(s -> participa(s, usuarioId))
                    .orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "La sesión no existe."));
            idSesion = sesion.getId();
            // Se reporta a la otra persona de la sesión.
            Integer usuarioEstudiante = sesion.getEstudiante().getUsuario().getId();
            reportado = usuarioEstudiante.equals(usuarioId) ? sesion.getMentor().getUsuario().getId() : usuarioEstudiante;
        } else {
            if (solicitud.idUsuario() == null) {
                throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "Indica el usuario que quieres reportar.");
            }
            if (solicitud.idUsuario().equals(usuarioId)) {
                throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "No puedes reportarte a ti mismo.");
            }
            if (!usuarioRepository.existsById(solicitud.idUsuario())) {
                throw new ReglaNegocioException(HttpStatus.NOT_FOUND, "El usuario no existe.");
            }
            reportado = solicitud.idUsuario();
        }

        if (reporteRepository.existsByIdReportanteAndIdUsuarioReportadoAndIdSesionAndEstado(
                usuarioId, reportado, idSesion, Reporte.Estado.PENDIENTE)) {
            throw new ReglaNegocioException(HttpStatus.CONFLICT,
                    "Ya enviaste un reporte sobre esto y todavía lo está revisando un administrador.");
        }

        Reporte reporte = new Reporte();
        reporte.setIdReportante(usuarioId);
        reporte.setTipo(tipo);
        reporte.setIdUsuarioReportado(reportado);
        reporte.setIdSesion(idSesion);
        reporte.setMotivo(motivo);
        reporte.setDescripcion(descripcion);
        reporteRepository.save(reporte);
        return new ReporteResponse(reporte.getId(), tipo.name(), reporte.getEstado().name(),
                "Recibimos tu reporte. Un administrador lo revisará.");
    }

    private static boolean participa(Sesion sesion, Integer usuarioId) {
        return sesion.getEstudiante().getUsuario().getId().equals(usuarioId)
                || sesion.getMentor().getUsuario().getId().equals(usuarioId);
    }

    private static Reporte.Tipo tipo(String valor) {
        try {
            return Reporte.Tipo.valueOf(valor == null ? "" : valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "El tipo de reporte debe ser USUARIO o SESION.");
        }
    }

    private static Reporte.Motivo motivo(String valor) {
        try {
            return Reporte.Motivo.valueOf(valor == null ? "" : valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "Elige un motivo válido para el reporte.");
        }
    }
}
