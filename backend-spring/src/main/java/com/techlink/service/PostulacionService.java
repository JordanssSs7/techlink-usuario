package com.techlink.service;

import com.techlink.dto.PostulacionResponse;
import com.techlink.exception.DatosInvalidosException;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.Estudiante;
import com.techlink.model.Postulacion;
import com.techlink.model.PostulacionArchivo;
import com.techlink.repository.EstudianteRepository;
import com.techlink.repository.PostulacionArchivoRepository;
import com.techlink.repository.PostulacionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

// HU-03: un estudiante de ciclos avanzados postula a ser mentor adjuntando su comprobante de cursos aprobados
// (PDF o imagen, máximo 5 MB). Al enviarla queda "EN REVISIÓN" y se le avisa que la recibimos.
// El administrador la evalúa después (HU-48).
@Service
public class PostulacionService {

    static final long MAX_ARCHIVO_BYTES = 5L * 1024 * 1024;
    static final int MAX_COMENTARIO = 500;
    static final int CICLO_MINIMO_POR_DEFECTO = 4;

    private final EstudianteRepository estudianteRepository;
    private final PostulacionRepository postulacionRepository;
    private final PostulacionArchivoRepository archivoRepository;
    private final ApplicationEventPublisher eventos;
    private final int cicloMinimo;

    @Autowired
    public PostulacionService(EstudianteRepository estudianteRepository,
                              PostulacionRepository postulacionRepository,
                              PostulacionArchivoRepository archivoRepository,
                              ApplicationEventPublisher eventos,
                              @Value("${app.postulaciones.ciclo-minimo:" + CICLO_MINIMO_POR_DEFECTO + "}") int cicloMinimo) {
        this.estudianteRepository = estudianteRepository;
        this.postulacionRepository = postulacionRepository;
        this.archivoRepository = archivoRepository;
        this.eventos = eventos;
        this.cicloMinimo = cicloMinimo;
    }

    // Qué ve el estudiante: su última postulación y si puede enviar una nueva.
    @Transactional(readOnly = true)
    public PostulacionResponse estado(Integer usuarioId) {
        Estudiante estudiante = buscarEstudiante(usuarioId);
        Optional<Postulacion> ultima = postulacionRepository
                .findFirstByEstudianteIdOrderByFechaEnvioDescIdDesc(estudiante.getId());

        String motivoNoPuede = motivoQueImpide(estudiante, ultima.orElse(null));
        if (ultima.isEmpty()) {
            return new PostulacionResponse("NINGUNA", null, null, null, null, motivoNoPuede == null, motivoNoPuede);
        }
        Postulacion p = ultima.get();
        return new PostulacionResponse(p.getEstado().name(), p.getId(), p.getNombreArchivo(), p.getFechaEnvio(),
                p.getMotivoRevision(), motivoNoPuede == null, motivoNoPuede);
    }

    @Transactional
    public PostulacionResponse postular(Integer usuarioId, MultipartFile archivo, String comentario) {
        Estudiante estudiante = buscarEstudiante(usuarioId);
        Postulacion ultima = postulacionRepository
                .findFirstByEstudianteIdOrderByFechaEnvioDescIdDesc(estudiante.getId()).orElse(null);

        String motivo = motivoQueImpide(estudiante, ultima);
        if (motivo != null) {
            throw new ReglaNegocioException(HttpStatus.CONFLICT, motivo);
        }

        String texto = comentario == null ? "" : comentario.trim();
        if (texto.length() > MAX_COMENTARIO) {
            throw new DatosInvalidosException("comentario", "El comentario no puede superar los " + MAX_COMENTARIO + " caracteres.");
        }
        byte[] bytes = ArchivoSubido.leerYValidar(archivo, "archivo", MAX_ARCHIVO_BYTES,
                Set.of(ArchivoSubido.PDF, ArchivoSubido.PNG, ArchivoSubido.JPG), "un PDF, PNG o JPG");

        Postulacion postulacion = new Postulacion();
        postulacion.setEstudiante(estudiante);
        postulacion.setEstado(Postulacion.Estado.EN_REVISION);
        postulacion.setNombreArchivo(ArchivoSubido.nombreSeguro(archivo.getOriginalFilename(), "comprobante"));
        postulacion.setTipoContenido(ArchivoSubido.detectarTipo(bytes));
        postulacion.setTamanoBytes(bytes.length);
        postulacion.setComentario(texto.isEmpty() ? null : texto);
        postulacion.setFechaEnvio(LocalDateTime.now());
        Postulacion guardada = postulacionRepository.saveAndFlush(postulacion);

        PostulacionArchivo contenido = new PostulacionArchivo();
        contenido.setIdPostulacion(guardada.getId());
        contenido.setContenido(bytes);
        archivoRepository.save(contenido);

        eventos.publishEvent(new PostulacionRecibidaEvent(guardada.getId(),
                estudiante.getUsuario().getCorreoInstitucional(), estudiante.getUsuario().getNombres()));
        return estado(usuarioId);
    }

    // null si puede postular; si no, el motivo para mostrarle al estudiante.
    private String motivoQueImpide(Estudiante estudiante, Postulacion ultima) {
        if (estudiante.getCiclo() == null || estudiante.getCiclo() < cicloMinimo) {
            return "Puedes postular a mentor desde el ciclo " + cicloMinimo + ".";
        }
        if (ultima != null && ultima.getEstado() == Postulacion.Estado.EN_REVISION) {
            return "Ya tienes una postulación en revisión.";
        }
        if (ultima != null && ultima.getEstado() == Postulacion.Estado.APROBADA) {
            return "Tu postulación ya fue aprobada.";
        }
        return null;
    }

    private Estudiante buscarEstudiante(Integer usuarioId) {
        return estudianteRepository.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "No tienes perfil de estudiante."));
    }
}
