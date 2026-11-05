package com.techlink.service;

import com.techlink.dto.CursoResponse;
import com.techlink.exception.CursosCriticosInvalidosException;
import com.techlink.exception.UsuarioNoRegistradoException;
import com.techlink.model.Curso;
import com.techlink.model.Estudiante;
import com.techlink.repository.CursoRepository;
import com.techlink.repository.EstudianteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// HU-04: cursos criticos del estudiante, los que quiere reforzar. Con ellos se calcula la recomendacion de mentores.
@Service
public class CursoCriticoService {

    // Un curso critico es uno que se quiere reforzar con urgencia: mas de 5 haria la recomendacion poco util.
    static final int MAX_CURSOS_CRITICOS = 5;

    private final EstudianteRepository estudianteRepository;
    private final CursoRepository cursoRepository;

    public CursoCriticoService(EstudianteRepository estudianteRepository, CursoRepository cursoRepository) {
        this.estudianteRepository = estudianteRepository;
        this.cursoRepository = cursoRepository;
    }

    // Cursos criticos del estudiante dueño del token, en orden alfabetico (lista vacia si aun no eligio ninguno).
    @Transactional(readOnly = true)
    public List<CursoResponse> listar(Integer idUsuario) {
        return aRespuesta(buscarEstudiante(idUsuario).getCursosCriticos());
    }

    // Reemplaza los cursos criticos del estudiante por los enviados (ids del catalogo).
    @Transactional
    public List<CursoResponse> guardar(Integer idUsuario, List<Integer> idsCursos) {
        Estudiante estudiante = buscarEstudiante(idUsuario);

        // Se quitan repetidos y vacios, conservando el orden en que llegaron.
        Set<Integer> ids = new LinkedHashSet<>();
        for (Integer id : idsCursos) {
            if (id != null) {
                ids.add(id);
            }
        }

        Map<String, String> errores = new LinkedHashMap<>();
        if (ids.size() > MAX_CURSOS_CRITICOS) {
            errores.put("cursos", "Puedes elegir hasta " + MAX_CURSOS_CRITICOS + " cursos críticos");
        }

        // Cada curso debe existir en el catalogo y estar activo.
        Set<Curso> cursos = new LinkedHashSet<>();
        for (Integer id : ids) {
            Curso curso = cursoRepository.findById(id).filter(c -> Boolean.TRUE.equals(c.getActivo())).orElse(null);
            if (curso == null) {
                errores.putIfAbsent("cursos", "El curso " + id + " no existe en el catálogo");
            } else {
                cursos.add(curso);
            }
        }
        if (!errores.isEmpty()) {
            throw new CursosCriticosInvalidosException(errores);
        }

        estudiante.getCursosCriticos().clear();
        estudiante.getCursosCriticos().addAll(cursos);
        estudianteRepository.save(estudiante);
        return aRespuesta(estudiante.getCursosCriticos());
    }

    private Estudiante buscarEstudiante(Integer idUsuario) {
        return estudianteRepository.findByUsuarioId(idUsuario)
                .orElseThrow(() -> new UsuarioNoRegistradoException("No tienes perfil de estudiante"));
    }

    private List<CursoResponse> aRespuesta(Set<Curso> cursos) {
        return cursos.stream()
                .sorted(Comparator.comparing(Curso::getNombre))
                .map(c -> new CursoResponse(c.getId(), c.getNombre()))
                .toList();
    }
}
