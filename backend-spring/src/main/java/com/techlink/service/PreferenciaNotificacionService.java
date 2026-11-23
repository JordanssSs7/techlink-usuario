package com.techlink.service;

import com.techlink.dto.NotificacionDtos.PreferenciaCambio;
import com.techlink.dto.NotificacionDtos.PreferenciaResponse;
import com.techlink.exception.DatosInvalidosException;
import com.techlink.model.Notificacion.Tipo;
import com.techlink.model.PreferenciaNotificacion;
import com.techlink.repository.PreferenciaNotificacionRepository;
import com.techlink.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// HU-24: el usuario elige, por tipo de aviso, si quiere recibirlo en la aplicación y/o por correo.
// Si nunca eligió nada, recibe todo. Los demás servicios consultan aquí antes de cada envío.
@Service
public class PreferenciaNotificacionService {

    public enum Canal {
        EN_APP,
        CORREO
    }

    private record Descripcion(String nombre, String detalle, boolean correoDisponible) {
    }

    private static final Map<Tipo, Descripcion> DESCRIPCIONES = new EnumMap<>(Tipo.class);

    static {
        DESCRIPCIONES.put(Tipo.SESION, new Descripcion("Mis sesiones",
                "Confirmaciones, cancelaciones y cambios de horario de tus sesiones.", true));
        DESCRIPCIONES.put(Tipo.RECORDATORIO, new Descripcion("Recordatorios",
                "Aviso 24 horas y 1 hora antes de cada sesión.", true));
        DESCRIPCIONES.put(Tipo.ANUNCIO, new Descripcion("Anuncios",
                "Novedades que publica el equipo de TechLink para tu rol.", false));
        DESCRIPCIONES.put(Tipo.FORO, new Descripcion("Foro",
                "Cuando alguien responde tu consulta o tu comentario en el foro.", false));
        DESCRIPCIONES.put(Tipo.AMISTAD, new Descripcion("Seguidores",
                "Cuando alguien empieza a seguirte.", false));
        DESCRIPCIONES.put(Tipo.CHARLA, new Descripcion("Charlas",
                "Charlas nuevas que coinciden con tus intereses, tu inscripción y los cambios o cancelaciones de tus charlas.", false));
    }

    private final PreferenciaNotificacionRepository repositorio;
    private final UsuarioRepository usuarioRepository;

    public PreferenciaNotificacionService(PreferenciaNotificacionRepository repositorio,
                                          UsuarioRepository usuarioRepository) {
        this.repositorio = repositorio;
        this.usuarioRepository = usuarioRepository;
    }

    // Todos los tipos con lo que tiene elegido el usuario (o lo predeterminado: todo activado).
    @Transactional(readOnly = true)
    public List<PreferenciaResponse> listar(Integer usuarioId) {
        Map<Tipo, PreferenciaNotificacion> guardadas = new EnumMap<>(Tipo.class);
        for (PreferenciaNotificacion p : repositorio.findByIdUsuario(usuarioId)) {
            guardadas.put(p.getTipo(), p);
        }
        List<PreferenciaResponse> lista = new ArrayList<>();
        for (Tipo tipo : Tipo.values()) {
            Descripcion d = DESCRIPCIONES.get(tipo);
            PreferenciaNotificacion p = guardadas.get(tipo);
            lista.add(new PreferenciaResponse(tipo.name(), d.nombre(), d.detalle(),
                    p == null || p.isEnApp(), d.correoDisponible() && (p == null || p.isCorreo()), d.correoDisponible()));
        }
        return lista;
    }

    // Guarda los cambios enviados y devuelve la lista completa actualizada. 400 si algún tipo no existe.
    @Transactional
    public List<PreferenciaResponse> guardar(Integer usuarioId, List<PreferenciaCambio> cambios) {
        if (cambios == null || cambios.isEmpty()) {
            throw new DatosInvalidosException("preferencias", "Envía al menos una preferencia.");
        }
        for (PreferenciaCambio cambio : cambios) {
            Tipo tipo = tipo(cambio.tipo());
            PreferenciaNotificacion p = repositorio.findByIdUsuarioAndTipo(usuarioId, tipo)
                    .orElseGet(() -> new PreferenciaNotificacion(usuarioId, tipo, true, true));
            if (cambio.enApp() != null) {
                p.setEnApp(cambio.enApp());
            }
            // Los tipos que no se envían por correo no aceptan activarlo.
            if (cambio.correo() != null && DESCRIPCIONES.get(tipo).correoDisponible()) {
                p.setCorreo(cambio.correo());
            }
            repositorio.save(p);
        }
        return listar(usuarioId);
    }

    // ¿Quiere el usuario recibir este tipo de aviso por este canal? Sin preferencia guardada, sí.
    @Transactional(readOnly = true)
    public boolean permite(Integer usuarioId, Tipo tipo, Canal canal) {
        if (canal == Canal.CORREO && !DESCRIPCIONES.get(tipo).correoDisponible()) {
            return false;
        }
        Optional<PreferenciaNotificacion> p = repositorio.findByIdUsuarioAndTipo(usuarioId, tipo);
        if (p.isEmpty()) {
            return true;
        }
        return canal == Canal.EN_APP ? p.get().isEnApp() : p.get().isCorreo();
    }

    // Igual que permite(), pero a partir del correo del destinatario (así llegan los eventos de las sesiones).
    // Si el correo no pertenece a ningún usuario, se deja pasar.
    @Transactional(readOnly = true)
    public boolean permiteCorreoA(String correo, Tipo tipo) {
        return usuarioRepository.findByCorreoInstitucional(correo)
                .map(u -> permite(u.getId(), tipo, Canal.CORREO))
                .orElse(true);
    }

    private static Tipo tipo(String texto) {
        try {
            return Tipo.valueOf(texto == null ? "" : texto.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new DatosInvalidosException("tipo", "El tipo de notificación no es válido.");
        }
    }
}
