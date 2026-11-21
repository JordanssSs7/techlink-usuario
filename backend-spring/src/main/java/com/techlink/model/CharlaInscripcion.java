package com.techlink.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

// HU-25: la inscripción de un usuario a una charla. La combinación (charla, usuario) es única en la base.
@Entity
@Table(name = "charla_inscripciones")
public class CharlaInscripcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_inscripcion")
    private Integer id;

    @Column(name = "id_charla", nullable = false)
    private Integer idCharla;

    @Column(name = "id_usuario", nullable = false)
    private Integer idUsuario;

    @Column(name = "fecha_inscripcion", nullable = false, insertable = false, updatable = false)
    private LocalDateTime fechaInscripcion;

    public CharlaInscripcion() {
    }

    public CharlaInscripcion(Integer idCharla, Integer idUsuario) {
        this.idCharla = idCharla;
        this.idUsuario = idUsuario;
    }

    public Integer getId() { return id; }

    public Integer getIdCharla() { return idCharla; }

    public Integer getIdUsuario() { return idUsuario; }

    public LocalDateTime getFechaInscripcion() { return fechaInscripcion; }
}
