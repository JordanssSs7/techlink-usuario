package com.techlink.service;

import com.techlink.dto.ReporteRequest;
import com.techlink.dto.ReporteResponse;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.Consulta;
import com.techlink.model.Reporte;
import com.techlink.model.Respuesta;
import com.techlink.model.Sesion;
import com.techlink.repository.ConsultaRepository;
import com.techlink.repository.ReporteRepository;
import com.techlink.repository.RespuestaRepository;
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

    private final ConsultaRepository consultaRepository;
    private final RespuestaRepository respuestaRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public ReporteService(ReporteRepository reporteRepository,
                          SesionRepository sesionRepository,
                          UsuarioRepository usuarioRepository,
                          ConsultaRepository consultaRepository,
                          RespuestaRepository respuestaRepository) {
        this.reporteRepository = reporteRepository;
        this.sesionRepository = sesionRepository;
        this.usuarioRepository = usuarioRepository;
        this.consultaRepository = consultaRepository;
        this.respuestaRepository = respuestaRepository;
    }

    // Sin foro (solo reportes de usuarios y sesiones): lo usan las pruebas automaticas.
    public ReporteService(ReporteRepository reporteRepository, SesionRepository sesionRepository,
                          UsuarioRepository usuarioRepository) {
        this(reporteRepository, sesionRepository, usuarioRepository, null, null);
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
        Integer idConsulta = null;
        Integer idRespuesta = null;
        String contenidoReportado = null;
        if (tipo == Reporte.Tipo.CONSULTA) {
            if (solicitud.idConsulta() == null || consultaRepository == null) {
                throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "Indica la consulta que quieres reportar.");
            }
            Consulta consulta = consultaRepository.findByIdAndOcultoFalse(solicitud.idConsulta())
                    .orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "La consulta no existe."));
            if (consulta.getAutor().getId().equals(usuarioId)) {
                throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "No puedes reportar tu propia consulta.");
            }
            idConsulta = consulta.getId();
            reportado = consulta.getAutor().getId();
            contenidoReportado = recortar(consulta.getTitulo() + ": " + consulta.getContenido());
        } else if (tipo == Reporte.Tipo.RESPUESTA) {
            if (solicitud.idRespuesta() == null || respuestaRepository == null) {
                throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "Indica la respuesta que quieres reportar.");
            }
            Respuesta respuesta = respuestaRepository.findById(solicitud.idRespuesta())
                    .filter(r -> !r.isOculto())
                    .orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "La respuesta no existe."));
            if (respuesta.getAutor().getId().equals(usuarioId)) {
                throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "No puedes reportar tu propia respuesta.");
            }
            idRespuesta = respuesta.getId();
            reportado = respuesta.getAutor().getId();
            contenidoReportado = recortar(respuesta.getContenido());
        } else if (tipo == Reporte.Tipo.SESION) {
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

        boolean repetido = switch (tipo) {
            case CONSULTA -> reporteRepository.existsByIdReportanteAndIdConsultaAndEstado(usuarioId, idConsulta, Reporte.Estado.PENDIENTE);
            case RESPUESTA -> reporteRepository.existsByIdReportanteAndIdRespuestaAndEstado(usuarioId, idRespuesta, Reporte.Estado.PENDIENTE);
            default -> reporteRepository.existsByIdReportanteAndIdUsuarioReportadoAndIdSesionAndEstado(
                    usuarioId, reportado, idSesion, Reporte.Estado.PENDIENTE);
        };
        if (repetido) {
            throw new ReglaNegocioException(HttpStatus.CONFLICT,
                    "Ya enviaste un reporte sobre esto y todavía lo está revisando un administrador.");
        }

        Reporte reporte = new Reporte();
        reporte.setIdReportante(usuarioId);
        reporte.setTipo(tipo);
        reporte.setIdUsuarioReportado(reportado);
        reporte.setIdSesion(idSesion);
        reporte.setIdConsulta(idConsulta);
        reporte.setIdRespuesta(idRespuesta);
        reporte.setContenidoReportado(contenidoReportado);
        reporte.setMotivo(motivo);
        reporte.setDescripcion(descripcion);
        reporteRepository.save(reporte);
        return new ReporteResponse(reporte.getId(), tipo.name(), reporte.getEstado().name(),
                "Recibimos tu reporte. Un administrador lo revisará.");
    }

    // Lo que decía lo reportado, para guardarlo (el límite de la columna es 500 caracteres).
    private static String recortar(String texto) {
        return texto.length() <= 500 ? texto : texto.substring(0, 499) + "…";
    }

    private static boolean participa(Sesion sesion, Integer usuarioId) {
        return sesion.getEstudiante().getUsuario().getId().equals(usuarioId)
                || sesion.getMentor().getUsuario().getId().equals(usuarioId);
    }

    private static Reporte.Tipo tipo(String valor) {
        try {
            return Reporte.Tipo.valueOf(valor == null ? "" : valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "El tipo de reporte debe ser USUARIO, SESION, CONSULTA o RESPUESTA.");
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
