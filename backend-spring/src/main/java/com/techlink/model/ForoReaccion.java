package com.techlink.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Una reacción de una persona a una consulta o a una respuesta del foro (solo una por persona y objeto).
@Entity
@Table(name = "foro_reacciones")
public class ForoReaccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_reaccion")
    private Integer id;

    @Column(name = "id_usuario", nullable = false)
    private Integer idUsuario;

    @Column(nullable = false, length = 12)
    private String objeto;

    @Column(name = "id_objeto", nullable = false)
    private Integer idObjeto;

    @Column(nullable = false, length = 20)
    private String reaccion;

    public ForoReaccion() {
    }

    public ForoReaccion(Integer idUsuario, String objeto, Integer idObjeto, String reaccion) {
        this.idUsuario = idUsuario;
        this.objeto = objeto;
        this.idObjeto = idObjeto;
        this.reaccion = reaccion;
    }

    public Integer getId() { return id; }
    public Integer getIdUsuario() { return idUsuario; }
    public String getObjeto() { return objeto; }
    public Integer getIdObjeto() { return idObjeto; }
    public String getReaccion() { return reaccion; }
    public void setReaccion(String reaccion) { this.reaccion = reaccion; }
}
