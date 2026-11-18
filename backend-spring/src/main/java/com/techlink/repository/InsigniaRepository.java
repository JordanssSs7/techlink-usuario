package com.techlink.repository;

import com.techlink.model.Insignia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// HU-15: catálogo de insignias.
public interface InsigniaRepository extends JpaRepository<Insignia, Integer> {

    List<Insignia> findAllByOrderByIdAsc();
}
