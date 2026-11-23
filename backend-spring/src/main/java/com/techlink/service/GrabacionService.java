package com.techlink.service;

import com.techlink.exception.SesionException;
import com.techlink.model.Sesion;
import com.techlink.model.SesionGrabacion;
import com.techlink.repository.SesionGrabacionRepository;
import com.techlink.repository.SesionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// Grabación de las sesiones. El navegador del mentor graba la reunión y la manda por partes (cada pocos segundos); aquí se van juntando en
// un solo archivo del servidor. Solo el mentor de la sesión puede grabar; el estudiante y el mentor pueden verla cuando termina.
@Service
public class GrabacionService {

    private final SesionGrabacionRepository grabaciones;
    private final SesionRepository sesiones;
    private final Path carpeta;
    private final long maxBytes;

    public GrabacionService(SesionGrabacionRepository grabaciones, SesionRepository sesiones,
                            @Value("${app.grabaciones.carpeta:grabaciones}") String carpeta,
                            @Value("${app.grabaciones.max-mb:1024}") long maxMb) {
        this.grabaciones = grabaciones;
        this.sesiones = sesiones;
        this.carpeta = Path.of(carpeta).toAbsolutePath();
        this.maxBytes = maxMb * 1024 * 1024;
    }

    // Guarda la parte número n (0, 1, 2…) de la grabación. Repetir una parte ya guardada no hace nada (por si se reintenta el envío).
    @Transactional
    public void agregarParte(Integer mentorUsuarioId, Integer sesionId, int n, byte[] datos) {
        Sesion sesion = sesionDelMentor(mentorUsuarioId, sesionId);
        if (sesion.getEstado() != Sesion.EstadoSesion.CONFIRMADA) {
            throw new SesionException(HttpStatus.CONFLICT, "Solo se graban sesiones confirmadas.");
        }
        if (datos == null || datos.length == 0) {
            return;
        }
        SesionGrabacion g = grabaciones.findById(sesionId).orElse(null);
        if (g == null) {
            if (n != 0) {
                throw new SesionException(HttpStatus.CONFLICT, "La grabación no empezó.");
            }
            g = new SesionGrabacion(sesionId, "sesion-" + sesionId + ".webm");
        } else if (SesionGrabacion.LISTA.equals(g.getEstado())) {
            throw new SesionException(HttpStatus.CONFLICT, "Esta sesión ya tiene una grabación.");
        }
        if (n < g.getPartes()) {
            return;
        }
        if (n > g.getPartes()) {
            throw new SesionException(HttpStatus.CONFLICT, "Falta una parte de la grabación.");
        }
        if (g.getBytes() + datos.length > maxBytes) {
            throw new SesionException(HttpStatus.PAYLOAD_TOO_LARGE, "La grabación superó el tamaño máximo permitido.");
        }
        try {
            Files.createDirectories(carpeta);
            Files.write(carpeta.resolve(g.getArchivo()), datos, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new SesionException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo guardar la grabación.");
        }
        g.setBytes(g.getBytes() + datos.length);
        g.setPartes(g.getPartes() + 1);
        grabaciones.save(g);
    }

    // Marca la grabación como lista, con su duración en segundos.
    @Transactional
    public void terminar(Integer mentorUsuarioId, Integer sesionId, int duracionSeg) {
        sesionDelMentor(mentorUsuarioId, sesionId);
        SesionGrabacion g = grabaciones.findById(sesionId)
                .orElseThrow(() -> new SesionException(HttpStatus.NOT_FOUND, "No hay una grabación en curso."));
        if (SesionGrabacion.LISTA.equals(g.getEstado())) {
            return;
        }
        g.setEstado(SesionGrabacion.LISTA);
        g.setDuracionSeg(Math.max(0, duracionSeg));
        g.setFechaFin(LocalDateTime.now());
        grabaciones.save(g);
    }

    // ¿Esta persona (estudiante o mentor de la sesión) puede ver la grabación? Devuelve el archivo si está lista.
    @Transactional(readOnly = true)
    public Path archivoParaVer(Integer usuarioId, Integer sesionId) {
        Sesion s = sesiones.findById(sesionId)
                .filter(x -> x.getMentor().getUsuario().getId().equals(usuarioId) || x.getEstudiante().getUsuario().getId().equals(usuarioId))
                .orElseThrow(() -> new SesionException(HttpStatus.NOT_FOUND, "La sesión no existe."));
        return archivoLista(s.getId());
    }

    @Transactional(readOnly = true)
    public Path archivoLista(Integer sesionId) {
        SesionGrabacion g = grabaciones.findById(sesionId)
                .filter(x -> SesionGrabacion.LISTA.equals(x.getEstado()))
                .orElseThrow(() -> new SesionException(HttpStatus.NOT_FOUND, "Esta sesión no tiene grabación."));
        Path archivo = carpeta.resolve(g.getArchivo());
        if (!Files.exists(archivo)) {
            throw new SesionException(HttpStatus.NOT_FOUND, "El archivo de la grabación ya no está disponible.");
        }
        return archivo;
    }

    // Ids, de entre estos, que ya tienen una grabación lista.
    @Transactional(readOnly = true)
    public Set<Integer> conGrabacion(Collection<Integer> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        List<SesionGrabacion> lista = grabaciones.findByIdSesionInAndEstado(ids, SesionGrabacion.LISTA);
        return lista.stream().map(SesionGrabacion::getIdSesion).collect(Collectors.toSet());
    }

    private Sesion sesionDelMentor(Integer mentorUsuarioId, Integer sesionId) {
        return sesiones.findById(sesionId)
                .filter(s -> s.getMentor().getUsuario().getId().equals(mentorUsuarioId))
                .orElseThrow(() -> new SesionException(HttpStatus.NOT_FOUND, "La sesión no existe."));
    }
}
