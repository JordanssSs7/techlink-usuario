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
import java.time.LocalDateTime;

// HU-27: la constancia de un mentor. Queda PENDIENTE cuando llega a las horas mínimas y EMITIDA cuando un administrador la
// emite desde el panel; solo entonces el mentor puede descargarla.
@Entity
@Table(name = "constancias")
public class ConstanciaMentor {

    public enum Estado {
        PENDIENTE,
        EMITIDA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_constancia")
    private Integer id;

    @Column(name = "id_mentor", nullable = false, unique = true)
    private Integer idMentor;

    @Column(name = "horas_al_cumplir", nullable = false, precision = 7, scale = 2)
    private BigDecimal horasAlCumplir;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Estado estado = Estado.PENDIENTE;

    @Column(name = "fecha_elegible", nullable = false, insertable = false, updatable = false)
    private LocalDateTime fechaElegible;

    @Column(name = "fecha_emision")
    private LocalDateTime fechaEmision;

    @Column(name = "id_admin")
    private Integer idAdmin;

    // Lo genera el panel al emitir la constancia; sirve para comprobar su autenticidad en la página pública de verificación.
    @Column(name = "codigo_verificacion", length = 20)
    private String codigoVerificacion;

    public ConstanciaMentor() {
    }

    public ConstanciaMentor(Integer idMentor, BigDecimal horasAlCumplir) {
        this.idMentor = idMentor;
        this.horasAlCumplir = horasAlCumplir;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getIdMentor() { return idMentor; }

    public BigDecimal getHorasAlCumplir() { return horasAlCumplir; }

    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }

    public LocalDateTime getFechaElegible() { return fechaElegible; }

    public LocalDateTime getFechaEmision() { return fechaEmision; }
    public void setFechaEmision(LocalDateTime fechaEmision) { this.fechaEmision = fechaEmision; }

    public Integer getIdAdmin() { return idAdmin; }
    public void setIdAdmin(Integer idAdmin) { this.idAdmin = idAdmin; }

    public String getCodigoVerificacion() { return codigoVerificacion; }
    public void setCodigoVerificacion(String codigoVerificacion) { this.codigoVerificacion = codigoVerificacion; }
}
