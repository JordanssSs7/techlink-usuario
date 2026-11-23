package com.techlink.repository;

import com.techlink.model.SesionGrabacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

// Grabaciones de las sesiones. El id de la grabación es el id de la sesión.
public interface SesionGrabacionRepository extends JpaRepository<SesionGrabacion, Integer> {

    List<SesionGrabacion> findByIdSesionInAndEstado(Collection<Integer> ids, String estado);
}
