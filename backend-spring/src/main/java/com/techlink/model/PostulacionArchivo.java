package com.techlink.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// HU-03: el comprobante de cursos aprobados (PDF o imagen, máximo 5 MB). El id es el de la postulación.
@Entity
@Table(name = "postulacion_archivos")
public class PostulacionArchivo {

    @Id
    @Column(name = "id_postulacion")
    private Integer idPostulacion;

    @JdbcTypeCode(SqlTypes.LONGVARBINARY)
    @Column(nullable = false, columnDefinition = "MEDIUMBLOB")
    private byte[] contenido;

    public PostulacionArchivo() {
    }

    public Integer getIdPostulacion() { return idPostulacion; }
    public void setIdPostulacion(Integer idPostulacion) { this.idPostulacion = idPostulacion; }

    public byte[] getContenido() { return contenido; }
    public void setContenido(byte[] contenido) { this.contenido = contenido; }
}
