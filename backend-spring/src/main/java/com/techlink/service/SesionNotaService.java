package com.techlink.service;

import com.techlink.dto.NotaResponse;
import com.techlink.exception.SesionException;
import com.techlink.model.Sesion;
import com.techlink.model.SesionNota;
import com.techlink.repository.SesionNotaRepository;
import com.techlink.repository.SesionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

// HU-21: el mentor registra notas de cada sesión (acuerdos y tareas pendientes). Puede editarlas hasta 24 horas
// después de que termine la sesión; el estudiante las ve en modo solo lectura.
@Service
public class SesionNotaService {

    static final int MAX_NOTA = 2000;
    static final Duration PLAZO_EDICION = Duration.ofHours(24);

    private final SesionRepository sesionRepository;
    private final SesionNotaRepository notaRepository;
    private final Clock reloj;

    @Autowired
    public SesionNotaService(SesionRepository sesionRepository, SesionNotaRepository notaRepository) {
        this(sesionRepository, notaRepository, Clock.systemDefaultZone());
    }

    // El reloj se puede cambiar solo en las pruebas automaticas.
    SesionNotaService(SesionRepository sesionRepository, SesionNotaRepository notaRepository, Clock reloj) {
        this.sesionRepository = sesionRepository;
        this.notaRepository = notaRepository;
        this.reloj = reloj;
    }

    // Guarda (o edita) las notas. Solo el mentor de la sesión.
    @Transactional
    public NotaResponse guardar(Integer usuarioId, Integer sesionId, String contenido) {
        String texto = contenido == null ? "" : contenido.trim();
        if (texto.isEmpty()) {
            throw new SesionException(HttpStatus.BAD_REQUEST, "Escribe la nota antes de guardarla.");
        }
        if (texto.length() > MAX_NOTA) {
            throw new SesionException(HttpStatus.BAD_REQUEST,
                    "La nota no puede superar los " + MAX_NOTA + " caracteres.");
        }

        Sesion sesion = sesionRepository.findById(sesionId)
                .filter(s -> s.getMentor().getUsuario().getId().equals(usuarioId))
                .orElseThrow(() -> new SesionException(HttpStatus.NOT_FOUND, "La sesión no existe."));

        if (sesion.getEstado() != Sesion.EstadoSesion.CONFIRMADA) {
            throw new SesionException(HttpStatus.CONFLICT, "Una sesión cancelada no tiene notas.");
        }
        LocalDateTime ahora = LocalDateTime.now(reloj);
        if (ahora.isBefore(LocalDateTime.of(sesion.getFecha(), sesion.getHoraInicio()))) {
            throw new SesionException(HttpStatus.CONFLICT, "Podrás escribir notas cuando empiece la sesión.");
        }
        if (!puedeEditar(sesion, ahora)) {
            throw new SesionException(HttpStatus.CONFLICT,
                    "Las notas solo se pueden editar hasta 24 horas después de la sesión.");
        }

        SesionNota nota = notaRepository.findBySesionId(sesionId).orElseGet(() -> {
            SesionNota nueva = new SesionNota();
            nueva.setSesion(sesion);
            return nueva;
        });
        nota.setContenido(texto);
        SesionNota guardada = notaRepository.saveAndFlush(nota);
        // En una nota recién creada la base de datos aún no devolvió la fecha: se usa la hora actual.
        LocalDateTime actualizada = guardada.getFechaActualizacion() != null ? guardada.getFechaActualizacion() : ahora;
        return new NotaResponse(guardada.getContenido(), actualizada, true);
    }

    // Lee las notas. Las pueden ver el mentor y el estudiante de la sesión; solo el mentor las puede editar.
    @Transactional(readOnly = true)
    public NotaResponse obtener(Integer usuarioId, Integer sesionId) {
        Sesion sesion = sesionRepository.findById(sesionId)
                .filter(s -> s.getMentor().getUsuario().getId().equals(usuarioId)
                        || s.getEstudiante().getUsuario().getId().equals(usuarioId))
                .orElseThrow(() -> new SesionException(HttpStatus.NOT_FOUND, "La sesión no existe."));

        boolean esMentor = sesion.getMentor().getUsuario().getId().equals(usuarioId);
        boolean editable = esMentor && sesion.getEstado() == Sesion.EstadoSesion.CONFIRMADA
                && puedeEditar(sesion, LocalDateTime.now(reloj))
                && !LocalDateTime.now(reloj).isBefore(LocalDateTime.of(sesion.getFecha(), sesion.getHoraInicio()));

        return notaRepository.findBySesionId(sesionId)
                .map(n -> new NotaResponse(n.getContenido(), n.getFechaActualizacion(), editable))
                .orElse(new NotaResponse(null, null, editable));
    }

    // Hasta 24 horas después de la hora de fin de la sesión.
    static boolean puedeEditar(Sesion sesion, LocalDateTime ahora) {
        return !ahora.isAfter(LocalDateTime.of(sesion.getFecha(), sesion.getHoraFin()).plus(PLAZO_EDICION));
    }
}
