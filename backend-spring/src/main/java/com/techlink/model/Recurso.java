package com.techlink.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

// HU-18: un recurso que el mentor comparte en una sesión: un archivo (PDF o imagen), un enlace o un fragmento de código.
// El contenido de un archivo va en RecursoArchivo. HU-54: el administrador lo puede ocultar sin borrarlo.
@Entity
@Table(name = "recursos")
public class Recurso {

    public enum Tipo {
        ARCHIVO,
        ENLACE,
        CODIGO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_recurso")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_sesion", nullable = false)
    private Sesion sesion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_autor", nullable = false)
    private Usuario autor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tipo tipo;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(length = 300)
    private String descripcion;

    @Column(length = 500)
    private String url;

    @Column(name = "contenido_texto", columnDefinition = "TEXT")
    private String contenidoTexto;

    @Column(length = 30)
    private String lenguaje;

    @Column(name = "nombre_archivo", length = 150)
    private String nombreArchivo;

    @Column(name = "tipo_contenido", length = 30)
    private String tipoContenido;

    @Column(name = "tamano_bytes")
    private Integer tamanoBytes;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(nullable = false, insertable = false, updatable = false)
    private boolean oculto;

    @Column(name = "motivo_moderacion", insertable = false, updatable = false)
    private String motivoModeracion;

    public Recurso() {
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Sesion getSesion() { return sesion; }
    public void setSesion(Sesion sesion) { this.sesion = sesion; }

    public Usuario getAutor() { return autor; }
    public void setAutor(Usuario autor) { this.autor = autor; }

    public Tipo getTipo() { return tipo; }
    public void setTipo(Tipo tipo) { this.tipo = tipo; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getContenidoTexto() { return contenidoTexto; }
    public void setContenidoTexto(String contenidoTexto) { this.contenidoTexto = contenidoTexto; }

    public String getLenguaje() { return lenguaje; }
    public void setLenguaje(String lenguaje) { this.lenguaje = lenguaje; }

    public String getNombreArchivo() { return nombreArchivo; }
    public void setNombreArchivo(String nombreArchivo) { this.nombreArchivo = nombreArchivo; }

    public String getTipoContenido() { return tipoContenido; }
    public void setTipoContenido(String tipoContenido) { this.tipoContenido = tipoContenido; }

    public Integer getTamanoBytes() { return tamanoBytes; }
    public void setTamanoBytes(Integer tamanoBytes) { this.tamanoBytes = tamanoBytes; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public boolean isOculto() { return oculto; }
    public String getMotivoModeracion() { return motivoModeracion; }
}
