package com.techlink.service;

import com.techlink.model.Estudiante;
import com.techlink.model.Mentor;
import com.techlink.model.MentorDisponibilidad;
import com.techlink.model.Sesion;
import com.techlink.model.Usuario;

import java.time.LocalDateTime;
import java.time.LocalTime;

// Datos de prueba compartidos por las pruebas del ciclo de la sesión (HU-09, HU-19, HU-20 y HU-21).
final class PruebaSesiones {

    static final Integer USUARIO_ESTUDIANTE = 10;
    static final Integer USUARIO_MENTOR = 20;
    static final Integer ID_MENTOR = 5;

    private PruebaSesiones() {
    }

    static Usuario usuario(Integer id, String nombres, String apellidos, String correo) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setNombres(nombres);
        u.setApellidos(apellidos);
        u.setCorreoInstitucional(correo);
        return u;
    }

    static Mentor mentor() {
        Mentor m = new Mentor();
        m.setId(ID_MENTOR);
        m.setUsuario(usuario(USUARIO_MENTOR, "Lucía", "Ramírez", "lucia@tecsup.edu.pe"));
        return m;
    }

    // Una sesión que empieza en "inicio" y dura "minutos". Usa la hora actual como referencia.
    static Sesion sesion(int id, LocalDateTime inicio, int minutos, Sesion.EstadoSesion estado) {
        Estudiante e = new Estudiante();
        e.setUsuario(usuario(USUARIO_ESTUDIANTE, "Ana", "Quispe", "ana@tecsup.edu.pe"));

        Mentor mentor = mentor();
        MentorDisponibilidad d = new MentorDisponibilidad();
        d.setId(1);
        d.setMentor(mentor);
        d.setActivo(true);
        d.setDiaSemana(inicio.getDayOfWeek().getValue());
        d.setHoraInicio(LocalTime.of(0, 0));
        d.setHoraFin(LocalTime.of(23, 59));

        Sesion s = new Sesion();
        s.setId(id);
        s.setEstudiante(e);
        s.setMentor(mentor);
        s.setDisponibilidad(d);
        s.setFecha(inicio.toLocalDate());
        s.setHoraInicio(inicio.toLocalTime());
        // Si la sesión cruzara la medianoche, termina a las 23:59 para que la fecha y las horas sigan siendo coherentes.
        LocalDateTime fin = inicio.plusMinutes(minutos);
        s.setHoraFin(fin.toLocalDate().equals(inicio.toLocalDate()) ? fin.toLocalTime() : LocalTime.of(23, 59, 59));
        s.setEstado(estado);
        s.setEnlaceReunion("https://meet.google.com/prueba");
        return s;
    }
}
