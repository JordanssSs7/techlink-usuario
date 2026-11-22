package com.techlink.service;

import com.techlink.dto.ForoDtos.Autor;
import com.techlink.dto.ForoDtos.ConsultaDetalle;
import com.techlink.dto.ForoDtos.ConsultaResumen;
import com.techlink.dto.ForoDtos.EdicionConsulta;
import com.techlink.dto.ForoDtos.EdicionRespuesta;
import com.techlink.dto.ForoDtos.NuevaConsulta;
import com.techlink.dto.ForoDtos.NuevaRespuesta;
import com.techlink.dto.ForoDtos.RespuestaResponse;
import com.techlink.exception.DatosInvalidosException;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.Consulta;
import com.techlink.model.Curso;
import com.techlink.model.Respuesta;
import com.techlink.model.Usuario;
import com.techlink.repository.ConsultaRepository;
import com.techlink.repository.CursoRepository;
import com.techlink.repository.RespuestaRepository;
import com.techlink.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// HU-17: foro de consultas por curso. Un usuario publica una duda asociada a un curso, otros usuarios la responden y
// el autor puede marcar una respuesta como útil: esa se muestra primero. Lo oculto por el administrador (HU-54) no aparece.
@Service
public class ForoService {

    static final int MAX_CONSULTAS = 50;
    static final int MIN_TITULO = 5;
    static final int MAX_TITULO = 150;
    static final int MIN_CONTENIDO = 10;
    static final int MAX_CONTENIDO = 2000;
    static final int MAX_EXTRACTO = 160;

    private final ConsultaRepository consultaRepository;
    private final RespuestaRepository respuestaRepository;
    private final CursoRepository cursoRepository;
    private final UsuarioRepository usuarioRepository;
    private final LimitadorConsultas limitador;
    private final Clock reloj;

    @Autowired
    public ForoService(ConsultaRepository consultaRepository,
                       RespuestaRepository respuestaRepository,
                       CursoRepository cursoRepository,
                       UsuarioRepository usuarioRepository,
                       @Value("${app.foro.max-publicaciones-hora:20}") int maxPublicacionesPorHora) {
        this(consultaRepository, respuestaRepository, cursoRepository, usuarioRepository,
                new LimitadorConsultas(maxPublicacionesPorHora, Clock.systemDefaultZone()), Clock.systemDefaultZone());
    }

    // Para las pruebas automaticas.
    ForoService(ConsultaRepository consultaRepository, RespuestaRepository respuestaRepository,
                CursoRepository cursoRepository, UsuarioRepository usuarioRepository,
                LimitadorConsultas limitador, Clock reloj) {
        this.consultaRepository = consultaRepository;
        this.respuestaRepository = respuestaRepository;
        this.cursoRepository = cursoRepository;
        this.usuarioRepository = usuarioRepository;
        this.limitador = limitador;
        this.reloj = reloj;
    }

    // Las consultas más recientes, con filtro opcional por curso y por palabra en el título o el contenido.
    @Transactional(readOnly = true)
    public List<ConsultaResumen> listar(Integer cursoId, String busqueda) {
        String texto = busqueda == null || busqueda.isBlank() ? null : "%" + busqueda.trim().toLowerCase(Locale.ROOT) + "%";
        List<Consulta> consultas = consultaRepository.buscar(cursoId, texto, PageRequest.of(0, MAX_CONSULTAS));
        if (consultas.isEmpty()) {
            return List.of();
        }

        Map<Integer, int[]> resumen = new LinkedHashMap<>();
        for (Object[] fila : respuestaRepository.resumenPorConsultas(consultas.stream().map(Consulta::getId).toList())) {
            resumen.put((Integer) fila[0], new int[]{((Number) fila[1]).intValue(), fila[2] == null ? 0 : ((Number) fila[2]).intValue()});
        }
        return consultas.stream().map(c -> {
            int[] r = resumen.getOrDefault(c.getId(), new int[]{0, 0});
            return new ConsultaResumen(c.getId(), c.getTitulo(), extracto(c.getContenido()), c.getCurso().getId(),
                    c.getCurso().getNombre(), autor(c.getAutor()), c.getFechaCreacion(), r[0], r[1] > 0);
        }).toList();
    }

