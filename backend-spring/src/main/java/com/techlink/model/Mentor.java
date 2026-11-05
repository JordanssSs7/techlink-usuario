package com.techlink.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// HU-05: entidad de la tabla "mentores": perfil extra de un usuario con rol MENTOR
// (fortalezas, cursos dominados, areas de conocimiento y disponibilidad).
@Entity
@Table(name = "mentores")
public class Mentor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_mentor")
    private Integer id;

    // Relacion uno a uno con el usuario dueño de este perfil.
    @OneToOne(optional = false)
    @JoinColumn(name = "id_usuario", nullable = false, unique = true)
    private Usuario usuario;

    @Column(length = 150)
    private String especialidad;

    @Column(columnDefinition = "TEXT")
    private String biografia;

    // Cursos que domina (tabla intermedia mentor_cursos).
    @ManyToMany
    @JoinTable(name = "mentor_cursos",
            joinColumns = @JoinColumn(name = "id_mentor"),
            inverseJoinColumns = @JoinColumn(name = "id_curso"))
    private Set<Curso> cursos = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "mentor_fortalezas", joinColumns = @JoinColumn(name = "id_mentor"))
    @Column(name = "fortaleza", nullable = false, length = 100)
    private Set<String> fortalezas = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "mentor_areas", joinColumns = @JoinColumn(name = "id_mentor"))
    @Column(name = "area", nullable = false, length = 100)
    private Set<String> areas = new LinkedHashSet<>();

    // Al reemplazar la lista, los horarios que ya no estan se borran de la base (orphanRemoval).
    @OneToMany(mappedBy = "mentor", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MentorDisponibilidad> disponibilidad = new ArrayList<>();

    public Mentor() {
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public String getEspecialidad() { return especialidad; }
    public void setEspecialidad(String especialidad) { this.especialidad = especialidad; }

    public String getBiografia() { return biografia; }
    public void setBiografia(String biografia) { this.biografia = biografia; }

    public Set<Curso> getCursos() { return cursos; }
    public void setCursos(Set<Curso> cursos) { this.cursos = cursos; }

    public Set<String> getFortalezas() { return fortalezas; }
    public void setFortalezas(Set<String> fortalezas) { this.fortalezas = fortalezas; }

    public Set<String> getAreas() { return areas; }
    public void setAreas(Set<String> areas) { this.areas = areas; }

    public List<MentorDisponibilidad> getDisponibilidad() { return disponibilidad; }
    public void setDisponibilidad(List<MentorDisponibilidad> disponibilidad) { this.disponibilidad = disponibilidad; }
}
