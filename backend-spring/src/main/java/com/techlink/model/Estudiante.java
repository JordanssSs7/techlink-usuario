package com.techlink.model;

import jakarta.persistence.*;

import java.util.LinkedHashSet;
import java.util.Set;

// Entidad que representa la tabla "estudiantes": perfil extra de un usuario con rol ESTUDIANTE.
@Entity
@Table(name = "estudiantes")
public class Estudiante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_estudiante")
    private Integer id;

    // Relación uno a uno con el usuario dueño de este perfil.
    @OneToOne(optional = false)
    @JoinColumn(name = "id_usuario", nullable = false, unique = true)
    private Usuario usuario;

    @Column(name = "codigo_estudiante", unique = true, length = 20)
    private String codigoEstudiante;

    private Integer ciclo;

    // HU-04: cursos que el estudiante quiere reforzar (tabla intermedia estudiante_cursos_criticos).
    @ManyToMany
    @JoinTable(name = "estudiante_cursos_criticos",
            joinColumns = @JoinColumn(name = "id_estudiante"),
            inverseJoinColumns = @JoinColumn(name = "id_curso"))
    private Set<Curso> cursosCriticos = new LinkedHashSet<>();

    public Estudiante() {
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public String getCodigoEstudiante() { return codigoEstudiante; }
    public void setCodigoEstudiante(String codigoEstudiante) { this.codigoEstudiante = codigoEstudiante; }

    public Set<Curso> getCursosCriticos() { return cursosCriticos; }
    public void setCursosCriticos(Set<Curso> cursosCriticos) { this.cursosCriticos = cursosCriticos; }

    public Integer getCiclo() { return ciclo; }
    public void setCiclo(Integer ciclo) { this.ciclo = ciclo; }
}
