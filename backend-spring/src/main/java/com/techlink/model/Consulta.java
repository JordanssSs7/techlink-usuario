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

// HU-17: una consulta (duda) que un usuario publica en el foro, asociada a un curso.
// HU-54: el administrador la puede ocultar sin borrarla; por eso "oculto" es de solo lectura aquí.
@Entity
@Table(name = "consultas")
public class Consulta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_consulta")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_autor", nullable = false)
    private Usuario autor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_curso", nullable = false)
    private Curso curso;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(nullable = false, length = 2000)
    private String contenido;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(nullable = false, insertable = false, updatable = false)
    private boolean oculto;

    @Column(name = "motivo_moderacion", insertable = false, updatable = false)
    private String motivoModeracion;

    public Consulta() {
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Usuario getAutor() { return autor; }
    public void setAutor(Usuario autor) { this.autor = autor; }

    public Curso getCurso() { return curso; }
    public void setCurso(Curso curso) { this.curso = curso; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public boolean isOculto() { return oculto; }
    public String getMotivoModeracion() { return motivoModeracion; }
}
