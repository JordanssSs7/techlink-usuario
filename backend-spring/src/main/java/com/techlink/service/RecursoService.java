package com.techlink.service;

import com.techlink.dto.RecursoDtos.Descarga;
import com.techlink.dto.RecursoDtos.NuevoCodigo;
import com.techlink.dto.RecursoDtos.NuevoEnlace;
import com.techlink.dto.RecursoDtos.RecursoResponse;
import com.techlink.exception.DatosInvalidosException;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.Recurso;
import com.techlink.model.RecursoArchivo;
import com.techlink.model.Sesion;
import com.techlink.repository.RecursoArchivoRepository;
import com.techlink.repository.RecursoRepository;
import com.techlink.repository.SesionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

// HU-18: el mentor comparte recursos en una sesión (archivo PDF o imagen de hasta 10 MB, enlace o código) y el estudiante
// los ve y los descarga desde el historial de la sesión. Los que el administrador oculta (HU-54) solo los ve su autor.
@Service
public class RecursoService {

    static final long MAX_ARCHIVO_BYTES = 10L * 1024 * 1024;
    static final int MAX_RECURSOS_POR_SESION = 20;
    static final int MIN_TITULO = 3;
    static final int MAX_TITULO = 150;
    static final int MAX_DESCRIPCION = 300;
    static final int MAX_URL = 500;
    static final int MAX_CODIGO = 5000;
    static final int MAX_LENGUAJE = 30;

    private final RecursoRepository recursoRepository;
    private final RecursoArchivoRepository archivoRepository;
    private final SesionRepository sesionRepository;
    private final Clock reloj;

    @Autowired
    public RecursoService(RecursoRepository recursoRepository, RecursoArchivoRepository archivoRepository,
                          SesionRepository sesionRepository) {
        this(recursoRepository, archivoRepository, sesionRepository, Clock.systemDefaultZone());
    }

    // Para las pruebas automaticas.
    RecursoService(RecursoRepository recursoRepository, RecursoArchivoRepository archivoRepository,
                   SesionRepository sesionRepository, Clock reloj) {
        this.recursoRepository = recursoRepository;
        this.archivoRepository = archivoRepository;
        this.sesionRepository = sesionRepository;
        this.reloj = reloj;
    }

    // El mentor ve todos sus recursos (marcando los ocultos); el estudiante solo los que no están ocultos.
    @Transactional(readOnly = true)
    public List<RecursoResponse> listar(Integer usuarioId, Integer sesionId) {
        Sesion sesion = participante(usuarioId, sesionId);
        boolean esMentor = esMentor(sesion, usuarioId);
        return recursoRepository.findBySesionIdOrderByFechaCreacionDescIdDesc(sesionId).stream()
                .filter(r -> esMentor || !r.isOculto())
                .map(r -> aRespuesta(r, esMentor))
                .toList();
    }

    @Transactional
    public RecursoResponse agregarEnlace(Integer usuarioId, Integer sesionId, NuevoEnlace peticion) {
        Sesion sesion = sesionDelMentor(usuarioId, sesionId);
        String titulo = titulo(peticion.titulo());
        String descripcion = descripcion(peticion.descripcion());
        String url = url(peticion.url());

        Recurso r = nuevo(sesion, usuarioId, Recurso.Tipo.ENLACE, titulo, descripcion);
        r.setUrl(url);
        return aRespuesta(recursoRepository.save(r), true);
    }

    @Transactional
    public RecursoResponse agregarCodigo(Integer usuarioId, Integer sesionId, NuevoCodigo peticion) {
        Sesion sesion = sesionDelMentor(usuarioId, sesionId);
        String titulo = titulo(peticion.titulo());
        String descripcion = descripcion(peticion.descripcion());
        String codigo = peticion.codigo() == null ? "" : peticion.codigo().strip();
        if (codigo.isEmpty()) {
            throw new DatosInvalidosException("codigo", "Pega el código que quieres compartir.");
        }
        if (codigo.length() > MAX_CODIGO) {
            throw new DatosInvalidosException("codigo", "El código no puede superar los " + MAX_CODIGO + " caracteres.");
        }
        String lenguaje = peticion.lenguaje() == null ? "" : peticion.lenguaje().trim();
        if (lenguaje.length() > MAX_LENGUAJE) {
            throw new DatosInvalidosException("lenguaje", "El lenguaje no puede superar los " + MAX_LENGUAJE + " caracteres.");
        }

        Recurso r = nuevo(sesion, usuarioId, Recurso.Tipo.CODIGO, titulo, descripcion);
        r.setContenidoTexto(codigo);
        r.setLenguaje(lenguaje.isEmpty() ? null : lenguaje);
        return aRespuesta(recursoRepository.save(r), true);
    }

    // PDF, PNG o JPG de hasta 10 MB. Se revisa por dentro, no por el nombre.
    @Transactional
    public RecursoResponse agregarArchivo(Integer usuarioId, Integer sesionId, String tituloCrudo, String descripcionCruda,
                                          MultipartFile archivo) {
        Sesion sesion = sesionDelMentor(usuarioId, sesionId);
        String titulo = titulo(tituloCrudo);
        String descripcion = descripcion(descripcionCruda);
        byte[] bytes = ArchivoSubido.leerYValidar(archivo, "archivo", MAX_ARCHIVO_BYTES,
                Set.of(ArchivoSubido.PDF, ArchivoSubido.PNG, ArchivoSubido.JPG), "un PDF, PNG o JPG");

        Recurso r = nuevo(sesion, usuarioId, Recurso.Tipo.ARCHIVO, titulo, descripcion);
        r.setNombreArchivo(ArchivoSubido.nombreSeguro(archivo.getOriginalFilename(), "recurso"));
        r.setTipoContenido(ArchivoSubido.detectarTipo(bytes));
        r.setTamanoBytes(bytes.length);
        Recurso guardado = recursoRepository.saveAndFlush(r);

        RecursoArchivo contenido = new RecursoArchivo();
        contenido.setIdRecurso(guardado.getId());
        contenido.setContenido(bytes);
        archivoRepository.save(contenido);
        return aRespuesta(guardado, true);
    }

