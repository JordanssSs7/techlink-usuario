package com.techlink.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

// HU-08: un mensaje del chat de la sala virtual (texto o fragmento de código). Queda guardado para que quien entre
// después, o vuelva a entrar, vea la conversación.
@Entity
@Table(name = "sala_mensajes")
public class SalaMensaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_mensaje")
    private Long id;

    // SESION o CHARLA, y el id de esa sesión o charla.
    @Column(name = "sala_tipo", nullable = false, length = 10)
    private String salaTipo;

    @Column(name = "sala_id", nullable = false)
    private Integer salaId;

    @Column(name = "id_usuario", nullable = false)
    private Integer idUsuario;

    // TEXTO o CODIGO.
    @Column(nullable = false, length = 10)
    private String tipo;

    @Column(length = 30)
    private String lenguaje;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String contenido;

    @Column(name = "fecha_creacion", nullable = false, insertable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    public SalaMensaje() {
    }

    public SalaMensaje(String salaTipo, Integer salaId, Integer idUsuario, String tipo, String lenguaje, String contenido) {
        this.salaTipo = salaTipo;
        this.salaId = salaId;
        this.idUsuario = idUsuario;
        this.tipo = tipo;
        this.lenguaje = lenguaje;
        this.contenido = contenido;
    }

    public Long getId() { return id; }
    public String getSalaTipo() { return salaTipo; }
    public Integer getSalaId() { return salaId; }
    public Integer getIdUsuario() { return idUsuario; }
    public String getTipo() { return tipo; }
    public String getLenguaje() { return lenguaje; }
    public String getContenido() { return contenido; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
}
