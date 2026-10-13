package com.techlink.model;

import jakarta.persistence.*;

// Entidad que representa la tabla "roles" (ESTUDIANTE, MENTOR, ADMIN).
@Entity
@Table(name = "roles")
public class Rol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_rol")
    private Integer id;

    @Column(name = "nombre_rol", nullable = false, unique = true, length = 50)
    private String nombre;

    public Rol() {
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
}
