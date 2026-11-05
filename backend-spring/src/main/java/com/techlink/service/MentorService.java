package com.techlink.service;

import com.techlink.dto.CursoResponse;
import com.techlink.dto.PerfilMentorRequest;
import com.techlink.dto.PerfilMentorResponse;
import com.techlink.dto.PerfilMentorResponse.HorarioResponse;
import com.techlink.exception.PerfilMentorInvalidoException;
import com.techlink.exception.UsuarioNoRegistradoException;
import com.techlink.model.Curso;
import com.techlink.model.Mentor;
import com.techlink.model.MentorDisponibilidad;
import com.techlink.model.Usuario;
import com.techlink.repository.CursoRepository;
import com.techlink.repository.MentorDisponibilidadRepository;
import com.techlink.repository.MentorRepository;
import com.techlink.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// HU-05: perfil del mentor (fortalezas, cursos dominados, areas de conocimiento y disponibilidad).
// Los endpoints de /api/mentor/** ya son solo para el rol MENTOR (SecurityConfig).
@Service
public class MentorService {

    private final UsuarioRepository usuarioRepository;
    private final MentorRepository mentorRepository;
    private final CursoRepository cursoRepository;
    private final MentorDisponibilidadRepository disponibilidadRepository;

    public MentorService(UsuarioRepository usuarioRepository,
                         MentorRepository mentorRepository,
                         CursoRepository cursoRepository,
                         MentorDisponibilidadRepository disponibilidadRepository) {
        this.usuarioRepository = usuarioRepository;
        this.mentorRepository = mentorRepository;
        this.cursoRepository = cursoRepository;
        this.disponibilidadRepository = disponibilidadRepository;
    }

    // Catalogo de cursos activos, en orden alfabetico.
    @Transactional(readOnly = true)
    public List<CursoResponse> listarCursos() {
        return cursoRepository.findByActivoTrueOrderByNombreAsc().stream()
                .map(c -> new CursoResponse(c.getId(), c.getNombre()))
                .toList();
    }

    // Perfil del mentor dueño del token. Si aun no guardo nada, devuelve un perfil vacio (no lo crea).
    @Transactional(readOnly = true)
    public PerfilMentorResponse obtenerPerfil(Integer idUsuario) {
        buscarUsuario(idUsuario);
        return mentorRepository.findByUsuarioId(idUsuario)
                .map(this::aRespuesta)
                .orElseGet(() -> new PerfilMentorResponse(null, null, List.of(), List.of(), List.of(), List.of()));
    }

    // Guarda el perfil: las listas del request reemplazan a las anteriores. Crea el perfil de mentor la primera vez.
    @Transactional
    public PerfilMentorResponse actualizarPerfil(Integer idUsuario, PerfilMentorRequest request) {
        Usuario usuario = buscarUsuario(idUsuario);
        Map<String, String> errores = new LinkedHashMap<>();

        Set<Curso> cursos = resolverCursos(request.cursoIds(), errores);
        validarHorarios(request.disponibilidad(), errores);
        if (!errores.isEmpty()) {
            throw new PerfilMentorInvalidoException(errores);
        }

        Mentor mentor = mentorRepository.findByUsuarioId(idUsuario).orElseGet(() -> {
            Mentor nuevo = new Mentor();
            nuevo.setUsuario(usuario);
            return nuevo;
        });

        if (request.especialidad() != null) mentor.setEspecialidad(textoOnull(request.especialidad()));
        if (request.biografia() != null) mentor.setBiografia(textoOnull(request.biografia()));

        mentor.getCursos().clear();
        mentor.getCursos().addAll(cursos);
        reemplazar(mentor.getFortalezas(), limpiarTextos(request.fortalezas()));
        reemplazar(mentor.getAreas(), limpiarTextos(request.areas()));

        // orphanRemoval borra los horarios que ya no estan en la lista.
        mentor.getDisponibilidad().clear();
        for (PerfilMentorRequest.Horario h : request.disponibilidad()) {
            MentorDisponibilidad d = new MentorDisponibilidad();
            d.setMentor(mentor);
            d.setDiaSemana(h.diaSemana());
            d.setHoraInicio(h.horaInicio());
            d.setHoraFin(h.horaFin());
            d.setActivo(h.activo() == null || h.activo());
            mentor.getDisponibilidad().add(d);
        }

        return aRespuesta(mentorRepository.saveAndFlush(mentor));
    }

