package com.techlink.repository;

import com.techlink.model.MentorDisponibilidad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// HU-05: acceso a los horarios del mentor. Lo usan tambien HU-04 (emparejamiento) y HU-07 (agendar).
public interface MentorDisponibilidadRepository extends JpaRepository<MentorDisponibilidad, Integer> {

    // Solo los horarios activos, ordenados por dia y hora.
    List<MentorDisponibilidad> findByMentorIdAndActivoTrueOrderByDiaSemanaAscHoraInicioAsc(Integer idMentor);
}
