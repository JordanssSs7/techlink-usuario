
package com.techlink.dto;

import java.time.LocalDate;
import java.time.LocalTime;

// HU-07: respuesta de una sesión de mentoría.
public class SesionResponse {

    private Integer id;
    private Integer estudianteId;
    private Integer mentorId;
    private Integer disponibilidadId;

    private LocalDate fecha;
    private LocalTime horaInicio;
    private LocalTime horaFin;

    private String estado;
    private String enlaceReunion;

    public SesionResponse() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getEstudianteId() {
        return estudianteId;
    }

    public void setEstudianteId(Integer estudianteId) {
        this.estudianteId = estudianteId;
    }

    public Integer getMentorId() {
        return mentorId;
    }

    public void setMentorId(Integer mentorId) {
        this.mentorId = mentorId;
    }

    public Integer getDisponibilidadId() {
        return disponibilidadId;
    }

    public void setDisponibilidadId(Integer disponibilidadId) {
        this.disponibilidadId = disponibilidadId;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public void setHoraInicio(LocalTime horaInicio) {
        this.horaInicio = horaInicio;
    }

    public LocalTime getHoraFin() {
        return horaFin;
    }

    public void setHoraFin(LocalTime horaFin) {
        this.horaFin = horaFin;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getEnlaceReunion() {
        return enlaceReunion;
    }

    public void setEnlaceReunion(String enlaceReunion) {
        this.enlaceReunion = enlaceReunion;
    }
}
