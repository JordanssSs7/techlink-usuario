package com.techlink.repository;

import com.techlink.model.CriterioEmparejamiento;
import org.springframework.data.jpa.repository.JpaRepository;

// HU-61: pesos del emparejamiento con IA.
public interface CriterioEmparejamientoRepository extends JpaRepository<CriterioEmparejamiento, String> {
}
