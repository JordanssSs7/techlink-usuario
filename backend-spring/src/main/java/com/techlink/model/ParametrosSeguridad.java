package com.techlink.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

// HU-60: los parámetros de seguridad del sistema. Hay una sola fila (id 1); el administrador la cambia desde el panel.
@Entity
@Table(name = "parametros_seguridad")
public class ParametrosSeguridad {

    public static final int ID = 1;

    @Id
    @Column(name = "id_parametros")
    private Integer id = ID;

    @Column(name = "dominio_permitido", nullable = false, length = 100)
    private String dominioPermitido;

    @Column(name = "max_intentos", nullable = false)
    private Integer maxIntentos;

    @Column(name = "minutos_bloqueo", nullable = false)
    private Integer minutosBloqueo;

    @Column(name = "id_admin")
    private Integer idAdmin;

    @Column(name = "fecha_actualizacion", nullable = false, insertable = false, updatable = false)
    private LocalDateTime fechaActualizacion;

    public ParametrosSeguridad() {
    }

    public Integer getId() { return id; }

    public String getDominioPermitido() { return dominioPermitido; }
    public void setDominioPermitido(String dominioPermitido) { this.dominioPermitido = dominioPermitido; }

    public Integer getMaxIntentos() { return maxIntentos; }
    public void setMaxIntentos(Integer maxIntentos) { this.maxIntentos = maxIntentos; }

    public Integer getMinutosBloqueo() { return minutosBloqueo; }
    public void setMinutosBloqueo(Integer minutosBloqueo) { this.minutosBloqueo = minutosBloqueo; }

    public Integer getIdAdmin() { return idAdmin; }
    public void setIdAdmin(Integer idAdmin) { this.idAdmin = idAdmin; }

    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
}
