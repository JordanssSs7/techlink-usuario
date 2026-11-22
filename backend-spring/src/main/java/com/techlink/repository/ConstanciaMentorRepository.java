package com.techlink.repository;

import com.techlink.model.ConstanciaMentor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// HU-27: constancias de los mentores (una por mentor).
public interface ConstanciaMentorRepository extends JpaRepository<ConstanciaMentor, Integer> {

    Optional<ConstanciaMentor> findByIdMentor(Integer idMentor);

    // Para la página pública de verificación.
    Optional<ConstanciaMentor> findByCodigoVerificacion(String codigoVerificacion);
}
