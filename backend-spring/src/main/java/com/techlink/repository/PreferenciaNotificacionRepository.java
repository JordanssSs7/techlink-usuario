package com.techlink.repository;

import com.techlink.model.Notificacion;
import com.techlink.model.PreferenciaNotificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// HU-24: preferencias de notificación.
public interface PreferenciaNotificacionRepository extends JpaRepository<PreferenciaNotificacion, Integer> {

    List<PreferenciaNotificacion> findByIdUsuario(Integer idUsuario);

    Optional<PreferenciaNotificacion> findByIdUsuarioAndTipo(Integer idUsuario, Notificacion.Tipo tipo);
}