    @Transactional(readOnly = true)
    public ConsultaDetalle detalle(Integer usuarioId, Integer consultaId) {
        return aDetalle(usuarioId, visible(consultaId));
    }

    @Transactional
    public ConsultaDetalle crear(Integer usuarioId, NuevaConsulta peticion) {
        Map<String, String> errores = new LinkedHashMap<>();
        String titulo = limpiar(peticion.titulo());
        String contenido = limpiar(peticion.contenido());
        if (titulo.length() < MIN_TITULO || titulo.length() > MAX_TITULO) {
            errores.put("titulo", "El título debe tener entre " + MIN_TITULO + " y " + MAX_TITULO + " caracteres.");
        }
        if (contenido.length() < MIN_CONTENIDO || contenido.length() > MAX_CONTENIDO) {
            errores.put("contenido", "Cuenta tu duda con entre " + MIN_CONTENIDO + " y " + MAX_CONTENIDO + " caracteres.");
        }
        Curso curso = peticion.cursoId() == null ? null : cursoRepository.findById(peticion.cursoId())
                .filter(c -> Boolean.TRUE.equals(c.getActivo())).orElse(null);
        if (curso == null) {
            errores.put("cursoId", "Elige el curso de tu consulta.");
        }
        if (!errores.isEmpty()) {
            throw new DatosInvalidosException(errores);
        }
        // Moderación: un insulto, una amenaza o spam no se publica (422); LlamaLink pide reformularlo.
        ModeracionTexto.exigirLimpio(titulo, contenido);
        controlarRitmo(usuarioId);

        Consulta consulta = new Consulta();
        consulta.setAutor(usuario(usuarioId));
        consulta.setCurso(curso);
        consulta.setTitulo(titulo);
        consulta.setContenido(contenido);
        consulta.setFechaCreacion(LocalDateTime.now(reloj));
        return aDetalle(usuarioId, consultaRepository.save(consulta));
    }

    @Transactional
    public ConsultaDetalle responder(Integer usuarioId, Integer consultaId, NuevaRespuesta peticion) {
        Consulta consulta = visible(consultaId);
        String contenido = limpiar(peticion.contenido());
        if (contenido.length() < MIN_CONTENIDO || contenido.length() > MAX_CONTENIDO) {
            throw new DatosInvalidosException("contenido",
                    "Escribe una respuesta de entre " + MIN_CONTENIDO + " y " + MAX_CONTENIDO + " caracteres.");
        }
        ModeracionTexto.exigirLimpio(contenido);
        Integer padre = padreDeLaRespuesta(consulta, peticion.respuestaPadreId());
        controlarRitmo(usuarioId);

        Respuesta respuesta = new Respuesta();
        respuesta.setIdPadre(padre);
        respuesta.setConsulta(consulta);
        respuesta.setAutor(usuario(usuarioId));
        respuesta.setContenido(contenido);
        respuesta.setFechaCreacion(LocalDateTime.now(reloj));
        respuestaRepository.save(respuesta);
        return aDetalle(usuarioId, consulta);
    }

    // ---------- editar y eliminar lo propio ----------

    // Quien hizo la consulta puede cambiar su título y su contenido (pasan por las mismas reglas y la moderación que al publicar).
    @Transactional
    public ConsultaDetalle editarConsulta(Integer usuarioId, Integer consultaId, EdicionConsulta peticion) {
        Consulta consulta = visible(consultaId);
        exigirAutor(consulta.getAutor().getId(), usuarioId, "Solo quien hizo la consulta puede editarla.");
        String titulo = limpiar(peticion.titulo());
        String contenido = limpiar(peticion.contenido());
        Map<String, String> errores = new LinkedHashMap<>();
        if (titulo.length() < MIN_TITULO || titulo.length() > MAX_TITULO) {
            errores.put("titulo", "El título debe tener entre " + MIN_TITULO + " y " + MAX_TITULO + " caracteres.");
        }
        if (contenido.length() < MIN_CONTENIDO || contenido.length() > MAX_CONTENIDO) {
            errores.put("contenido", "Cuenta tu duda con entre " + MIN_CONTENIDO + " y " + MAX_CONTENIDO + " caracteres.");
        }
        if (!errores.isEmpty()) {
            throw new DatosInvalidosException(errores);
        }
        ModeracionTexto.exigirLimpio(titulo, contenido);
        consulta.setTitulo(titulo);
        consulta.setContenido(contenido);
        consulta.setFechaEdicion(LocalDateTime.now(reloj));
        return aDetalle(usuarioId, consultaRepository.save(consulta));
    }

