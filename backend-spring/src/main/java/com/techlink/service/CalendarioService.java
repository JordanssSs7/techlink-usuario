package com.techlink.service;

import com.techlink.dto.EventoCalendarioResponse;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.Charla;
import com.techlink.model.Sesion;
import com.techlink.model.Usuario;
import com.techlink.repository.CharlaRepository;
import com.techlink.repository.SesionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// HU-26: el calendario del usuario. Junta las sesiones de mentoría confirmadas (como estudiante o como mentor) y,
// para los estudiantes, las charlas a las que se inscribió, dentro de un rango de fechas.
@Service
public class CalendarioService {

    // Un rango más largo que esto no hace falta para ver un mes y evita pedir toda la historia de golpe.
    static final long MAX_DIAS = 62;

    private final SesionRepository sesionRepository;
    private final CharlaRepository charlaRepository;

    public CalendarioService(SesionRepository sesionRepository, CharlaRepository charlaRepository) {
        this.sesionRepository = sesionRepository;
        this.charlaRepository = charlaRepository;
    }

    @Transactional(readOnly = true)
    public List<EventoCalendarioResponse> eventos(Integer usuarioId, String rol, LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "Indica las fechas desde y hasta (AAAA-MM-DD).");
        }
        if (hasta.isBefore(desde)) {
            throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "La fecha hasta no puede ser anterior a la fecha desde.");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) > MAX_DIAS) {
            throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "El rango no puede superar los " + MAX_DIAS + " días.");
        }

        List<EventoCalendarioResponse> eventos = new ArrayList<>();
        for (Sesion s : sesionRepository.confirmadasDelUsuarioEntre(usuarioId, desde, hasta)) {
            Usuario otra = s.getEstudiante().getUsuario().getId().equals(usuarioId)
                    ? s.getMentor().getUsuario() : s.getEstudiante().getUsuario();
            eventos.add(new EventoCalendarioResponse("SESION", s.getId(),
                    s.getCurso() == null ? "Mentoría" : s.getCurso().getNombre(),
                    "Con " + otra.getNombres() + " " + otra.getApellidos(),
                    s.getFecha(), s.getHoraInicio(), s.getHoraFin(), s.getEnlaceReunion()));
        }
        if ("ESTUDIANTE".equals(rol)) {
            for (Charla c : charlaRepository.inscritasEntre(usuarioId, desde, hasta)) {
                eventos.add(new EventoCalendarioResponse("CHARLA", c.getId(), c.getNombre(), c.getTematica(),
                        c.getFecha(), c.getHoraInicio(), c.getHoraFin(), c.getEnlace()));
            }
        }
        eventos.sort(Comparator.comparing(EventoCalendarioResponse::fecha)
                .thenComparing(EventoCalendarioResponse::horaInicio, Comparator.nullsLast(Comparator.<LocalTime>naturalOrder())));
        return eventos;
    }
}
