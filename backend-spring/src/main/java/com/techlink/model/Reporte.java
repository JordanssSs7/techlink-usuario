package com.techlink.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

// HU-29: reporte de un usuario o de una sesión. El administrador lo revisa desde el panel (HU-52).
@Entity
@Table(name = "reportes")
public class Reporte {

    public enum Tipo { USUARIO, SESION, CONSULTA, RESPUESTA }

    public enum Motivo { CONDUCTA_INADECUADA, ACOSO, CONTENIDO_OFENSIVO, INCUMPLIMIENTO, SPAM, OTRO }

    public enum Estado { PENDIENTE, RESUELTO, DESCARTADO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_reporte")
    private Integer id;

    @Column(name = "id_reportante", nullable = false)
    private Integer idReportante;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tipo tipo;

    @Column(name = "id_usuario_reportado", nullable = false)
    private Integer idUsuarioReportado;

    @Column(name = "id_sesion")
    private Integer idSesion;

    // Reportes del foro: la consulta o la respuesta reportada y lo que decía al reportarla.
    @Column(name = "id_consulta")
    private Integer idConsulta;

    @Column(name = "id_respuesta")
    private Integer idRespuesta;

    @Column(name = "contenido_reportado", length = 500)
    private String contenidoReportado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Motivo motivo;

    @Column(nullable = false, length = 500)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado = Estado.PENDIENTE;

    @Column(name = "fecha_creacion", nullable = false, insertable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    public Reporte() {
    }

    public Integer getId() { return id; }

    public Integer getIdReportante() { return idReportante; }
    public void setIdReportante(Integer idReportante) { this.idReportante = idReportante; }

    public Tipo getTipo() { return tipo; }
    public void setTipo(Tipo tipo) { this.tipo = tipo; }

    public Integer getIdUsuarioReportado() { return idUsuarioReportado; }
    public void setIdUsuarioReportado(Integer idUsuarioReportado) { this.idUsuarioReportado = idUsuarioReportado; }

    public Integer getIdConsulta() { return idConsulta; }
    public void setIdConsulta(Integer idConsulta) { this.idConsulta = idConsulta; }

    public Integer getIdRespuesta() { return idRespuesta; }
    public void setIdRespuesta(Integer idRespuesta) { this.idRespuesta = idRespuesta; }

    public String getContenidoReportado() { return contenidoReportado; }
    public void setContenidoReportado(String contenidoReportado) { this.contenidoReportado = contenidoReportado; }

    public Integer getIdSesion() { return idSesion; }
    public void setIdSesion(Integer idSesion) { this.idSesion = idSesion; }

    public Motivo getMotivo() { return motivo; }
    public void setMotivo(Motivo motivo) { this.motivo = motivo; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public Estado getEstado() { return estado; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
}
