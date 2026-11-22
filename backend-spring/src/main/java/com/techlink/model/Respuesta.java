package com.techlink.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

// HU-17: la respuesta de un usuario a una consulta del foro. "util" la marca el autor de la consulta (solo una).
// HU-54: el administrador la puede ocultar sin borrarla; por eso "oculto" es de solo lectura aquí.
@Entity
@Table(name = "respuestas")
public class Respuesta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_respuesta")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_consulta", nullable = false)
    private Consulta consulta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_autor", nullable = false)
    private Usuario autor;

    @Column(nullable = false, length = 2000)
    private String contenido;

    @Column(nullable = false)
    private boolean util;

    // Null en una respuesta principal; en una respuesta dentro de un hilo, el id de la respuesta principal.
    @Column(name = "id_respuesta_padre")
    private Integer idPadre;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    // Cuándo la editó su autor (null si nunca).
    @Column(name = "fecha_edicion")
    private LocalDateTime fechaEdicion;

    @Column(nullable = false, insertable = false, updatable = false)
    private boolean oculto;

    public Respuesta() {
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Consulta getConsulta() { return consulta; }
    public void setConsulta(Consulta consulta) { this.consulta = consulta; }

    public Usuario getAutor() { return autor; }
    public void setAutor(Usuario autor) { this.autor = autor; }

    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }

    public Integer getIdPadre() { return idPadre; }
    public void setIdPadre(Integer idPadre) { this.idPadre = idPadre; }

    public boolean isUtil() { return util; }
    public void setUtil(boolean util) { this.util = util; }

    public LocalDateTime getFechaEdicion() { return fechaEdicion; }
    public void setFechaEdicion(LocalDateTime fechaEdicion) { this.fechaEdicion = fechaEdicion; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public boolean isOculto() { return oculto; }
}
