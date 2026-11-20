package com.techlink.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// HU-18: el contenido de un recurso de tipo ARCHIVO (hasta 10 MB). El id es el del recurso.
@Entity
@Table(name = "recurso_archivos")
public class RecursoArchivo {

    @Id
    @Column(name = "id_recurso")
    private Integer idRecurso;

    @JdbcTypeCode(SqlTypes.LONGVARBINARY)
    @Column(nullable = false, columnDefinition = "MEDIUMBLOB")
    private byte[] contenido;

    public RecursoArchivo() {
    }

    public Integer getIdRecurso() { return idRecurso; }
    public void setIdRecurso(Integer idRecurso) { this.idRecurso = idRecurso; }

    public byte[] getContenido() { return contenido; }
    public void setContenido(byte[] contenido) { this.contenido = contenido; }
}
