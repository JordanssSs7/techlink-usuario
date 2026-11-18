package com.techlink.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// HU-03: avisa por correo al estudiante que recibimos su postulación. Si el correo falla (por ejemplo, sin configurar),
// la postulación ya quedó guardada y solo se registra el error.
@Component
public class PostulacionNotificacionService {

    private static final Logger logger = LoggerFactory.getLogger(PostulacionNotificacionService.class);

    private final JavaMailSender mailSender;
    private final String correoRemitente;
    private final String correoPruebas;

    public PostulacionNotificacionService(JavaMailSender mailSender,
                                          @Value("${spring.mail.username:}") String correoRemitente,
                                          @Value("${app.sesiones.correo-pruebas:}") String correoPruebas) {
        this.mailSender = mailSender;
        this.correoRemitente = correoRemitente;
        this.correoPruebas = correoPruebas;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alRecibirPostulacion(PostulacionRecibidaEvent evento) {
        try {
            SimpleMailMessage correo = new SimpleMailMessage();
            correo.setFrom(correoRemitente);
            // En desarrollo se puede redirigir todo a un correo de pruebas (igual que las sesiones).
            correo.setTo(correoPruebas != null && !correoPruebas.isBlank() ? correoPruebas : evento.correo());
            correo.setSubject("TechLink - Recibimos tu postulación a mentor");
            correo.setText("""
                    Hola %s,

                    Recibimos tu postulación para ser mentor en TechLink y tu comprobante de cursos aprobados.
                    Su estado es: EN REVISIÓN.

                    Un administrador la evaluará y te avisaremos el resultado.

                    Gracias por querer guiar a otros alumnos.
                    """.formatted(evento.nombres()));
            mailSender.send(correo);
            logger.info("Aviso de postulacion recibida enviado para la postulacion {}", evento.postulacionId());
        } catch (Exception e) {
            logger.error("No se pudo avisar la recepcion de la postulacion {}", evento.postulacionId(), e);
        }
    }
}
