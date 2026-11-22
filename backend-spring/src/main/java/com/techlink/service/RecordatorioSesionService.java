package com.techlink.service;

import com.techlink.model.Notificacion;
import com.techlink.model.Sesion;
import com.techlink.model.SesionRecordatorio;
import com.techlink.model.SesionRecordatorio.Momento;
import com.techlink.model.Usuario;
import com.techlink.repository.SesionRecordatorioRepository;
import com.techlink.repository.SesionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// HU-23: recordatorios de las sesiones próximas, por correo y en el centro de notificaciones.
//  - Cuando falta menos de 24 horas (y más de 1) se envía el aviso H24; cuando falta menos de 1 hora, el H1.
//  - Solo se avisa de sesiones CONFIRMADAS: una sesión cancelada no genera recordatorios.
//  - Cada recordatorio se manda una sola vez por horario (si la sesión se reprograma, se avisa del nuevo horario).
//  - Cada destinatario recibe solo lo que no desactivó en sus preferencias (HU-24).
@Service
public class RecordatorioSesionService {

    private static final Logger logger = LoggerFactory.getLogger(RecordatorioSesionService.class);

    private final SesionRepository sesionRepository;
    private final SesionRecordatorioRepository recordatorioRepository;
    private final NotificacionService notificaciones;
    private final SesionNotificacionService correos;
    private final Clock reloj;

    @Autowired
    public RecordatorioSesionService(SesionRepository sesionRepository,
                                     SesionRecordatorioRepository recordatorioRepository,
                                     NotificacionService notificaciones,
                                     SesionNotificacionService correos) {
        this(sesionRepository, recordatorioRepository, notificaciones, correos, Clock.systemDefaultZone());
    }

    // El reloj se puede cambiar solo en las pruebas automaticas.
    RecordatorioSesionService(SesionRepository sesionRepository,
                              SesionRecordatorioRepository recordatorioRepository,
                              NotificacionService notificaciones,
                              SesionNotificacionService correos,
                              Clock reloj) {
        this.sesionRepository = sesionRepository;
        this.recordatorioRepository = recordatorioRepository;
        this.notificaciones = notificaciones;
        this.correos = correos;
        this.reloj = reloj;
    }

    // Envía los recordatorios que toquen en este momento y devuelve cuántos (sesión, momento) procesó.
    @Transactional
    public int enviarPendientes() {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        int enviados = 0;
        for (Sesion sesion : sesionRepository.confirmadasEntre(ahora.toLocalDate(), ahora.toLocalDate().plusDays(1))) {
            LocalDateTime inicio = LocalDateTime.of(sesion.getFecha(), sesion.getHoraInicio());
            Momento momento = momento(inicio, ahora);
            if (sesion.getEstado() != Sesion.EstadoSesion.CONFIRMADA || momento == null
                    || recordatorioRepository.existsByIdSesionAndMomentoAndInicioSesion(sesion.getId(), momento, inicio)) {
                continue;
            }
            recordatorioRepository.save(new SesionRecordatorio(sesion.getId(), momento, inicio));
            avisar(sesion, momento);
            enviados++;
        }
        return enviados;
    }

    // Qué recordatorio toca para una sesión que empieza en "inicio": H1 si falta 1 hora o menos, H24 si falta entre 1 y 24
    // horas, null si ya empezó o falta más de un día.
    static Momento momento(LocalDateTime inicio, LocalDateTime ahora) {
        if (!inicio.isAfter(ahora)) {
            return null;
        }
        Duration falta = Duration.between(ahora, inicio);
        if (falta.compareTo(Duration.ofHours(1)) <= 0) {
            return Momento.H1;
        }
        return falta.compareTo(Duration.ofHours(24)) <= 0 ? Momento.H24 : null;
    }

    private void avisar(Sesion sesion, Momento momento) {
        String cuando = momento == Momento.H1 ? "menos de 1 hora" : "menos de 24 horas";
        Usuario estudiante = sesion.getEstudiante().getUsuario();
        Usuario mentor = sesion.getMentor().getUsuario();
        avisarA(estudiante, mentor, sesion, cuando);
        avisarA(mentor, estudiante, sesion, cuando);
    }

    private void avisarA(Usuario destinatario, Usuario otro, Sesion sesion, String cuando) {
        String hora = sesion.getHoraInicio().format(DateTimeFormatter.ofPattern("HH:mm"));
        try {
            notificaciones.crear(destinatario.getId(), Notificacion.Tipo.RECORDATORIO, "Tu sesión empieza pronto",
                    "Tu sesión con %s empieza en %s (%s a las %s). Enlace: %s".formatted(
                            otro.getNombres(), cuando, sesion.getFecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                            hora, sesion.getEnlaceReunion()));
        } catch (Exception e) {
            logger.error("No se pudo guardar el recordatorio de la sesion {}", sesion.getId(), e);
        }
        try {
            correos.enviarRecordatorio(destinatario.getCorreoInstitucional(), destinatario.getNombres(), cuando,
                    sesion.getFecha(), sesion.getHoraInicio(), sesion.getHoraFin(), sesion.getEnlaceReunion());
        } catch (Exception e) {
            logger.error("No se pudo enviar el correo de recordatorio de la sesion {}", sesion.getId(), e);
        }
    }
}
