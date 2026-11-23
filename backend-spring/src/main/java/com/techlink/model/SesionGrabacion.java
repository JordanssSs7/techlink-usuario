package com.techlink.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

// La grabación de una sesión: un archivo de video que el navegador del mentor va mandando por partes.
@Entity
@Table(name = "sesion_grabaciones")
public class SesionGrabacion {

    public static final String EN_CURSO = "EN_CURSO";
    public static final String LISTA = "LISTA";

    @Id
    @Column(name = "id_sesion")
    private Integer idSesion;

    @Column(nullable = false, length = 200)
    private String archivo;

    @Column(nullable = false)
    private long bytes;

    @Column(nullable = false)
    private int partes;

    @Column(name = "duracion_seg")
    private Integer duracionSeg;

    @Column(nullable = false, length = 10)
    private String estado = EN_CURSO;

    @Column(name = "fecha_inicio", insertable = false, updatable = false)
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDateTime fechaFin;

    public SesionGrabacion() {
    }

    public SesionGrabacion(Integer idSesion, String archivo) {
        this.idSesion = idSesion;
        this.archivo = archivo;
    }

    public Integer getIdSesion() { return idSesion; }
    public String getArchivo() { return archivo; }
    public long getBytes() { return bytes; }
    public void setBytes(long bytes) { this.bytes = bytes; }
    public int getPartes() { return partes; }
    public void setPartes(int partes) { this.partes = partes; }
    public Integer getDuracionSeg() { return duracionSeg; }
    public void setDuracionSeg(Integer duracionSeg) { this.duracionSeg = duracionSeg; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public LocalDateTime getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDateTime fechaFin) { this.fechaFin = fechaFin; }
}
