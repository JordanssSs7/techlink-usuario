package com.techlink.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

// HU-61: el peso (de 0 a 100) de uno de los criterios del emparejamiento: CURSOS, CALIFICACION o DISPONIBILIDAD.
@Entity
@Table(name = "emparejamiento_criterios")
public class CriterioEmparejamiento {

    @Id
    @Column(length = 20)
    private String clave;

    @Column(nullable = false)
    private Integer peso;

    // Administrador que lo cambió por última vez (nulo mientras tenga el valor inicial).
    @Column(name = "id_admin")
    private Integer idAdmin;

    @Column(name = "fecha_actualizacion", nullable = false, insertable = false, updatable = false)
    private LocalDateTime fechaActualizacion;

    public CriterioEmparejamiento() {
    }

    public CriterioEmparejamiento(String clave, Integer peso) {
        this.clave = clave;
        this.peso = peso;
    }

    public String getClave() { return clave; }

    public Integer getPeso() { return peso; }
    public void setPeso(Integer peso) { this.peso = peso; }

    public Integer getIdAdmin() { return idAdmin; }
    public void setIdAdmin(Integer idAdmin) { this.idAdmin = idAdmin; }

    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
}
