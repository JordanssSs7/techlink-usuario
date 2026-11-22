package com.techlink.repository;

import com.techlink.model.SesionRecordatorio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

// HU-23: recordatorios de sesión ya enviados.
public interface SesionRecordatorioRepository extends JpaRepository<SesionRecordatorio, Integer> {

    boolean existsByIdSesionAndMomentoAndInicioSesion(Integer idSesion, SesionRecordatorio.Momento momento,
                                                      LocalDateTime inicioSesion);
}
