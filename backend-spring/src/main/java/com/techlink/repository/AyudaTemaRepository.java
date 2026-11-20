package com.techlink.repository;

import com.techlink.model.AyudaTema;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// HU-30: temas del centro de ayuda.
public interface AyudaTemaRepository extends JpaRepository<AyudaTema, Integer> {

    List<AyudaTema> findAllByOrderByOrdenAscIdAsc();
}
