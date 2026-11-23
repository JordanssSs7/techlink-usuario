package com.techlink.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

// Una solicitud de amistad entre dos usuarios: PENDIENTE hasta que el destinatario la acepta (ACEPTADA).
@Entity
@Table(name = "amistades")
public class Amistad {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String ACEPTADA = "ACEPTADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_amistad")
    private Integer id;

    @Column(name = "id_solicitante", nullable = false)
    private Integer idSolicitante;

    @Column(name = "id_destinatario", nullable = false)
    private Integer idDestinatario;

    @Column(nullable = false, length = 10)
    private String estado = PENDIENTE;

    @Column(name = "fecha_solicitud", insertable = false, updatable = false)
    private LocalDateTime fechaSolicitud;

    @Column(name = "fecha_respuesta")
    private LocalDateTime fechaRespuesta;

    public Amistad() {
    }

    public Amistad(Integer idSolicitante, Integer idDestinatario) {
        this.idSolicitante = idSolicitante;
        this.idDestinatario = idDestinatario;
    }

    public Integer getId() { return id; }
    public Integer getIdSolicitante() { return idSolicitante; }
    public Integer getIdDestinatario() { return idDestinatario; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public LocalDateTime getFechaSolicitud() { return fechaSolicitud; }
    public LocalDateTime getFechaRespuesta() { return fechaRespuesta; }
    public void setFechaRespuesta(LocalDateTime fechaRespuesta) { this.fechaRespuesta = fechaRespuesta; }

    public Integer otro(Integer yo) {
        return yo.equals(idSolicitante) ? idDestinatario : idSolicitante;
    }
}
