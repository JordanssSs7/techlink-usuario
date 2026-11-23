package com.techlink.service;

import com.techlink.dto.CharlaResponse;
import com.techlink.dto.CharlasRecomendadasResponse;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.Charla;
import com.techlink.model.CharlaInscripcion;
import com.techlink.model.Notificacion;
import com.techlink.model.Curso;
import com.techlink.model.Estudiante;
import com.techlink.repository.CharlaInscripcionRepository;
import com.techlink.repository.CharlaRepository;
import com.techlink.repository.EstudianteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// HU-06: recomienda al estudiante las charlas vigentes que coinciden con sus intereses o con sus cursos críticos.
// HU-25: el estudiante se inscribe a una charla vigente (una sola vez) y ve las suyas en "Mis charlas".
// Las charlas las crea el administrador desde el panel (HU-58); aquí solo se leen.
@Service
public class CharlaService {

    private final CharlaRepository charlaRepository;
    private final CharlaInscripcionRepository inscripcionRepository;
    private final EstudianteRepository estudianteRepository;
    private final NotificacionService notificaciones;
    private final Clock reloj;
    private SalaRegistro salas; // para saber si el invitado sigue en la sala o ya terminó la charla

    @Autowired(required = false)
    public void setSalas(SalaRegistro salas) {
        this.salas = salas;
    }

    private boolean enCurso(Charla c) {
        return salas != null && salas.invitadoPresente("CHARLA-" + c.getId());
    }

    // Las charlas vigentes: las que aún no llegan a su hora de fin, más las que se pasaron de hora pero cuyo invitado sigue en la sala. Que el
    // invitado salga antes (o termine) no las oculta: puede volver a entrar hasta la hora de fin.
    private List<Charla> listadas(LocalDateTime ahora) {
        List<Charla> lista = new ArrayList<>(charlaRepository.vigentes(ahora.toLocalDate(), ahora.toLocalTime()));
        for (Charla c : charlaRepository.terminadasHoy(ahora.toLocalDate(), ahora.toLocalTime())) {
            if (enCurso(c)) {
                lista.add(c);
            }
        }
        return lista;
    }

    @Autowired
    public CharlaService(CharlaRepository charlaRepository,
                         CharlaInscripcionRepository inscripcionRepository,
                         EstudianteRepository estudianteRepository,
                         NotificacionService notificaciones) {
        this(charlaRepository, inscripcionRepository, estudianteRepository, notificaciones, Clock.systemDefaultZone());
    }

    // El reloj se puede cambiar solo en las pruebas automaticas.
    CharlaService(CharlaRepository charlaRepository,
                  CharlaInscripcionRepository inscripcionRepository,
                  EstudianteRepository estudianteRepository,
                  NotificacionService notificaciones,
                  Clock reloj) {
        this.charlaRepository = charlaRepository;
        this.inscripcionRepository = inscripcionRepository;
        this.estudianteRepository = estudianteRepository;
        this.notificaciones = notificaciones;
        this.reloj = reloj;
    }

    // HU-06: las charlas vigentes, con las que coinciden con el perfil primero (y la razón de cada una).
    @Transactional(readOnly = true)
    public CharlasRecomendadasResponse recomendadas(Integer usuarioId) {
        Estudiante estudiante = estudiante(usuarioId);
        List<String> intereses = estudiante.getUsuario().getIntereses().stream().toList();
        Set<Integer> idsCursos = new HashSet<>();
        for (Curso curso : estudiante.getCursosCriticos()) {
            idsCursos.add(curso.getId());
        }
        Set<Integer> inscritas = new HashSet<>(inscripcionRepository.idsDeCharlas(usuarioId));
        LocalDateTime ahora = LocalDateTime.now(reloj);

        List<CharlaResponse> recomendadas = new ArrayList<>();
        List<CharlaResponse> otras = new ArrayList<>();
        for (Charla charla : listadas(ahora)) {
            String razon = razon(charla, idsCursos, intereses);
            CharlaResponse respuesta = aRespuesta(charla, razon, inscritas.contains(charla.getId()), ahora);
            (razon != null ? recomendadas : otras).add(respuesta);
        }
        boolean perfilCompleto = !intereses.isEmpty() || !idsCursos.isEmpty();
        return new CharlasRecomendadasResponse(recomendadas, otras, perfilCompleto);
    }

    // Para el mentor: las mismas charlas vigentes, sin recomendaciones (no tiene intereses ni cursos críticos).
    @Transactional(readOnly = true)
    public CharlasRecomendadasResponse vigentesParaMentor(Integer usuarioId) {
        Set<Integer> inscritas = new HashSet<>(inscripcionRepository.idsDeCharlas(usuarioId));
        LocalDateTime ahora = LocalDateTime.now(reloj);
        List<CharlaResponse> todas = new ArrayList<>();
        for (Charla charla : listadas(ahora)) {
            todas.add(aRespuesta(charla, null, inscritas.contains(charla.getId()), ahora));
        }
        return new CharlasRecomendadasResponse(new ArrayList<>(), todas, true);
    }

