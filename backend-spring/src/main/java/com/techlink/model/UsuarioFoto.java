package com.techlink.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

// HU-13: foto de perfil de un usuario (JPG o PNG, máximo 2 MB). El id es el del usuario dueño de la foto.
@Entity
@Table(name = "usuario_fotos")
public class UsuarioFoto {

    @Id
    @Column(name = "id_usuario")
    private Integer idUsuario;

    @Column(name = "tipo_contenido", nullable = false, length = 30)
    private String tipoContenido;

    @JdbcTypeCode(SqlTypes.LONGVARBINARY)
    @Column(nullable = false, columnDefinition = "MEDIUMBLOB")
    private byte[] contenido;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    public UsuarioFoto() {
    }

    public Integer getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Integer idUsuario) { this.idUsuario = idUsuario; }

    public String getTipoContenido() { return tipoContenido; }
    public void setTipoContenido(String tipoContenido) { this.tipoContenido = tipoContenido; }

    public byte[] getContenido() { return contenido; }
    public void setContenido(byte[] contenido) { this.contenido = contenido; }

    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(LocalDateTime fechaActualizacion) { this.fechaActualizacion = fechaActualizacion; }
}
