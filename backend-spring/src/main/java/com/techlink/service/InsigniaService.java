package com.techlink.service;

import com.techlink.model.Insignia;
import com.techlink.model.Mentor;
import com.techlink.repository.CalificacionRepository;
import com.techlink.repository.InsigniaRepository;
import com.techlink.repository.MentorRepository;
import com.techlink.repository.SesionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

// HU-15: otorga insignias a los mentores de forma automática cuando alcanzan un umbral de horas dictadas
// (por ejemplo, 10 horas) o de promedio de estrellas (por ejemplo, 4.5 o más). Nunca se quitan.
@Service
public class InsigniaService {

    // Lo que un mentor lleva hecho: horas de sesiones ya terminadas y cuántas son.
    public record Actividad(double horas, int sesiones) {
    }

    private final InsigniaRepository insigniaRepository;
    private final MentorRepository mentorRepository;
    private final SesionRepository sesionRepository;
    private final CalificacionRepository calificacionRepository;
    private final Clock reloj;

    @Autowired
    public InsigniaService(InsigniaRepository insigniaRepository,
                           MentorRepository mentorRepository,
                           SesionRepository sesionRepository,
                           CalificacionRepository calificacionRepository) {
        this(insigniaRepository, mentorRepository, sesionRepository, calificacionRepository, Clock.systemDefaultZone());
    }

    // El reloj se puede cambiar solo en las pruebas automaticas.
    InsigniaService(InsigniaRepository insigniaRepository, MentorRepository mentorRepository,
                    SesionRepository sesionRepository, CalificacionRepository calificacionRepository, Clock reloj) {
        this.insigniaRepository = insigniaRepository;
        this.mentorRepository = mentorRepository;
        this.sesionRepository = sesionRepository;
        this.calificacionRepository = calificacionRepository;
        this.reloj = reloj;
    }

    // Horas y sesiones de un mentor. Solo cuentan las sesiones CONFIRMADAS cuya hora de fin ya pasó.
    @Transactional(readOnly = true)
    public Actividad actividad(Integer mentorId) {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        long minutos = 0;
        int sesiones = 0;
        for (Object[] fila : sesionRepository.horariosConfirmadosDeMentor(mentorId)) {
            LocalDate fecha = (LocalDate) fila[0];
            LocalTime inicio = (LocalTime) fila[1];
            LocalTime fin = (LocalTime) fila[2];
            if (!LocalDateTime.of(fecha, fin).isAfter(ahora)) {
                minutos += Duration.between(inicio, fin).toMinutes();
                sesiones++;
            }
        }
        return new Actividad(Math.round(minutos / 60.0 * 100) / 100.0, sesiones);
    }

    // Revisa a un mentor y le otorga las insignias que ya merece. Devuelve las nuevas.
    @Transactional
    public List<Insignia> evaluar(Integer mentorId) {
        Mentor mentor = mentorRepository.findById(mentorId).orElse(null);
        if (mentor == null) {
            return List.of();
        }
        double horas = actividad(mentorId).horas();
        CalificacionRepository.Resumen resumen = calificacionRepository.resumenDeMentor(mentorId);
        double promedio = resumen.getPromedio() == null ? 0 : resumen.getPromedio();
        long total = resumen.getTotal() == null ? 0 : resumen.getTotal();

        List<Insignia> nuevas = new ArrayList<>();
        for (Insignia insignia : insigniaRepository.findAllByOrderByIdAsc()) {
            boolean yaLaTiene = mentor.getInsignias().stream().anyMatch(i -> i.getId().equals(insignia.getId()));
            if (!yaLaTiene && cumple(insignia, horas, promedio, total)) {
                mentor.getInsignias().add(insignia);
                nuevas.add(insignia);
            }
        }
        if (!nuevas.isEmpty()) {
            mentorRepository.save(mentor);
        }
        return nuevas;
    }

    // Revisa a todos los mentores (lo usa el proceso programado, porque las horas se cumplen con el paso del tiempo).
    @Transactional
    public int evaluarTodos() {
        int otorgadas = 0;
        for (Mentor mentor : mentorRepository.findAll()) {
            otorgadas += evaluar(mentor.getId()).size();
        }
        return otorgadas;
    }

    static boolean cumple(Insignia insignia, double horas, double promedio, long totalCalificaciones) {
        double umbral = insignia.getUmbral().doubleValue();
        return switch (insignia.getTipo()) {
            case HORAS -> horas >= umbral;
            case PROMEDIO -> totalCalificaciones >= insignia.getMinimoCalificaciones() && promedio >= umbral;
        };
    }
}