    // HU-25: "Mis charlas": las próximas primero (de la más cercana a la más lejana) y luego las que ya pasaron.
    @Transactional(readOnly = true)
    public List<CharlaResponse> mias(Integer usuarioId) {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        List<CharlaResponse> proximas = new ArrayList<>();
        List<CharlaResponse> pasadas = new ArrayList<>();
        for (Charla charla : charlaRepository.inscritasDe(usuarioId)) {
            CharlaResponse respuesta = aRespuesta(charla, null, true, ahora);
            (respuesta.vigente() ? proximas : pasadas).add(respuesta);
        }
        // inscritasDe devuelve de la más reciente a la más antigua: las próximas se invierten para ir de cerca a lejos.
        java.util.Collections.reverse(proximas);
        proximas.addAll(pasadas);
        return proximas;
    }

    // HU-25: se inscribe a una charla vigente. 404 si no existe o está desactivada, 409 si ya terminó o ya está inscrito.
    @Transactional
    public CharlaResponse inscribir(Integer usuarioId, Integer charlaId) {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        Charla charla = charlaRepository.findById(charlaId)
                .filter(Charla::isActivo)
                .orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "La charla no existe."));
        if (!vigente(charla, ahora)) {
            throw new ReglaNegocioException(HttpStatus.CONFLICT, "Esta charla ya terminó.");
        }
        if (inscripcionRepository.existsByIdCharlaAndIdUsuario(charlaId, usuarioId)) {
            throw new ReglaNegocioException(HttpStatus.CONFLICT, "Ya estás inscrito en esta charla.");
        }
        inscripcionRepository.save(new CharlaInscripcion(charlaId, usuarioId));
        // HU-24: aviso de tipo CHARLA (si el estudiante no lo desactivó en sus preferencias).
        notificaciones.crear(usuarioId, Notificacion.Tipo.CHARLA, "Te inscribiste a una charla",
                "Quedaste inscrito en \"%s\": %s de %s a %s. Enlace: %s".formatted(charla.getNombre(),
                        charla.getFecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                        charla.getHoraInicio().format(DateTimeFormatter.ofPattern("HH:mm")),
                        charla.getHoraFin().format(DateTimeFormatter.ofPattern("HH:mm")), charla.getEnlace()));
        return aRespuesta(charla, null, true, ahora);
    }

    // Anula la inscripción. 404 si no estaba inscrito, 409 si la charla ya terminó.
    @Transactional
    public void cancelar(Integer usuarioId, Integer charlaId) {
        CharlaInscripcion inscripcion = inscripcionRepository.findByIdCharlaAndIdUsuario(charlaId, usuarioId)
                .orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "No estás inscrito en esta charla."));
        Charla charla = charlaRepository.findById(charlaId)
                .orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "La charla no existe."));
        if (!vigente(charla, LocalDateTime.now(reloj))) {
            throw new ReglaNegocioException(HttpStatus.CONFLICT, "Esta charla ya terminó.");
        }
        inscripcionRepository.delete(inscripcion);
    }

    private Estudiante estudiante(Integer usuarioId) {
        return estudianteRepository.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new ReglaNegocioException(HttpStatus.FORBIDDEN, "Solo los estudiantes pueden ver e inscribirse a charlas."));
    }

    // Una charla sigue vigente mientras esté activa y su hora de fin no haya pasado.
    static boolean vigente(Charla charla, LocalDateTime ahora) {
        return charla.isActivo() && LocalDateTime.of(charla.getFecha(), charla.getHoraFin()).isAfter(ahora);
    }

    // Por qué se recomienda: primero si refuerza un curso crítico suyo, luego si la temática o el nombre contienen
    // alguno de sus intereses (sin distinguir mayúsculas ni tildes). null si no coincide con nada.
    static String razon(Charla charla, Set<Integer> idsCursosCriticos, List<String> intereses) {
        Curso curso = charla.getCurso();
        if (curso != null && idsCursosCriticos.contains(curso.getId())) {
            return "Refuerza tu curso " + curso.getNombre();
        }
        String texto = normalizar(charla.getTematica() + " " + charla.getNombre());
        for (String interes : intereses) {
            if (coincide(texto, normalizar(interes))) {
                return "Coincide con tu interés en " + interes;
            }
        }
        return null;
    }

    // El interés coincide si aparece completo en el texto o si alguna de sus palabras significativas (5 letras o más)
    // aparece en él: así "Base de datos" coincide con una charla de "Bases de Datos".
    private static boolean coincide(String texto, String interes) {
        if (interes.isBlank()) {
            return false;
        }
        if (texto.contains(interes)) {
            return true;
        }
        for (String palabra : interes.split("\s+")) {
            if (palabra.length() >= 5 && texto.contains(palabra)) {
                return true;
            }
        }
        return false;
    }

    static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

    private CharlaResponse aRespuesta(Charla c, String razon, boolean inscrito, LocalDateTime ahora) {
        boolean sigue = vigente(c, ahora) || (c.isActivo() && enCurso(c));
        LocalDate fecha = c.getFecha();
        LocalTime inicio = c.getHoraInicio();
        return new CharlaResponse(c.getId(), c.getNombre(), c.getTematica(), c.getDescripcion(),
                c.getCurso() == null ? null : c.getCurso().getNombre(), fecha, inicio, c.getHoraFin(), c.getEnlace(),
                razon, inscrito, sigue, c.isActivo(), c.getImagen());
    }
}
