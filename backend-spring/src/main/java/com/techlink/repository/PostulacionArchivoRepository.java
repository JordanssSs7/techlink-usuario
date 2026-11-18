package com.techlink.repository;

import com.techlink.model.PostulacionArchivo;
import org.springframework.data.jpa.repository.JpaRepository;

// HU-03: comprobantes de las postulaciones. El id del archivo es el id de la postulación.
public interface PostulacionArchivoRepository extends JpaRepository<PostulacionArchivo, Integer> {
}
