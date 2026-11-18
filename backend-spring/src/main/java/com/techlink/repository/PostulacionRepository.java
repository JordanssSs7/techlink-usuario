package com.techlink.repository;

import com.techlink.model.Postulacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// HU-03: postulaciones para ser mentor.
public interface PostulacionRepository extends JpaRepository<Postulacion, Integer> {

    // La postulación más reciente de un estudiante (la que decide qué estado ve).
    Optional<Postulacion> findFirstByEstudianteIdOrderByFechaEnvioDescIdDesc(Integer idEstudiante);

    boolean existsByEstudianteIdAndEstado(Integer idEstudiante, Postulacion.Estado estado);

    // Para el panel del administrador (HU-48): las postulaciones por evaluar, de la más antigua a la más reciente.
    List<Postulacion> findByEstadoOrderByFechaEnvioAsc(Postulacion.Estado estado);
}
