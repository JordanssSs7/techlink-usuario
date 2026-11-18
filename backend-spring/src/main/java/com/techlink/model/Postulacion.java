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

// HU-03: postulación de un estudiante para ser mentor, con su comprobante de cursos aprobados (en PostulacionArchivo).
// El administrador la evalúa (HU-48): pasa de EN_REVISION a APROBADA o RECHAZADA.
@Entity
@Table(name = "postulaciones")
public class Postulacion {

    public enum Estado {
        EN_REVISION,
        APROBADA,
        RECHAZADA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_postulacion")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_estudiante", nullable = false)
    private Estudiante estudiante;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado = Estado.EN_REVISION;

    @Column(name = "nombre_archivo", nullable = false, length = 150)
    private String nombreArchivo;

    @Column(name = "tipo_contenido", nullable = false, length = 30)
    private String tipoContenido;

    @Column(name = "tamano_bytes", nullable = false)
    private Integer tamanoBytes;

    @Column(length = 500)
    private String comentario;

    @Column(name = "fecha_envio", nullable = false)
    private LocalDateTime fechaEnvio;

    @Column(name = "fecha_revision")
    private LocalDateTime fechaRevision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_revisor")
    private Usuario revisor;

    @Column(name = "motivo_revision", length = 500)
    private String motivoRevision;

    public Postulacion() {
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Estudiante getEstudiante() { return estudiante; }
    public void setEstudiante(Estudiante estudiante) { this.estudiante = estudiante; }

    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }

    public String getNombreArchivo() { return nombreArchivo; }
    public void setNombreArchivo(String nombreArchivo) { this.nombreArchivo = nombreArchivo; }

    public String getTipoContenido() { return tipoContenido; }
    public void setTipoContenido(String tipoContenido) { this.tipoContenido = tipoContenido; }

    public Integer getTamanoBytes() { return tamanoBytes; }
    public void setTamanoBytes(Integer tamanoBytes) { this.tamanoBytes = tamanoBytes; }

    public String getComentario() { return comentario; }
    public void setComentario(String comentario) { this.comentario = comentario; }

    public LocalDateTime getFechaEnvio() { return fechaEnvio; }
    public void setFechaEnvio(LocalDateTime fechaEnvio) { this.fechaEnvio = fechaEnvio; }

    public LocalDateTime getFechaRevision() { return fechaRevision; }
    public void setFechaRevision(LocalDateTime fechaRevision) { this.fechaRevision = fechaRevision; }

    public Usuario getRevisor() { return revisor; }
    public void setRevisor(Usuario revisor) { this.revisor = revisor; }

    public String getMotivoRevision() { return motivoRevision; }
    public void setMotivoRevision(String motivoRevision) { this.motivoRevision = motivoRevision; }
}
