package com.techlink.repository;

import com.techlink.model.ParametrosSeguridad;
import org.springframework.data.jpa.repository.JpaRepository;

// HU-60: la fila de parámetros de seguridad (id 1).
public interface ParametrosSeguridadRepository extends JpaRepository<ParametrosSeguridad, Integer> {
}
