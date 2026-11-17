
package com.techlink.dto;

import java.time.LocalDate;
import java.time.LocalTime;

// HU-07: datos necesarios para solicitar una mentoría.
public class AgendarSesionRequest {

    private Integer mentorId;
    private Integer disponibilidadId;

    private LocalDate fecha;
    private LocalTime horaInicio;
    private LocalTime horaFin;

    private String enlaceReunion;

    public AgendarSesionRequest() {
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

    public String getEnlaceReunion() {
        return enlaceReunion;
    }

    public void setEnlaceReunion(String enlaceReunion) {
        this.enlaceReunion = enlaceReunion;
    }
}