    // Descarga de un archivo: el mentor y el estudiante de la sesión (un recurso oculto solo lo baja su autor).
    @Transactional(readOnly = true)
    public Descarga descargar(Integer usuarioId, Integer recursoId) {
        Recurso recurso = recursoRepository.findById(recursoId)
                .orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "El recurso no existe."));
        Sesion sesion = participante(usuarioId, recurso.getSesion().getId());
        if (recurso.isOculto() && !esMentor(sesion, usuarioId)) {
            throw new ReglaNegocioException(HttpStatus.NOT_FOUND, "El recurso no existe.");
        }
        if (recurso.getTipo() != Recurso.Tipo.ARCHIVO) {
            throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "Este recurso no es un archivo.");
        }
        RecursoArchivo contenido = archivoRepository.findById(recursoId)
                .orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "El archivo ya no está disponible."));
        return new Descarga(recurso.getNombreArchivo(), recurso.getTipoContenido(), contenido.getContenido());
    }

    // El mentor puede quitar un recurso que compartió.
    @Transactional
    public void eliminar(Integer usuarioId, Integer recursoId) {
        Recurso recurso = recursoRepository.findById(recursoId)
                .filter(r -> r.getAutor().getId().equals(usuarioId))
                .orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "El recurso no existe."));
        archivoRepository.findById(recursoId).ifPresent(archivoRepository::delete);
        recursoRepository.delete(recurso);
    }

    private Recurso nuevo(Sesion sesion, Integer usuarioId, Recurso.Tipo tipo, String titulo, String descripcion) {
        if (recursoRepository.findBySesionIdOrderByFechaCreacionDescIdDesc(sesion.getId()).size() >= MAX_RECURSOS_POR_SESION) {
            throw new ReglaNegocioException(HttpStatus.CONFLICT,
                    "Una sesión admite hasta " + MAX_RECURSOS_POR_SESION + " recursos. Quita alguno para agregar otro.");
        }
        Recurso r = new Recurso();
        r.setSesion(sesion);
        r.setAutor(sesion.getMentor().getUsuario());
        r.setTipo(tipo);
        r.setTitulo(titulo);
        r.setDescripcion(descripcion);
        r.setFechaCreacion(LocalDateTime.now(reloj));
        return r;
    }

    // Solo el mentor de la sesión comparte recursos, y no en una sesión cancelada.
    private Sesion sesionDelMentor(Integer usuarioId, Integer sesionId) {
        Sesion sesion = sesionRepository.findById(sesionId)
                .filter(s -> esMentor(s, usuarioId))
                .orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "La sesión no existe."));
        if (sesion.getEstado() != Sesion.EstadoSesion.CONFIRMADA) {
            throw new ReglaNegocioException(HttpStatus.CONFLICT, "No se pueden compartir recursos en una sesión cancelada.");
        }
        return sesion;
    }

    // El mentor o el estudiante de la sesión; cualquier otro la ve como inexistente.
    private Sesion participante(Integer usuarioId, Integer sesionId) {
        return sesionRepository.findById(sesionId)
                .filter(s -> esMentor(s, usuarioId) || s.getEstudiante().getUsuario().getId().equals(usuarioId))
                .orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "La sesión no existe."));
    }

    private static boolean esMentor(Sesion sesion, Integer usuarioId) {
        return sesion.getMentor().getUsuario().getId().equals(usuarioId);
    }

    private static String titulo(String crudo) {
        String titulo = crudo == null ? "" : crudo.trim();
        if (titulo.length() < MIN_TITULO || titulo.length() > MAX_TITULO) {
            throw new DatosInvalidosException("titulo", "El título debe tener entre " + MIN_TITULO + " y " + MAX_TITULO + " caracteres.");
        }
        return titulo;
    }

    private static String descripcion(String cruda) {
        String d = cruda == null ? "" : cruda.trim();
        if (d.length() > MAX_DESCRIPCION) {
            throw new DatosInvalidosException("descripcion", "La descripción no puede superar los " + MAX_DESCRIPCION + " caracteres.");
        }
        return d.isEmpty() ? null : d;
    }

    // Solo http o https, con dominio y sin espacios: así no se aceptan cosas como javascript: o file:.
    static String url(String cruda) {
        String url = cruda == null ? "" : cruda.trim();
        if (url.isEmpty()) {
            throw new DatosInvalidosException("url", "Escribe el enlace del recurso.");
        }
        if (url.length() > MAX_URL) {
            throw new DatosInvalidosException("url", "El enlace no puede superar los " + MAX_URL + " caracteres.");
        }
        try {
            URI uri = URI.create(url);
            String esquema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            if ((!esquema.equals("http") && !esquema.equals("https")) || uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new DatosInvalidosException("url", "El enlace debe empezar con http:// o https:// y ser una dirección válida.");
        }
        return url;
    }

    private static RecursoResponse aRespuesta(Recurso r, boolean esMentor) {
        return new RecursoResponse(r.getId(), r.getTipo().name(), r.getTitulo(), r.getDescripcion(), r.getUrl(),
                r.getContenidoTexto(), r.getLenguaje(), r.getNombreArchivo(), r.getTipoContenido(), r.getTamanoBytes(),
                r.getFechaCreacion(), esMentor && r.isOculto(), esMentor ? r.getMotivoModeracion() : null, esMentor);
    }
}
