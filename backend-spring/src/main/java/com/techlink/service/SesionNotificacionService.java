
package com.techlink.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Service
public class SesionNotificacionService {

    private final JavaMailSender mailSender;
    private final String correoRemitente;
    private final String correoPruebas;


    public SesionNotificacionService(
            JavaMailSender mailSender,
            @Value("${spring.mail.username}") String correoRemitente,
            @Value("${app.sesiones.correo-pruebas:}") String correoPruebas
    ) {
        this.mailSender = mailSender;
        this.correoRemitente = correoRemitente;
        this.correoPruebas = correoPruebas;
    }

    public void enviarConfirmacion(
            String correoEstudiante,
            String correoMentor,
            LocalDate fecha,
            LocalTime horaInicio,
            LocalTime horaFin,
            String enlaceReunion
    ) {
        DateTimeFormatter formatoFecha =
                DateTimeFormatter.ofPattern("dd/MM/yyyy");

        DateTimeFormatter formatoHora =
                DateTimeFormatter.ofPattern("HH:mm");

        String mensaje = """
                Tu sesion de mentoria en TechLink ha sido confirmada.

                Fecha: %s
                Hora de inicio: %s
                Hora de finalizacion: %s

                Enlace de reunion:
                %s

                Gracias por utilizar TechLink.
                """.formatted(
                fecha.format(formatoFecha),
                horaInicio.format(formatoHora),
                horaFin.format(formatoHora),
                enlaceReunion
        );

        enviarCorreo(correoEstudiante, mensaje);
        enviarCorreo(correoMentor, mensaje);
    }

    private void enviarCorreo(
            String destinatario,
            String mensaje
    ) {
        if (destinatario == null || destinatario.isBlank()) {
            throw new IllegalArgumentException(
                    "El correo del destinatario es obligatorio."
            );
        }

        SimpleMailMessage correo = new SimpleMailMessage();

        correo.setFrom(correoRemitente);

        String destinatarioFinal = destinatario;

        if (correoPruebas != null && !correoPruebas.isBlank()) {
            destinatarioFinal = correoPruebas;
        }

        correo.setTo(destinatarioFinal);
        correo.setSubject(
                "TechLink - Confirmacion de sesion de mentoria"
        );
        correo.setText(mensaje);

        mailSender.send(correo);
    }
}