    // Quien hizo la consulta puede eliminarla. Se van con ella sus respuestas.
    @Transactional
    public void eliminarConsulta(Integer usuarioId, Integer consultaId) {
        Consulta consulta = visible(consultaId);
        exigirAutor(consulta.getAutor().getId(), usuarioId, "Solo quien hizo la consulta puede eliminarla.");
        consultaRepository.delete(consulta);
    }

    @Transactional
    public ConsultaDetalle editarRespuesta(Integer usuarioId, Integer consultaId, Integer respuestaId, EdicionRespuesta peticion) {
        Consulta consulta = visible(consultaId);
        Respuesta respuesta = respuestaVisible(consulta, respuestaId);
        exigirAutor(respuesta.getAutor().getId(), usuarioId, "Solo quien escribió la respuesta puede editarla.");
        String contenido = limpiar(peticion.contenido());
        if (contenido.length() < MIN_CONTENIDO || contenido.length() > MAX_CONTENIDO) {
            throw new DatosInvalidosException("contenido",
                    "Escribe una respuesta de entre " + MIN_CONTENIDO + " y " + MAX_CONTENIDO + " caracteres.");
        }
        ModeracionTexto.exigirLimpio(contenido);
        respuesta.setContenido(contenido);
        respuesta.setFechaEdicion(LocalDateTime.now(reloj));
        respuestaRepository.save(respuesta);
        return aDetalle(usuarioId, consulta);
    }

    // Quien escribió una respuesta puede eliminarla. Si es una respuesta principal, se va con las de su hilo.
    @Transactional
    public ConsultaDetalle eliminarRespuesta(Integer usuarioId, Integer consultaId, Integer respuestaId) {
        Consulta consulta = visible(consultaId);
        Respuesta respuesta = respuestaVisible(consulta, respuestaId);
        exigirAutor(respuesta.getAutor().getId(), usuarioId, "Solo quien escribió la respuesta puede eliminarla.");
        respuestaRepository.delete(respuesta);
        respuestaRepository.flush();
        return aDetalle(usuarioId, consulta);
    }

