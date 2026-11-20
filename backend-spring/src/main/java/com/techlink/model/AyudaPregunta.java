package com.techlink.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// HU-30: una pregunta frecuente con su respuesta, dentro de un tema del centro de ayuda.
@Entity
@Table(name = "ayuda_preguntas")
public class AyudaPregunta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pregunta")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tema", nullable = false)
    private AyudaTema tema;

    @Column(nullable = false, length = 200)
    private String pregunta;

    @Column(nullable = false, length = 1500)
    private String respuesta;

    @Column(nullable = false)
    private Integer orden = 0;

    @Column(nullable = false)
    private boolean activa = true;

    public AyudaPregunta() {
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public AyudaTema getTema() { return tema; }
    public void setTema(AyudaTema tema) { this.tema = tema; }

    public String getPregunta() { return pregunta; }
    public void setPregunta(String pregunta) { this.pregunta = pregunta; }

    public String getRespuesta() { return respuesta; }
    public void setRespuesta(String respuesta) { this.respuesta = respuesta; }

    public Integer getOrden() { return orden; }
    public void setOrden(Integer orden) { this.orden = orden; }

    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }
}
