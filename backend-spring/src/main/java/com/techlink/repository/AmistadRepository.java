package com.techlink.repository;

import com.techlink.model.Amistad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// Seguimientos entre usuarios: id_solicitante sigue a id_destinatario.
public interface AmistadRepository extends JpaRepository<Amistad, Integer> {

    @Query("SELECT a FROM Amistad a WHERE a.idSolicitante = :quien AND a.idDestinatario = :aQuien")
    Optional<Amistad> sigue(@Param("quien") Integer quien, @Param("aQuien") Integer aQuien);

    @Query("SELECT a FROM Amistad a WHERE a.idDestinatario = :u ORDER BY a.id DESC")
    List<Amistad> seguidoresDe(@Param("u") Integer u);

    @Query("SELECT a FROM Amistad a WHERE a.idSolicitante = :u ORDER BY a.id DESC")
    List<Amistad> siguiendoDe(@Param("u") Integer u);

    @Query("SELECT COUNT(a) FROM Amistad a WHERE a.idDestinatario = :u")
    long contarSeguidores(@Param("u") Integer u);

    @Query("SELECT COUNT(a) FROM Amistad a WHERE a.idSolicitante = :u")
    long contarSiguiendo(@Param("u") Integer u);
}
