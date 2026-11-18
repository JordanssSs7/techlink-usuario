package com.techlink.service;

import com.techlink.dto.CalificacionResponse;
import com.techlink.exception.SesionException;
import com.techlink.model.Calificacion;
import com.techlink.model.Mentor;
import com.techlink.model.Sesion;
import com.techlink.repository.CalificacionRepository;
import com.techlink.repository.MentorRepository;
import com.techlink.repository.SesionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;

// HU-09: al terminar una sesión, el estudiante la califica de 1 a 5 estrellas (con un comentario opcional) y la
// reputación del mentor se recalcula con todas las calificaciones que tiene.
@Service
public class CalificacionService {

    static final int MAX_COMENTARIO = 500;

    private final SesionRepository sesionRepository;
    private final CalificacionRepository calificacionRepository;
    private final MentorRepository mentorRepository;
    private final Clock reloj;

    @Autowired
    public CalificacionService(SesionRepository sesionRepository,
                               CalificacionRepository calificacionRepository,
                               MentorRepository mentorRepository) {
        this(sesionRepository, calificacionRepository, mentorRepository, Clock.systemDefaultZone());
    }

    // El reloj se puede cambiar solo en las pruebas automaticas.
    CalificacionService(SesionRepository sesionRepository,
                        CalificacionRepository calificacionRepository,
                        MentorRepository mentorRepository,
                        Clock reloj) {
        this.sesionRepository = sesionRepository;
        this.calificacionRepository = calificacionRepository;
        this.mentorRepository = mentorRepository;
        this.reloj = reloj;
    }

    @Transactional
    public CalificacionResponse calificar(Integer usuarioId, Integer sesionId, Integer estrellas, String comentario) {
        if (estrellas == null || estrellas < 1 || estrellas > 5) {
            throw new SesionException(HttpStatus.BAD_REQUEST, "Elige entre 1 y 5 estrellas.");
        }
        String texto = comentario == null ? "" : comentario.trim();
        if (texto.length() > MAX_COMENTARIO) {
            throw new SesionException(HttpStatus.BAD_REQUEST,
                    "El comentario no puede superar los " + MAX_COMENTARIO + " caracteres.");
        }

        Sesion sesion = sesionRepository.findById(sesionId)
                .filter(s -> s.getEstudiante().getUsuario().getId().equals(usuarioId))
                .orElseThrow(() -> new SesionException(HttpStatus.NOT_FOUND, "La sesión no existe."));

        if (sesion.getEstado() != Sesion.EstadoSesion.CONFIRMADA) {
            throw new SesionException(HttpStatus.CONFLICT, "No se puede calificar una sesión cancelada.");
        }
        if (LocalDateTime.now(reloj).isBefore(LocalDateTime.of(sesion.getFecha(), sesion.getHoraFin()))) {
            throw new SesionException(HttpStatus.CONFLICT, "Podrás calificar cuando termine la sesión.");
        }
        if (calificacionRepository.findBySesionId(sesionId).isPresent()) {
            throw new SesionException(HttpStatus.CONFLICT, "Ya calificaste esta sesión.");
        }

        Calificacion calificacion = new Calificacion();
        calificacion.setSesion(sesion);
        calificacion.setEstrellas(estrellas);
        calificacion.setComentario(texto.isEmpty() ? null : texto);
        calificacionRepository.save(calificacion);

        Mentor mentor = recalcularReputacion(sesion.getMentor());
        return new CalificacionResponse(estrellas, calificacion.getComentario(),
                mentor.getReputacion(), mentor.getTotalCalificaciones());
    }

    // La reputación del mentor es el promedio de todas sus estrellas, con 2 decimales.
    private Mentor recalcularReputacion(Mentor mentor) {
        CalificacionRepository.Resumen resumen = calificacionRepository.resumenDeMentor(mentor.getId());
        double promedio = resumen.getPromedio() == null ? 0 : resumen.getPromedio();
        mentor.setReputacion(BigDecimal.valueOf(promedio).setScale(2, RoundingMode.HALF_UP));
        mentor.setTotalCalificaciones(resumen.getTotal() == null ? 0 : resumen.getTotal().intValue());
        return mentorRepository.save(mentor);
    }
}
