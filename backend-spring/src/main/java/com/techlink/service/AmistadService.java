package com.techlink.service;

import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.Amistad;
import com.techlink.model.EstadoUsuario;
import com.techlink.model.Notificacion;
import com.techlink.model.Usuario;
import com.techlink.model.Mentor;
import com.techlink.repository.AmistadRepository;
import com.techlink.repository.MentorRepository;
import com.techlink.repository.UsuarioFotoRepository;
import com.techlink.repository.UsuarioPortadaRepository;
import com.techlink.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

// Seguidores, como en GitHub: una persona sigue a otra (sin que la otra tenga que aceptar). El perfil de cada persona es visible para
// los demás (descripción, intereses, apodo, estado…). Cada fila de la tabla "amistades" es un seguimiento: id_solicitante sigue a id_destinatario.
@Service
public class AmistadService {

    private static final Set<String> ROLES = Set.of("ESTUDIANTE", "MENTOR");

    // Una persona en una lista de seguidores o de seguidos.
    public record Persona(Integer idUsuario, String nombre, String rol, String frase) {
    }

    // El perfil de otra persona. idMentor solo viene si es mentor (para abrir sus cursos, calificaciones e insignias).
    public record Perfil(Integer idUsuario, String nombres, String apellidos, String apodo, String rol, String frase, boolean tieneFoto,
                         boolean tienePortada, boolean siguiendo, boolean meSigue, long seguidores, long siguiendoTotal, boolean esYo,
                         String descripcion, List<String> intereses, Integer idMentor) {
    }

    private final AmistadRepository relaciones;
    private final UsuarioRepository usuarios;
    private final UsuarioFotoRepository fotos;
    private final UsuarioPortadaRepository portadas;
    private final NotificacionService notificaciones;
    private final MentorRepository mentores;

    public AmistadService(AmistadRepository relaciones, UsuarioRepository usuarios, UsuarioFotoRepository fotos,
                          UsuarioPortadaRepository portadas, NotificacionService notificaciones, MentorRepository mentores) {
        this.mentores = mentores;
        this.relaciones = relaciones;
        this.usuarios = usuarios;
        this.fotos = fotos;
        this.portadas = portadas;
        this.notificaciones = notificaciones;
    }

    @Transactional(readOnly = true)
    public Perfil perfil(Integer yo, Integer otroId) {
        Usuario otro = usuarioValido(otroId);
        boolean esYo = yo.equals(otroId);
        boolean siguiendo = !esYo && relaciones.sigue(yo, otroId).isPresent();
        boolean meSigue = !esYo && relaciones.sigue(otroId, yo).isPresent();
        return new Perfil(otro.getId(), otro.getNombres(), "-".equals(otro.getApellidos()) ? "" : otro.getApellidos(), otro.getApodo(),
                otro.getRol().getNombre(), otro.getFrase(), fotos.existsById(otroId), portadas.existsById(otroId), siguiendo, meSigue,
                relaciones.contarSeguidores(otroId), relaciones.contarSiguiendo(otroId), esYo,
                otro.getDescripcion(), new ArrayList<>(otro.getIntereses()),
                "MENTOR".equals(otro.getRol().getNombre()) ? mentores.findByUsuarioId(otroId).map(Mentor::getId).orElse(null) : null);
    }

    @Transactional(readOnly = true)
    public List<Persona> seguidores(Integer usuarioId) {
        return relaciones.seguidoresDe(usuarioId).stream().map(a -> a.getIdSolicitante()).map(this::personaDe).flatMap(Optional::stream).toList();
    }

    @Transactional(readOnly = true)
    public List<Persona> siguiendo(Integer usuarioId) {
        return relaciones.siguiendoDe(usuarioId).stream().map(a -> a.getIdDestinatario()).map(this::personaDe).flatMap(Optional::stream).toList();
    }

    @Transactional
    public void seguir(Integer yo, Integer otroId) {
        if (yo.equals(otroId)) {
            throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "No puedes seguirte a ti mismo.");
        }
        Usuario otro = usuarioValido(otroId);
        if (relaciones.sigue(yo, otroId).isPresent()) {
            return;
        }
        Usuario quien = usuarios.findById(yo).orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "El usuario no existe."));
        Amistad a = new Amistad(yo, otroId);
        a.setEstado(Amistad.ACEPTADA);
        relaciones.save(a);
        notificaciones.crear(otro.getId(), Notificacion.Tipo.AMISTAD, "Tienes un nuevo seguidor",
                SalaMensajeService.nombreDe(quien) + " empezó a seguirte.", "usuario:" + yo);
    }

    @Transactional
    public void dejarDeSeguir(Integer yo, Integer otroId) {
        relaciones.sigue(yo, otroId).ifPresent(relaciones::delete);
    }

    private Optional<Persona> personaDe(Integer id) {
        return usuarios.findById(id).map(u -> new Persona(u.getId(), SalaMensajeService.nombreDe(u), u.getRol().getNombre(), u.getFrase()));
    }

    private Usuario usuarioValido(Integer id) {
        Usuario u = usuarios.findById(id).orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "La persona no existe."));
        if (u.getEstado() != EstadoUsuario.ACTIVO || !ROLES.contains(u.getRol().getNombre())) {
            throw new ReglaNegocioException(HttpStatus.NOT_FOUND, "La persona no existe.");
        }
        return u;
    }
}
