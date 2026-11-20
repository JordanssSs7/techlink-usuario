package com.techlink.repository;

import com.techlink.model.RecursoArchivo;
import org.springframework.data.jpa.repository.JpaRepository;

// HU-18: contenido de los recursos de tipo archivo. El id es el id del recurso.
public interface RecursoArchivoRepository extends JpaRepository<RecursoArchivo, Integer> {
}
