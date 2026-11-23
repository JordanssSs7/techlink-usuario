package com.techlink.service;

import com.techlink.dto.NotificacionDtos.NotificacionResponse;
import com.techlink.dto.NotificacionDtos.NotificacionesResponse;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.Notificacion;
import com.techlink.model.Notificacion.Tipo;
import com.techlink.repository.NotificacionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// HU-22: centro de notificaciones. Otros servicios llaman a crear() para avisar al usuario; crear() respeta las
// preferencias del usuario (HU-24): si desactivó ese tipo en la aplicación, no se guarda nada.
@Service
public class NotificacionService {

    static final int LIMITE_PREDETERMINADO = 30;
    static final int LIMITE_MAXIMO = 100;

    private final NotificacionRepository repositorio;
    private final PreferenciaNotificacionService preferencias;

    public NotificacionService(NotificacionRepository repositorio, PreferenciaNotificacionService preferencias) {
        this.repositorio = repositorio;
        this.preferencias = preferencias;
    }

    private EventosService eventos;

    // Tiempo real: opcional, para que las pruebas que arman el servicio a mano no lo necesiten.
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setEventos(EventosService eventos) {
        this.eventos = eventos;
    }

    // Guarda un aviso para el usuario. Devuelve false si él lo tiene desactivado.
    @Transactional
    public boolean crear(Integer usuarioId, Tipo tipo, String titulo, String mensaje) {
        return crear(usuarioId, tipo, titulo, mensaje, null);
    }

    // Igual, pero con un enlace: al hacer clic en el aviso se abre eso (por ejemplo "foro:12").
    @Transactional
    public boolean crear(Integer usuarioId, Tipo tipo, String titulo, String mensaje, String enlace) {
        if (!preferencias.permite(usuarioId, tipo, PreferenciaNotificacionService.Canal.EN_APP)) {
            return false;
        }
        Notificacion nueva = new Notificacion(usuarioId, tipo, recortar(titulo, 150), recortar(mensaje, 500));
        nueva.setEnlace(enlace);
        repositorio.save(nueva);
        if (eventos != null) {
            eventos.notificacion(usuarioId);
        }
        return true;
    }

    // Las más recientes primero. soloNoLeidas filtra las pendientes; limite se ajusta entre 1 y 100.
    @Transactional(readOnly = true)
    public NotificacionesResponse listar(Integer usuarioId, boolean soloNoLeidas, Integer limite) {
        int tamano = limite == null ? LIMITE_PREDETERMINADO : Math.max(1, Math.min(limite, LIMITE_MAXIMO));
        PageRequest pagina = PageRequest.of(0, tamano);
        List<Notificacion> lista = soloNoLeidas
                ? repositorio.findByIdUsuarioAndLeidaFalseOrderByFechaCreacionDescIdDesc(usuarioId, pagina)
                : repositorio.findByIdUsuarioOrderByFechaCreacionDescIdDesc(usuarioId, pagina);
        return new NotificacionesResponse(repositorio.countByIdUsuarioAndLeidaFalse(usuarioId),
                lista.stream().map(NotificacionService::aRespuesta).toList());
    }

    @Transactional(readOnly = true)
    public long noLeidas(Integer usuarioId) {
        return repositorio.countByIdUsuarioAndLeidaFalse(usuarioId);
    }

    // Marca una como leída. 404 si no existe o es de otro usuario.
    @Transactional
    public NotificacionResponse marcarLeida(Integer usuarioId, Integer id) {
        Notificacion n = repositorio.findByIdAndIdUsuario(id, usuarioId)
                .orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "La notificación no existe."));
        n.setLeida(true);
        return aRespuesta(repositorio.save(n));
    }

    // Marca todas como leídas y devuelve cuántas cambiaron.
    @Transactional
    public int marcarTodasLeidas(Integer usuarioId) {
        return repositorio.marcarTodasLeidas(usuarioId);
    }

    static NotificacionResponse aRespuesta(Notificacion n) {
        return new NotificacionResponse(n.getId(), n.getTipo().name(), n.getTitulo(), n.getMensaje(), n.isLeida(),
                n.getFechaCreacion(), n.getEnlace());
    }

    private static String recortar(String texto, int maximo) {
        if (texto == null) {
            return "";
        }
        return texto.length() <= maximo ? texto : texto.substring(0, maximo - 1) + "…";
    }
}
