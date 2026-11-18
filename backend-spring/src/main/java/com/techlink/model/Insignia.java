package com.techlink.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

// HU-15: insignia que se otorga a un mentor al alcanzar un umbral de horas dictadas o de promedio de estrellas.
@Entity
@Table(name = "insignias")
public class Insignia {

    public enum Tipo {
        HORAS,
        PROMEDIO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_insignia")
    private Integer id;

    @Column(nullable = false, unique = true, length = 40)
    private String codigo;

    @Column(nullable = false, length = 60)
    private String nombre;

    @Column(nullable = false, length = 200)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tipo tipo;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal umbral;

    @Column(name = "minimo_calificaciones", nullable = false)
    private Integer minimoCalificaciones = 0;

    public Insignia() {
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public Tipo getTipo() { return tipo; }
    public void setTipo(Tipo tipo) { this.tipo = tipo; }

    public BigDecimal getUmbral() { return umbral; }
    public void setUmbral(BigDecimal umbral) { this.umbral = umbral; }

    public Integer getMinimoCalificaciones() { return minimoCalificaciones; }
    public void setMinimoCalificaciones(Integer minimoCalificaciones) { this.minimoCalificaciones = minimoCalificaciones; }
}
