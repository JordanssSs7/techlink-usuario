package com.techlink.repository;

import com.techlink.model.Recurso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

// HU-18: recursos compartidos en las sesiones.
public interface RecursoRepository extends JpaRepository<Recurso, Integer> {

    List<Recurso> findBySesionIdOrderByFechaCreacionDescIdDesc(Integer idSesion);

    // Por sesión: cuántos recursos tiene. Con soloVisibles = true no cuenta los ocultos por el administrador.
    @Query("""
            SELECT r.sesion.id, COUNT(r) FROM Recurso r
            WHERE r.sesion.id IN :ids AND (:soloVisibles = false OR r.oculto = false)
            GROUP BY r.sesion.id
            """)
    List<Object[]> contarPorSesiones(@Param("ids") Collection<Integer> ids, @Param("soloVisibles") boolean soloVisibles);
}
