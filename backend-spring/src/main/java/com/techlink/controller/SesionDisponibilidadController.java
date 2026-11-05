
package com.techlink.controller;

import com.techlink.model.MentorDisponibilidad;
import com.techlink.repository.MentorDisponibilidadRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/sesiones/disponibilidades")
public class SesionDisponibilidadController {

    private final MentorDisponibilidadRepository disponibilidadRepository;

    public SesionDisponibilidadController(
            MentorDisponibilidadRepository disponibilidadRepository
    ) {
        this.disponibilidadRepository = disponibilidadRepository;
    }

    @GetMapping("/mentor/{mentorId}")
    public ResponseEntity<List<DisponibilidadResponse>> listarDisponibilidades(
            @PathVariable Integer mentorId
    ) {
        List<DisponibilidadResponse> horarios = disponibilidadRepository
                .findByMentorIdAndActivoTrueOrderByDiaSemanaAscHoraInicioAsc(mentorId)
                .stream()
                .map(d -> new DisponibilidadResponse(
                        d.getId(),
                        d.getDiaSemana(),
                        d.getHoraInicio(),
                        d.getHoraFin()
                ))
                .toList();

        return ResponseEntity.ok(horarios);
    }

    public record DisponibilidadResponse(
            Integer idDisponibilidad,
            Integer diaSemana,
            LocalTime horaInicio,
            LocalTime horaFin
    ) {
    }
}
