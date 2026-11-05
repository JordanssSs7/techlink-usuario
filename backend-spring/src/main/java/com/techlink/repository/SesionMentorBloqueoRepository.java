
package com.techlink.repository;

import com.techlink.model.Mentor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SesionMentorBloqueoRepository
        extends JpaRepository<Mentor, Integer> {

    @Query(
            value = "SELECT * FROM mentores WHERE id_mentor = :mentorId FOR UPDATE",
            nativeQuery = true
    )
    Optional<Mentor> buscarMentorConBloqueo(
            @Param("mentorId") Integer mentorId
    );
}
