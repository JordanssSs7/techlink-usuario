package com.techlink.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Una consulta del foro que una persona guardó para volver a verla.
@Entity
@Table(name = "foro_guardados")
public class ForoGuardado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_guardado")
    private Integer id;

    @Column(name = "id_usuario", nullable = false)
    private Integer idUsuario;

    @Column(name = "id_consulta", nullable = false)
    private Integer idConsulta;

    public ForoGuardado() {
    }

    public ForoGuardado(Integer idUsuario, Integer idConsulta) {
        this.idUsuario = idUsuario;
        this.idConsulta = idConsulta;
    }

    public Integer getId() { return id; }
    public Integer getIdUsuario() { return idUsuario; }
    public Integer getIdConsulta() { return idConsulta; }
}
