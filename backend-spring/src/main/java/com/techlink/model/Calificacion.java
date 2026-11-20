package com.techlink.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

// HU-09: calificación (1 a 5 estrellas y comentario opcional) que el estudiante deja al terminar una sesión.
@Entity
@Table(name = "calificaciones")
public class Calificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_calificacion")
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_sesion", nullable = false, unique = true)
    private Sesion sesion;

    @Column(nullable = false)
    private Integer estrellas;

    @Column(length = 500)
    private String comentario;

    @Column(name = "fecha_creacion", nullable = false, insertable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    // HU-53: una calificación oculta por el administrador no se muestra ni cuenta para la reputación del mentor.
    // Solo la modifica el panel de administración, por eso aquí es de solo lectura.
    @Column(nullable = false, insertable = false, updatable = false)
    private boolean oculto;

    public Calificacion() {
    }

    public Integer getId() { return id; }

    public Sesion getSesion() { return sesion; }
    public void setSesion(Sesion sesion) { this.sesion = sesion; }

    public Integer getEstrellas() { return estrellas; }
    public void setEstrellas(Integer estrellas) { this.estrellas = estrellas; }

    public String getComentario() { return comentario; }
    public void setComentario(String comentario) { this.comentario = comentario; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }

    public boolean isOculto() { return oculto; }
}
