
package com.techlink.service;

import com.techlink.model.Notificacion;
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
    private final PreferenciaNotificacionService preferencias;


    public SesionNotificacionService(
            JavaMailSender mailSender,
            @Value("${spring.mail.username}") String correoRemitente,
            @Value("${app.sesiones.correo-pruebas:}") String correoPruebas,
            PreferenciaNotificacionService preferencias
    ) {
        this.mailSender = mailSender;
        this.correoRemitente = correoRemitente;
        this.correoPruebas = correoPruebas;
        this.preferencias = preferencias;
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

        // HU-24: solo se envía a quien no desactivó los correos de sus sesiones.
        enviarSiPermite(correoEstudiante, "TechLink - Confirmacion de sesion de mentoria", mensaje);
        enviarSiPermite(correoMentor, "TechLink - Confirmacion de sesion de mentoria", mensaje);
    }

    // HU-23: recordatorio previo a la sesión, con fecha, hora y enlace de la reunión. Respeta las preferencias (HU-24).
    // Devuelve false si el destinatario desactivó los recordatorios por correo.
    public boolean enviarRecordatorio(String correo, String nombre, String horasAntes, LocalDate fecha,
                                      LocalTime horaInicio, LocalTime horaFin, String enlaceReunion) {
        String mensaje = """
                Hola %s,

                Te recordamos que tu sesion de mentoria en TechLink empieza en %s.

                Fecha: %s
                Hora de inicio: %s
                Hora de finalizacion: %s

                Enlace de reunion:
                %s
                """.formatted(nombre, horasAntes, fecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                horaInicio.format(DateTimeFormatter.ofPattern("HH:mm")),
                horaFin.format(DateTimeFormatter.ofPattern("HH:mm")), enlaceReunion);
        return enviarSiPermite(correo, "TechLink - Recordatorio de tu sesion de mentoria", mensaje, Notificacion.Tipo.RECORDATORIO);
    }

    // HU-19: avisa al estudiante y al mentor que la sesión se canceló o cambió de horario.
    public void enviarCambio(SesionModificadaEvent evento) {
        DateTimeFormatter formatoFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter formatoHora = DateTimeFormatter.ofPattern("HH:mm");

        String asunto;
        String mensaje;
        if (evento.tipo() == SesionModificadaEvent.Tipo.CANCELADA) {
            asunto = "TechLink - Sesion de mentoria cancelada";
            mensaje = """
                    La sesion de mentoria del %s a las %s fue cancelada.

                    El horario del mentor quedo libre nuevamente.
                    """.formatted(
                    evento.fechaAnterior().format(formatoFecha),
                    evento.horaInicioAnterior().format(formatoHora));
        } else {
            asunto = "TechLink - Sesion de mentoria reprogramada";
            mensaje = """
                    La sesion de mentoria del %s a las %s fue reprogramada.

                    Nueva fecha: %s
                    Hora de inicio: %s
                    Hora de finalizacion: %s

                    Enlace de reunion:
                    %s
                    """.formatted(
                    evento.fechaAnterior().format(formatoFecha),
                    evento.horaInicioAnterior().format(formatoHora),
                    evento.fecha().format(formatoFecha),
                    evento.horaInicio().format(formatoHora),
                    evento.horaFin().format(formatoHora),
                    evento.enlaceReunion());
        }

        enviarSiPermite(evento.correoEstudiante(), asunto, mensaje);
        enviarSiPermite(evento.correoMentor(), asunto, mensaje);
    }

    private boolean enviarSiPermite(String destinatario, String asunto, String mensaje) {
        return enviarSiPermite(destinatario, asunto, mensaje, Notificacion.Tipo.SESION);
    }

    // HU-24: consulta las preferencias del destinatario antes de enviar.
    private boolean enviarSiPermite(String destinatario, String asunto, String mensaje, Notificacion.Tipo tipo) {
        if (destinatario != null && !preferencias.permiteCorreoA(destinatario, tipo)) {
            return false;
        }
        enviarCorreo(destinatario, asunto, mensaje);
        return true;
    }

    private void enviarCorreo(
            String destinatario,
            String asunto,
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
        correo.setSubject(asunto);
        correo.setText(mensaje);

        mailSender.send(correo);
    }
}
