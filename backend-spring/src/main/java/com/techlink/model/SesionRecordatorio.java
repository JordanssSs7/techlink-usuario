package com.techlink.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

// HU-23: registro de un recordatorio ya enviado. (sesión, momento, inicio) es único: no se repite el aviso, y si la
// sesión se reprograma (cambia su inicio) se avisa de nuevo para el nuevo horario.
@Entity
@Table(name = "sesion_recordatorios")
public class SesionRecordatorio {

    // H24: la sesión empieza en menos de 24 horas. H1: empieza en menos de 1 hora.
    public enum Momento {
        H24,
        H1
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_recordatorio")
    private Integer id;

    @Column(name = "id_sesion", nullable = false)
    private Integer idSesion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private Momento momento;

    @Column(name = "inicio_sesion", nullable = false)
    private LocalDateTime inicioSesion;

    public SesionRecordatorio() {
    }

    public SesionRecordatorio(Integer idSesion, Momento momento, LocalDateTime inicioSesion) {
        this.idSesion = idSesion;
        this.momento = momento;
        this.inicioSesion = inicioSesion;
    }

    public Integer getId() { return id; }
    public Integer getIdSesion() { return idSesion; }
    public Momento getMomento() { return momento; }
    public LocalDateTime getInicioSesion() { return inicioSesion; }
}