    // Horarios activos de un mentor: lo que ven los estudiantes y usan HU-04 y HU-07.
    @Transactional(readOnly = true)
    public List<HorarioResponse> horariosActivos(Integer idMentor) {
        if (!mentorRepository.existsById(idMentor)) {
            throw new UsuarioNoRegistradoException("El mentor no existe");
        }
        return disponibilidadRepository.findByMentorIdAndActivoTrueOrderByDiaSemanaAscHoraInicioAsc(idMentor).stream()
                .map(this::aHorario)
                .toList();
    }

    private Usuario buscarUsuario(Integer idUsuario) {
        return usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new UsuarioNoRegistradoException("El usuario no existe"));
    }

    // Todos los ids deben existir en el catalogo y estar activos.
    private Set<Curso> resolverCursos(List<Integer> ids, Map<String, String> errores) {
        Set<Integer> unicos = new LinkedHashSet<>(ids);
        Set<Curso> encontrados = new LinkedHashSet<>();
        for (Curso c : cursoRepository.findAllById(unicos)) {
            if (Boolean.TRUE.equals(c.getActivo())) encontrados.add(c);
        }
        if (encontrados.size() != unicos.size()) {
            errores.put("cursoIds", "Algún curso no existe o no está disponible");
        }
        return encontrados;
    }

    // Cada horario debe terminar despues de empezar y los activos de un mismo dia no pueden cruzarse.
    private void validarHorarios(List<PerfilMentorRequest.Horario> horarios, Map<String, String> errores) {
        for (int i = 0; i < horarios.size(); i++) {
            PerfilMentorRequest.Horario h = horarios.get(i);
            if (!h.horaFin().isAfter(h.horaInicio())) {
                errores.put("disponibilidad[" + i + "].horaFin", "La hora de fin debe ser posterior a la de inicio");
            }
        }
        if (!errores.isEmpty()) return;

        List<PerfilMentorRequest.Horario> activos = horarios.stream()
                .filter(h -> h.activo() == null || h.activo())
                .sorted(Comparator.comparing(PerfilMentorRequest.Horario::diaSemana)
                        .thenComparing(PerfilMentorRequest.Horario::horaInicio))
                .toList();
        for (int i = 1; i < activos.size(); i++) {
            PerfilMentorRequest.Horario anterior = activos.get(i - 1);
            PerfilMentorRequest.Horario actual = activos.get(i);
            if (anterior.diaSemana().equals(actual.diaSemana()) && actual.horaInicio().isBefore(anterior.horaFin())) {
                errores.put("disponibilidad", "Hay horarios activos que se cruzan el mismo día");
                return;
            }
        }
    }

    // Quita espacios y repetidos (sin distinguir mayusculas), conservando el orden.
    private List<String> limpiarTextos(List<String> textos) {
        Map<String, String> unicos = new LinkedHashMap<>();
        for (String t : textos) {
            String limpio = t.trim();
            unicos.putIfAbsent(limpio.toLowerCase(), limpio);
        }
        return new ArrayList<>(unicos.values());
    }

    private void reemplazar(Set<String> destino, List<String> nuevos) {
        destino.clear();
        destino.addAll(nuevos);
    }

    private String textoOnull(String texto) {
        String limpio = texto.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    private PerfilMentorResponse aRespuesta(Mentor mentor) {
        return new PerfilMentorResponse(
                mentor.getEspecialidad(),
                mentor.getBiografia(),
                new ArrayList<>(mentor.getFortalezas()),
                new ArrayList<>(mentor.getAreas()),
                mentor.getCursos().stream()
                        .sorted(Comparator.comparing(Curso::getNombre))
                        .map(c -> new CursoResponse(c.getId(), c.getNombre()))
                        .toList(),
                mentor.getDisponibilidad().stream()
                        .sorted(Comparator.comparing(MentorDisponibilidad::getDiaSemana)
                                .thenComparing(MentorDisponibilidad::getHoraInicio))
                        .map(this::aHorario)
                        .toList());
    }

    private HorarioResponse aHorario(MentorDisponibilidad d) {
        return new HorarioResponse(d.getId(), d.getDiaSemana(), d.getHoraInicio(), d.getHoraFin(), d.getActivo());
    }
}
