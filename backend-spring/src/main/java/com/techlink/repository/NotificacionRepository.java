package com.techlink.repository;

import com.techlink.model.Notificacion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// HU-22: notificaciones de un usuario.
public interface NotificacionRepository extends JpaRepository<Notificacion, Integer> {

    // Las más recientes primero.
    List<Notificacion> findByIdUsuarioOrderByFechaCreacionDescIdDesc(Integer idUsuario, Pageable pagina);

    List<Notificacion> findByIdUsuarioAndLeidaFalseOrderByFechaCreacionDescIdDesc(Integer idUsuario, Pageable pagina);

    long countByIdUsuarioAndLeidaFalse(Integer idUsuario);

    Optional<Notificacion> findByIdAndIdUsuario(Integer id, Integer idUsuario);

    @Modifying
    @Query("UPDATE Notificacion n SET n.leida = true WHERE n.idUsuario = :usuarioId AND n.leida = false")
    int marcarTodasLeidas(@Param("usuarioId") Integer usuarioId);
}