    private Respuesta respuestaVisible(Consulta consulta, Integer respuestaId) {
        return respuestaRepository.findByConsultaIdAndOcultoFalseOrderByUtilDescFechaCreacionAscIdAsc(consulta.getId()).stream()
                .filter(r -> r.getId().equals(respuestaId)).findFirst()
                .orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "La respuesta no existe."));
    }

    private static void exigirAutor(Integer idAutor, Integer usuarioId, String mensaje) {
        if (!idAutor.equals(usuarioId)) {
            throw new ReglaNegocioException(HttpStatus.FORBIDDEN, mensaje);
        }
    }

    // Solo el autor de la consulta marca (o desmarca) la respuesta que le sirvió. Solo puede haber una útil.
    @Transactional
    public ConsultaDetalle marcarUtil(Integer usuarioId, Integer consultaId, Integer respuestaId) {
        Consulta consulta = visible(consultaId);
        if (!consulta.getAutor().getId().equals(usuarioId)) {
            throw new ReglaNegocioException(HttpStatus.FORBIDDEN, "Solo quien hizo la consulta puede marcar una respuesta como útil.");
        }
        List<Respuesta> todas = respuestaRepository.findByConsultaId(consultaId);
        Respuesta elegida = todas.stream()
                .filter(r -> r.getId().equals(respuestaId) && !r.isOculto())
                .findFirst()
                .orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "La respuesta no existe."));

        if (elegida.getIdPadre() != null) {
            throw new ReglaNegocioException(HttpStatus.CONFLICT, "Solo las respuestas principales se pueden marcar como útiles.");
        }
        boolean yaEraUtil = elegida.isUtil();
        for (Respuesta r : todas) {
            r.setUtil(false);
        }
        elegida.setUtil(!yaEraUtil);
        respuestaRepository.saveAll(todas);
        return aDetalle(usuarioId, consulta);
    }

    // La respuesta principal a la que se contesta: null si se responde a la consulta. Contestar a una respuesta que ya está dentro de un
    // hilo la deja en ese mismo hilo (un solo nivel). 404 si esa respuesta no existe, es de otra consulta o fue ocultada.
    private Integer padreDeLaRespuesta(Consulta consulta, Integer padreId) {
        if (padreId == null) {
            return null;
        }
        Respuesta destino = respuestaRepository.findByConsultaIdAndOcultoFalseOrderByUtilDescFechaCreacionAscIdAsc(consulta.getId())
                .stream().filter(r -> r.getId().equals(padreId)).findFirst()
                .orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "La respuesta que quieres contestar no existe."));
        return destino.getIdPadre() != null ? destino.getIdPadre() : destino.getId();
    }

    private Consulta visible(Integer consultaId) {
        return consultaRepository.findByIdAndOcultoFalse(consultaId)
                .orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "La consulta no existe."));
    }

    private Usuario usuario(Integer usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "El usuario no existe."));
    }

    // Evita el spam: un usuario no puede publicar más de cierta cantidad de consultas y respuestas por hora.
    private void controlarRitmo(Integer usuarioId) {
        if (!limitador.intentar(usuarioId)) {
            throw new ReglaNegocioException(HttpStatus.TOO_MANY_REQUESTS,
                    "Estás publicando muy rápido. Espera un rato antes de volver a escribir.");
        }
    }

    private ConsultaDetalle aDetalle(Integer usuarioId, Consulta c) {
        List<Respuesta> visibles = respuestaRepository.findByConsultaIdAndOcultoFalseOrderByUtilDescFechaCreacionAscIdAsc(c.getId());
        java.util.Set<Integer> idsVisibles = visibles.stream().map(Respuesta::getId).collect(java.util.stream.Collectors.toSet());
        // Si el administrador ocultó una respuesta principal, su hilo también desaparece.
        List<RespuestaResponse> respuestas = visibles.stream()
                .filter(r -> r.getIdPadre() == null || idsVisibles.contains(r.getIdPadre()))
                .map(r -> new RespuestaResponse(r.getId(), r.getContenido(), autor(r.getAutor()), r.getFechaCreacion(),
                        r.isUtil(), r.getAutor().getId().equals(usuarioId), r.getIdPadre(), r.getFechaEdicion() != null))
                .toList();
        return new ConsultaDetalle(c.getId(), c.getTitulo(), c.getContenido(), c.getCurso().getId(), c.getCurso().getNombre(),
                autor(c.getAutor()), c.getFechaCreacion(), c.getAutor().getId().equals(usuarioId), respuestas, c.getFechaEdicion() != null);
    }

    static Autor autor(Usuario u) {
        String nombre = primera(u.getNombres());
        String apellido = "-".equals(u.getApellidos()) ? "" : primera(u.getApellidos());
        return new Autor(u.getId(), (nombre + " " + apellido).trim(), u.getRol().getNombre());
    }

    private static String primera(String texto) {
        return texto == null || texto.isBlank() ? "" : texto.trim().split("\\s+")[0];
    }

    private static String limpiar(String texto) {
        return texto == null ? "" : texto.trim().replaceAll("[ \\t]+", " ");
    }

    private static String extracto(String contenido) {
        String uno = contenido.replaceAll("\\s+", " ").trim();
        return uno.length() > MAX_EXTRACTO ? uno.substring(0, MAX_EXTRACTO - 1) + "…" : uno;
    }
}
