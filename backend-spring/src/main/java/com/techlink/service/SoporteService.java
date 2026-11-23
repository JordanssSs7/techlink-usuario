package com.techlink.service;

import com.techlink.exception.DatosInvalidosException;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.Usuario;
import com.techlink.repository.UsuarioRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

// Contactar a soporte desde la propia página: la persona escribe el asunto y el mensaje, y le llegan al correo de soporte de TechLink
// con su correo como "responder a", para que soporte le conteste directo. Usa el mismo correo que envía los recordatorios.
@Service
public class SoporteService {

    static final int MIN_ASUNTO = 5;
    static final int MAX_ASUNTO = 100;
    static final int MIN_MENSAJE = 10;
    static final int MAX_MENSAJE = 2000;

    private static final Logger logger = LoggerFactory.getLogger(SoporteService.class);

    private static final String NL = System.lineSeparator();

    private final JavaMailSender mailSender;
    private final UsuarioRepository usuarioRepository;
    private final String correoSoporte;
    private final String remitente;
    private final LimitadorConsultas limitador;
    private final java.util.concurrent.Executor envio = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread hilo = new Thread(r, "correo-soporte");
        hilo.setDaemon(true);
        return hilo;
    });

    public SoporteService(JavaMailSender mailSender, UsuarioRepository usuarioRepository,
                          @Value("${app.ayuda.correo-soporte:soporte.techlink.app@gmail.com}") String correoSoporte,
                          @Value("${spring.mail.username:}") String remitente) {
        this.mailSender = mailSender;
        this.usuarioRepository = usuarioRepository;
        this.correoSoporte = correoSoporte;
        this.remitente = remitente;
        this.limitador = new LimitadorConsultas(5, Clock.systemUTC()); // 5 mensajes por hora y persona
    }

    public void enviar(Integer usuarioId, String asuntoCrudo, String mensajeCrudo) {
        String asunto = limpiar(asuntoCrudo).replaceAll("\\s+", " ");
        String mensaje = limpiar(mensajeCrudo);
        Map<String, String> errores = new LinkedHashMap<>();
        if (asunto.length() < MIN_ASUNTO || asunto.length() > MAX_ASUNTO) {
            errores.put("asunto", "El asunto debe tener entre " + MIN_ASUNTO + " y " + MAX_ASUNTO + " caracteres.");
        }
        if (mensaje.length() < MIN_MENSAJE || mensaje.length() > MAX_MENSAJE) {
            errores.put("mensaje", "Cuéntanos tu problema con entre " + MIN_MENSAJE + " y " + MAX_MENSAJE + " caracteres.");
        }
        if (!errores.isEmpty()) {
            throw new DatosInvalidosException(errores);
        }
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "El usuario no existe."));
        if (!limitador.intentar(usuarioId)) {
            throw new ReglaNegocioException(HttpStatus.TOO_MANY_REQUESTS,
                    "Ya enviaste varios mensajes. Espera un rato antes de escribir de nuevo.");
        }
        // El correo se manda en segundo plano: Gmail puede tardar varios segundos y la persona no tiene que esperar.
        String de = SalaMensajeService.nombreDe(usuario);
        String correoUsuario = usuario.getCorreoInstitucional();
        String cuerpo = "De: " + de + " <" + correoUsuario + ">" + NL
                + "Rol: " + usuario.getRol().getNombre() + " · Id de usuario: " + usuario.getId() + NL + NL + mensaje;
        envio.execute(() -> {
            try {
                MimeMessage correo = mailSender.createMimeMessage();
                MimeMessageHelper ayudante = new MimeMessageHelper(correo, false, "UTF-8");
                if (!remitente.isBlank()) {
                    ayudante.setFrom(remitente);
                }
                ayudante.setTo(correoSoporte);
                ayudante.setReplyTo(correoUsuario);
                ayudante.setSubject("[TechLink soporte] " + asunto);
                ayudante.setText(cuerpo);
                mailSender.send(correo);
            } catch (Exception e) {
                logger.error("No se pudo enviar el mensaje de soporte del usuario {}", usuarioId, e);
            }
        });
    }

    private static String limpiar(String texto) {
        return texto == null ? "" : texto.trim();
    }
}
