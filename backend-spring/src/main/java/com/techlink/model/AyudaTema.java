package com.techlink.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// HU-30: un tema del centro de ayuda (por ejemplo, «Cuenta y acceso») que agrupa preguntas frecuentes.
@Entity
@Table(name = "ayuda_temas")
public class AyudaTema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tema")
    private Integer id;

    @Column(nullable = false, unique = true, length = 80)
    private String nombre;

    @Column(nullable = false)
    private Integer orden = 0;

    public AyudaTema() {
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public Integer getOrden() { return orden; }
    public void setOrden(Integer orden) { this.orden = orden; }
}
